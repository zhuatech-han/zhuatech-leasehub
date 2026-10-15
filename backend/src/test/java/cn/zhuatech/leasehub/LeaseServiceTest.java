// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.leasehub;

import static org.junit.jupiter.api.Assertions.*;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

/** 真实事务下的隔离和财务不变量集成测试。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:leasehubtest;MODE=MySQL;NON_KEYWORDS=VALUE;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "leasehub.admin-password=TEST-Only-Password123!"
    })
@Transactional
class LeaseServiceTest {
  @Autowired LeaseService service;
  @Autowired Database db;
  long org, org2, partner, partner2, customer, customer2, client, client2, finance, asset, rate;

  @BeforeEach
  void setup() {
    as(1);
    org = create("organizations", Map.of("name", "TEST合作商A", "code", "TEST_A"));
    org2 = create("organizations", Map.of("name", "TEST合作商B", "code", "TEST_B"));
    customer = create("customers", Map.of("org_id", org, "name", "TEST客户A", "contact", "TEST联系人"));
    customer2 = create("customers", Map.of("org_id", org, "name", "TEST客户B", "contact", "TEST联系人"));
    partner = user("test_partner", 4, org, null);
    partner2 = user("test_partner2", 4, org2, null);
    client = user("test_client", 5, org, customer);
    client2 = user("test_client2", 5, org, customer2);
    finance = user("test_finance", 3, null, null);
    asset =
        create(
            "assets",
            Map.of(
                "serial",
                "TEST-001",
                "name",
                "TEST挖掘机",
                "category",
                "engineering",
                "purchase_cents",
                10000000));
    rate =
        create(
            "rates",
            Map.of(
                "org_id",
                org,
                "name",
                "TEST日租金",
                "daily_cents",
                101,
                "hq_bps",
                3333,
                "effective_on",
                Rules.today().minusDays(120).toString()));
    act("assets", asset, "allocate", Map.of("org_id", org));
    as(partner);
    act("assets", asset, "accept", Map.of());
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  void as(long id) {
    var a = db.one("SELECT * FROM account WHERE id=?", id);
    var auth =
        UsernamePasswordAuthenticationToken.authenticated(a.get("username"), null, List.of());
    auth.setDetails(a.get("password_hash"));
    SecurityContextHolder.getContext().setAuthentication(auth);
  }

  long create(String resource, Map<String, Object> body) {
    return ((Number) service.create(resource, body).get("id")).longValue();
  }

  long user(String username, long role, Long org, Long customer) {
    var b = new LinkedHashMap<String, Object>();
    b.put("username", username);
    b.put("display_name", username);
    b.put("password", "TEST-Only-Password123!");
    b.put("role_id", role);
    if (org != null) b.put("org_id", org);
    if (customer != null) b.put("customer_id", customer);
    return create("accounts", b);
  }

  void act(String resource, long id, String action, Map<String, Object> more) {
    var b = new LinkedHashMap<>(more);
    b.put(
        "version",
        db.one("SELECT version FROM " + LeaseService.TABLES.get(resource) + " WHERE id=?", id)
            .get("version"));
    b.put("note", "TEST核验说明");
    service.action(resource, id, action, b);
  }

  long contract() {
    as(partner);
    return create(
        "contracts",
        Map.of(
            "customer_id",
            customer,
            "asset_id",
            asset,
            "rate_id",
            rate,
            "name",
            "TEST合同",
            "start_on",
            Rules.today().minusDays(60).toString(),
            "end_on",
            Rules.today().plusDays(5).toString()));
  }

  long active() {
    long c = contract();
    act("contracts", c, "submit", Map.of());
    as(client);
    act("contracts", c, "confirm", Map.of());
    as(partner);
    act("contracts", c, "deliver", Map.of("delivered_on", Rules.today().toString()));
    as(client);
    act("contracts", c, "accept-delivery", Map.of());
    return c;
  }

  long bill() {
    return Database.id(db.one("SELECT * FROM bill ORDER BY id LIMIT 1"), "id");
  }

  long receipt(long cents) {
    as(partner);
    return create(
        "receipts",
        Map.of(
            "bill_id",
            bill(),
            "amount_cents",
            cents,
            "received_on",
            Rules.today().toString(),
            "reference",
            "TEST-R-" + UUID.randomUUID(),
            "note",
            "TEST线下凭据"));
  }

  @Test
  void isolatedListsDetailsAndLookups() {
    as(partner2);
    assertEquals(0L, service.list("assets", 1, 20, "", "").get("total"));
    assertThrows(Problem.class, () -> service.detail("assets", asset));
    assertThrows(
        Problem.class,
        () -> service.action("assets", asset, "accept", Map.of("version", 1, "note", "TEST")));
    assertTrue(((List<?>) service.lookup().get("customers")).isEmpty());
    assertThrows(Problem.class, () -> service.create("rates", Map.of("org_id", org2)));
    assertThrows(Problem.class, () -> service.list("accounts", 1, 20, "", ""));
  }

  @Test
  void contractOccupancyFrozenPriceAndCustomerIsolation() {
    long c = contract(), other = contract();
    act("contracts", c, "submit", Map.of());
    assertThrows(Problem.class, () -> act("contracts", other, "submit", Map.of()));
    as(client2);
    assertEquals(0L, service.list("contracts", 1, 20, "", "").get("total"));
    assertThrows(Problem.class, () -> service.detail("contracts", c));
    as(1);
    assertThrows(Problem.class, () -> act("contracts", c, "confirm", Map.of()));
    as(client);
    act("contracts", c, "confirm", Map.of());
    as(partner);
    act("contracts", c, "deliver", Map.of("delivered_on", Rules.today().toString()));
    as(client);
    act("contracts", c, "accept-delivery", Map.of());
    assertEquals("RENTED", db.one("SELECT state FROM asset WHERE id=?", asset).get("state"));
    assertThrows(Problem.class, () -> act("contracts", c, "confirm", Map.of()));
    as(1);
    create(
        "rates",
        Map.of(
            "org_id",
            org,
            "name",
            "TEST新版",
            "daily_cents",
            9999,
            "hq_bps",
            9000,
            "effective_on",
            Rules.today().toString()));
    assertEquals(
        101L, Database.id(db.one("SELECT * FROM rental_contract WHERE id=?", c), "daily_cents"));
    assertEquals(
        6565L,
        Database.id(
            db.one("SELECT SUM(amount_cents) AS total FROM bill WHERE contract_id=?", c), "total"));
  }

  @Test
  void staleVersionAndBusyAsset() {
    active();
    as(1);
    assertThrows(
        Problem.class,
        () -> service.action("assets", asset, "recall", Map.of("version", 0, "note", "TEST")));
    assertThrows(Problem.class, () -> act("assets", asset, "maintain", Map.of()));
    assertThrows(Problem.class, () -> act("assets", asset, "dispose", Map.of()));
  }

  @Test
  void partialReceiptsOverpaymentAndReversal() {
    active();
    long r = receipt(101);
    as(finance);
    act("receipts", r, "approve", Map.of());
    assertEquals(101L, Database.id(db.one("SELECT * FROM bill WHERE id=?", bill()), "paid_cents"));
    long remaining =
        Database.id(db.one("SELECT * FROM bill WHERE id=?", bill()), "amount_cents") - 101;
    long r2 = receipt(remaining), r3 = receipt(remaining);
    as(finance);
    act("receipts", r2, "approve", Map.of());
    assertThrows(Problem.class, () -> act("receipts", r3, "approve", Map.of()));
    act("receipts", r2, "reverse", Map.of());
    assertEquals(101L, Database.id(db.one("SELECT * FROM bill WHERE id=?", bill()), "paid_cents"));
  }

  @Test
  void settlementNegativeNetDoubleApprovalAndReversals() {
    active();
    long r = receipt(101);
    long cost =
        create(
            "costs",
            Map.of(
                "asset_id",
                asset,
                "category",
                "maintenance",
                "amount_cents",
                40,
                "incurred_on",
                Rules.today().toString(),
                "reference",
                "TEST-COST",
                "note",
                "TEST维保"));
    as(finance);
    act("receipts", r, "approve", Map.of());
    act("costs", cost, "approve", Map.of());
    as(partner);
    long s = create("settlements", Map.of("month_on", Rules.today().withDayOfMonth(1).toString()));
    assertEquals(
        -7L, Database.id(db.one("SELECT * FROM settlement WHERE id=?", s), "hq_net_cents"));
    assertThrows(
        Problem.class,
        () ->
            create("settlements", Map.of("month_on", Rules.today().withDayOfMonth(1).toString())));
    as(finance);
    assertThrows(Problem.class, () -> act("receipts", r, "reverse", Map.of()));
    act("settlements", s, "approve", Map.of());
    act(
        "settlements",
        s,
        "pay",
        Map.of("transferred_on", Rules.today().toString(), "reference", "TEST-TRANSFER"));
    assertEquals(
        "HQ_TO_PARTNER",
        db.one("SELECT * FROM transfer_entry WHERE settlement_id=?", s).get("direction"));
    assertThrows(Problem.class, () -> act("settlements", s, "reverse", Map.of()));
    act(
        "settlements",
        s,
        "unpay",
        Map.of("transferred_on", Rules.today().toString(), "reference", "TEST-REVERSE"));
    act("settlements", s, "reverse", Map.of());
    act("receipts", r, "reverse", Map.of());
    assertEquals(2, db.rows("SELECT * FROM transfer_entry WHERE settlement_id=?", s).size());
    assertEquals(0L, Database.id(db.one("SELECT * FROM bill WHERE id=?", bill()), "paid_cents"));
  }

  @Test
  void selfSettlementApprovalIsBlocked() {
    active();
    long r = receipt(101);
    as(finance);
    act("receipts", r, "approve", Map.of());
    long s =
        create(
            "settlements",
            Map.of("org_id", org, "month_on", Rules.today().withDayOfMonth(1).toString()));
    assertThrows(Problem.class, () -> act("settlements", s, "approve", Map.of()));
    as(1);
    act("settlements", s, "approve", Map.of());
  }

  @Test
  void handoverRecallDisposalLifecycle() {
    as(1);
    assertThrows(Problem.class, () -> act("assets", asset, "accept", Map.of()));
    act("assets", asset, "recall", Map.of());
    as(partner);
    assertThrows(Problem.class, () -> act("assets", asset, "receive", Map.of()));
    as(1);
    act("assets", asset, "receive", Map.of());
    act("assets", asset, "dispose", Map.of());
    assertNull(db.one("SELECT * FROM asset WHERE id=?", asset).get("org_id"));
    assertEquals(5, db.rows("SELECT * FROM asset_event WHERE asset_id=?", asset).size());
  }

  @Test
  void disableOrganizationInvalidatesSession() {
    as(1);
    var o = db.one("SELECT * FROM organization WHERE id=?", org);
    service.edit(
        "organizations",
        org,
        Map.of("name", "TEST停用", "enabled", false, "version", o.get("version")));
    as(partner);
    assertThrows(Problem.class, () -> service.lookup());
  }

  @Test
  void roleEscalationAndCustomerCreationRejected() {
    as(1);
    assertThrows(
        Problem.class,
        () ->
            create(
                "roles",
                Map.of("name", "TEST越权", "scope", "PARTNER", "permissions", "admin,approve")));
    as(client);
    assertThrows(Problem.class, () -> create("contracts", Map.of()));
    assertThrows(Problem.class, () -> service.report());
  }

  @Test
  void riskFollowupAndHistory() {
    as(partner);
    long c =
        create(
            "risks",
            Map.of(
                "title",
                "TEST逾期跟进",
                "owner_id",
                partner,
                "asset_id",
                asset,
                "next_on",
                Rules.today().toString()));
    act("risks", c, "follow", Map.of("next_on", Rules.today().plusDays(2).toString()));
    act("risks", c, "close", Map.of());
    act("risks", c, "reopen", Map.of());
    assertEquals(3, db.rows("SELECT * FROM case_note WHERE case_id=?", c).size());
    as(partner2);
    assertThrows(Problem.class, () -> service.detail("risks", c));
  }

  @Test
  void pagingExportAndNoPasswordLeak() {
    as(1);
    assertFalse(service.list("accounts", 1, 20, "test", "").toString().contains("password_hash"));
    assertEquals(2L, service.list("customers", 1, 1, "TEST", "").get("total"));
    assertThrows(Problem.class, () -> service.list("assets", 0, 20, "", ""));
    assertThrows(Problem.class, () -> service.export("accounts"));
    as(partner2);
    assertFalse(service.export("assets").contains("TEST-001"));
  }

  @Test
  void deliveryAndReturnNeedBothParties() {
    long c = contract();
    act("contracts", c, "submit", Map.of());
    as(client);
    act("contracts", c, "confirm", Map.of());
    assertEquals("RESERVED", db.one("SELECT state FROM asset WHERE id=?", asset).get("state"));
    as(partner);
    assertThrows(
        Problem.class,
        () -> act("contracts", c, "return", Map.of("return_on", Rules.today().toString())));
    act("contracts", c, "deliver", Map.of("delivered_on", Rules.today().toString()));
    assertThrows(Problem.class, () -> act("contracts", c, "accept-delivery", Map.of()));
    as(client);
    act("contracts", c, "accept-delivery", Map.of());
    as(partner);
    act("contracts", c, "return", Map.of("return_on", Rules.today().toString()));
    assertEquals("RENTED", db.one("SELECT state FROM asset WHERE id=?", asset).get("state"));
    as(client);
    act("contracts", c, "confirm-return", Map.of());
    assertEquals(
        "PARTNER_AVAILABLE", db.one("SELECT state FROM asset WHERE id=?", asset).get("state"));
    assertEquals(
        "RETURNED", db.one("SELECT state FROM rental_contract WHERE id=?", c).get("state"));
    assertEquals(6, db.rows("SELECT * FROM contract_event WHERE contract_id=?", c).size());
  }

  @Test
  void roleChangeIsImmediateAndCrossScopeBindingRejected() {
    as(1);
    var a = db.one("SELECT * FROM account WHERE id=?", partner);
    assertThrows(
        Problem.class,
        () ->
            service.edit(
                "accounts",
                partner,
                Map.of(
                    "display_name",
                    "TEST",
                    "enabled",
                    true,
                    "role_id",
                    1,
                    "version",
                    a.get("version"))));
    long role =
        create("roles", Map.of("name", "TEST只读经营", "scope", "PARTNER", "permissions", "reports"));
    service.edit(
        "accounts",
        partner,
        Map.of(
            "display_name", "TEST", "enabled", true, "role_id", role, "version", a.get("version")));
    as(partner);
    assertThrows(Problem.class, () -> service.list("assets", 1, 20, "", ""));
    assertNotNull(service.report());
  }

  @Test
  void customerViewHidesInternalPartnerSplitAndReceiptNotes() {
    long c = active();
    long receipt = receipt(101);
    as(client);
    var own = service.detail("contracts", c);
    assertFalse(((Map<?, ?>) own.get("record")).containsKey("hq_bps"));
    assertFalse(((Map<?, ?>) own.get("record")).containsKey("rate_id"));
    var receipts = (List<?>) service.list("receipts", 1, 20, "", "").get("items");
    assertFalse(((Map<?, ?>) receipts.getFirst()).containsKey("note"));
    assertFalse(
        ((Map<?, ?>) service.detail("receipts", receipt).get("record")).containsKey("creator_id"));
    as(partner);
    assertTrue(((Map<?, ?>) service.detail("contracts", c).get("record")).containsKey("hq_bps"));
  }
}
