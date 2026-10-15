// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.leasehub;

import java.time.*;
import java.util.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 租赁投放、履约与财务状态机；所有写操作在同一事务内检查权限和版本。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
public class LeaseService {
  final Database db;
  final Access access;
  final BCryptPasswordEncoder encoder;
  static final Map<String, String> TABLES =
      Map.ofEntries(
          Map.entry("organizations", "organization"),
          Map.entry("accounts", "account"),
          Map.entry("roles", "access_role"),
          Map.entry("dictionary", "dictionary_entry"),
          Map.entry("settings", "system_setting"),
          Map.entry("menus", "nav_menu"),
          Map.entry("assets", "asset"),
          Map.entry("customers", "customer"),
          Map.entry("rates", "rate_version"),
          Map.entry("contracts", "rental_contract"),
          Map.entry("bills", "bill"),
          Map.entry("receipts", "receipt"),
          Map.entry("costs", "operating_cost"),
          Map.entry("settlements", "settlement"),
          Map.entry("risks", "risk_case"),
          Map.entry("audit", "audit_event"),
          Map.entry("transfers", "transfer_entry"));
  static final Set<String> ADMIN =
      Set.of("organizations", "accounts", "roles", "dictionary", "settings", "menus", "audit");
  static final Set<String> CUSTOMER = Set.of("contracts", "bills", "receipts");
  static final Set<String> PERMS =
      Set.of("admin", "assets", "contracts", "finance", "approve", "risk", "reports", "portal");

  public LeaseService(Database db, Access access, BCryptPasswordEncoder encoder) {
    this.db = db;
    this.access = access;
    this.encoder = encoder;
  }

  String permission(String resource) {
    return switch (resource) {
      case "assets" -> "assets";
      case "customers", "rates", "contracts" -> "contracts";
      case "bills", "receipts", "costs", "settlements", "transfers" -> "finance";
      case "risks" -> "risk";
      default -> "admin";
    };
  }

  Map<String, Object> viewer(String resource) {
    if (!TABLES.containsKey(resource)) throw new Problem(404, "页面不存在");
    var a = access.current();
    if ("CUSTOMER".equals(a.get("scope")) && CUSTOMER.contains(resource)) {
      access.require("portal");
      return a;
    }
    access.require(permission(resource));
    if (ADMIN.contains(resource)) access.hq(a);
    return a;
  }

  String predicate(String resource, Map<String, Object> a) {
    return ADMIN.contains(resource) ? "1=1" : access.filter(a, CUSTOMER.contains(resource));
  }

  /** 服务端分页、状态过滤和对象隔离。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Map<String, Object> list(
      String resource, int page, int size, String search, String state) {
    var a = viewer(resource);
    Rules.check(page >= 1 && page <= 100000 && size >= 1 && size <= 100, "分页范围无效");
    String table = TABLES.get(resource), where = predicate(resource, a);
    List<Object> args = new ArrayList<>();
    if (!search.isBlank()) {
      String field =
          switch (resource) {
            case "assets" -> "CONCAT(serial,' ',name)";
            case "accounts" -> "CONCAT(username,' ',display_name)";
            case "risks" -> "title";
            case "organizations", "roles", "customers", "rates", "contracts" -> "name";
            case "receipts", "costs", "transfers" -> "reference";
            case "settings", "menus", "dictionary" -> "label";
            case "audit" -> "action";
            default -> "CAST(id AS CHAR)";
          };
      where += " AND " + field + " LIKE ? ESCAPE '!'";
      args.add("%" + search.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%");
    }
    if (!state.isBlank()
        && Set.of("assets", "contracts", "receipts", "costs", "settlements", "risks")
            .contains(resource)) {
      where += " AND state=?";
      args.add(state);
    }
    long total =
        Database.id(
            db.one("SELECT COUNT(*) AS total FROM " + table + " WHERE " + where, args.toArray()),
            "total");
    args.add(size);
    args.add((page - 1) * size);
    var rows =
        db.rows(
            "SELECT * FROM " + table + " WHERE " + where + " ORDER BY id DESC LIMIT ? OFFSET ?",
            args.toArray());
    rows.forEach(m -> customerView(resource, a, m));
    return Map.of("items", rows, "total", total, "page", page, "size", size);
  }

  /** 经隔离的表单关联项，不返回密码散列和跨商客户资料。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Map<String, Object> lookup() {
    var a = access.current();
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("dictionary", db.rows("SELECT * FROM dictionary_entry WHERE enabled=TRUE ORDER BY id"));
    if ("CUSTOMER".equals(a.get("scope"))) {
      out.put(
          "contracts",
          db.rows(
              "SELECT id,name FROM rental_contract WHERE org_id=? AND customer_id=?",
              a.get("org_id"),
              a.get("customer_id")));
      return out;
    }
    String f = access.filter(a, false);
    out.put(
        "organizations",
        "HQ".equals(a.get("scope"))
            ? db.rows("SELECT * FROM organization ORDER BY id")
            : db.rows("SELECT * FROM organization WHERE id=?", a.get("org_id")));
    out.put(
        "customers",
        db.rows("SELECT id,org_id,name FROM customer WHERE " + f + " ORDER BY id LIMIT 1000"));
    out.put(
        "assets",
        db.rows(
            "SELECT id,org_id,serial,name,state FROM asset WHERE "
                + f
                + " ORDER BY id LIMIT 1000"));
    out.put(
        "rates", db.rows("SELECT * FROM rate_version WHERE " + f + " ORDER BY id DESC LIMIT 1000"));
    out.put(
        "accounts",
        db.rows(
            "SELECT id,display_name,org_id FROM account WHERE enabled=TRUE AND "
                + ("HQ".equals(a.get("scope")) ? "1=1" : "org_id=" + Database.id(a, "org_id"))
                + " ORDER BY id LIMIT 1000"));
    out.put(
        "contracts",
        db.rows(
            "SELECT id,org_id,name FROM rental_contract WHERE "
                + f
                + " ORDER BY id DESC LIMIT 1000"));
    if (access.can(a, "finance"))
      out.put(
          "bills",
          db.rows(
              "SELECT id,org_id,contract_id,amount_cents,paid_cents FROM bill WHERE "
                  + f
                  + " AND amount_cents>paid_cents ORDER BY id DESC LIMIT 1000"));
    if (access.can(a, "admin")) out.put("roles", db.rows("SELECT * FROM access_role ORDER BY id"));
    return out;
  }

  /** 客户视图仅保留自身履约和费用信息，隐藏内部分成与核销经办信息。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  void customerView(String resource, Map<String, Object> a, Map<String, Object> record) {
    record.remove("password_hash");
    if ("CUSTOMER".equals(a.get("scope"))) {
      if (resource.equals("contracts")) {
        record.remove("hq_bps");
        record.remove("rate_id");
      }
      if (resource.equals("receipts")) {
        for (String key : List.of("creator_id", "reviewer_id", "note")) record.remove(key);
      }
    }
  }

  Map<String, Object> row(String resource, long id, Map<String, Object> a) {
    var m = db.one("SELECT * FROM " + TABLES.get(resource) + " WHERE id=?", id);
    if (!ADMIN.contains(resource)) access.scope(a, m);
    return m;
  }

  void version(Map<String, Object> row, Map<String, Object> b) {
    if (Database.id(row, "version") != Rules.number(b.get("version")))
      throw new Problem(409, "记录已更新，请刷新后重试");
  }

  void state(Map<String, Object> row, String... allowed) {
    Rules.check(List.of(allowed).contains(row.get("state")), "当前状态不允许此操作");
  }

  void change(String table, long id, String state) {
    if (table.equals("asset"))
      db.update(
          "UPDATE asset SET state=?,changed_on=?,version=version+1 WHERE id=?",
          state,
          Rules.today(),
          id);
    else db.update("UPDATE " + table + " SET state=?,version=version+1 WHERE id=?", state, id);
  }

  void dictionary(String kind, String code) {
    Rules.check(
        !db.rows(
                "SELECT id FROM dictionary_entry WHERE kind=? AND code=? AND enabled=TRUE",
                kind,
                code)
            .isEmpty(),
        "请选择有效分类");
  }

  long org(Map<String, Object> a, Map<String, Object> b) {
    long org =
        "HQ".equals(a.get("scope")) ? Rules.number(b.get("org_id")) : Database.id(a, "org_id");
    access.org(a, org);
    return org;
  }

  /** 创建真实主数据与业务单据，不允许请求携带审批状态或任意机构。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional
  public Map<String, Object> create(String resource, Map<String, Object> b) {
    db.lock();
    var a = viewer(resource);
    if ("CUSTOMER".equals(a.get("scope"))) throw new Problem(403, "客户不可创建业务单据");
    Map<String, Object> v = new LinkedHashMap<>();
    switch (resource) {
      case "organizations" -> {
        v.put("name", Rules.text(b.get("name"), 120));
        v.put("code", Rules.text(b.get("code"), 60));
      }
      case "roles" -> {
        String scope = Rules.text(b.get("scope"), 20);
        Rules.check(Set.of("HQ", "PARTNER", "CUSTOMER").contains(scope), "角色范围无效");
        String permissions = Rules.text(b.get("permissions"), 1000);
        Set<String> p = new HashSet<>(Arrays.asList(permissions.split(",")));
        Rules.check(PERMS.containsAll(p), "权限无效");
        Rules.check(
            scope.equals("HQ") || !p.contains("admin") && !p.contains("approve"), "跨范围权限无效");
        Rules.check(!scope.equals("CUSTOMER") || p.equals(Set.of("portal")), "客户仅允许portal权限");
        Rules.check(scope.equals("CUSTOMER") || !p.contains("portal"), "员工角色不可混入客户权限");
        v.putAll(
            Map.of(
                "name",
                Rules.text(b.get("name"), 120),
                "scope",
                scope,
                "permissions",
                String.join(",", new TreeSet<>(p))));
      }
      case "accounts" -> {
        String username = Rules.text(b.get("username"), 60).toLowerCase(Locale.ROOT);
        Rules.check(username.matches("[a-z0-9_.-]{3,60}"), "用户名仅支持3–60位字母、数字、_.-");
        String pw = Rules.text(b.get("password"), 128);
        Rules.password(pw);
        long role = Rules.number(b.get("role_id"));
        var r = db.one("SELECT * FROM access_role WHERE id=?", role);
        v.putAll(
            Map.of(
                "username",
                username,
                "display_name",
                Rules.text(b.get("display_name"), 120),
                "role_id",
                role,
                "password_hash",
                encoder.encode(pw)));
        if (!"HQ".equals(r.get("scope"))) {
          long org = Rules.number(b.get("org_id"));
          access.org(a, org);
          v.put("org_id", org);
          if ("CUSTOMER".equals(r.get("scope"))) {
            long customer = Rules.number(b.get("customer_id"));
            var c = db.one("SELECT * FROM customer WHERE id=? AND org_id=?", customer, org);
            v.put("customer_id", c.get("id"));
          }
        }
      }
      case "dictionary" -> {
        String kind = Rules.text(b.get("kind"), 30);
        Rules.check(Set.of("asset_category", "cost_category").contains(kind), "字典类型无效");
        v.putAll(
            Map.of(
                "kind",
                kind,
                "code",
                Rules.text(b.get("code"), 60),
                "label",
                Rules.text(b.get("label"), 120)));
      }
      case "customers" -> {
        long org = org(a, b);
        v.putAll(
            Map.of(
                "org_id",
                org,
                "name",
                Rules.text(b.get("name"), 120),
                "contact",
                Rules.text(b.get("contact"), 120)));
      }
      case "assets" -> {
        access.hq(a);
        String category = Rules.text(b.get("category"), 60);
        dictionary("asset_category", category);
        v.putAll(
            Map.of(
                "serial",
                Rules.text(b.get("serial"), 60),
                "name",
                Rules.text(b.get("name"), 120),
                "category",
                category,
                "purchase_cents",
                Rules.money(b.get("purchase_cents")),
                "state",
                "HQ_AVAILABLE",
                "changed_on",
                Rules.today()));
      }
      case "rates" -> {
        access.hq(a);
        long org = org(a, b), bps = Rules.number(b.get("hq_bps"));
        Rules.check(bps >= 0 && bps <= 10000, "总部分成应为0–100%");
        v.putAll(
            Map.of(
                "org_id",
                org,
                "name",
                Rules.text(b.get("name"), 120),
                "daily_cents",
                Rules.money(b.get("daily_cents")),
                "hq_bps",
                bps,
                "effective_on",
                Rules.date(b.get("effective_on"))));
      }
      case "contracts" -> {
        long org = org(a, b);
        var asset =
            db.one(
                "SELECT * FROM asset WHERE id=? AND org_id=?",
                Rules.number(b.get("asset_id")),
                org);
        state(asset, "PARTNER_AVAILABLE");
        var customer =
            db.one(
                "SELECT * FROM customer WHERE id=? AND org_id=?",
                Rules.number(b.get("customer_id")),
                org);
        var rate =
            db.one(
                "SELECT * FROM rate_version WHERE id=? AND org_id=?",
                Rules.number(b.get("rate_id")),
                org);
        Rules.check(Database.enabled(rate), "费率已停用");
        LocalDate start = Rules.date(b.get("start_on")), end = Rules.date(b.get("end_on"));
        Rules.check(!start.isBefore(Rules.date(rate.get("effective_on"))), "合同开始日早于费率生效日");
        Rules.periods(start, end, Database.id(rate, "daily_cents"));
        v.putAll(
            Map.of(
                "org_id",
                org,
                "asset_id",
                asset.get("id"),
                "customer_id",
                customer.get("id"),
                "rate_id",
                rate.get("id"),
                "name",
                Rules.text(b.get("name"), 120),
                "start_on",
                start,
                "end_on",
                end,
                "daily_cents",
                rate.get("daily_cents"),
                "hq_bps",
                rate.get("hq_bps"),
                "rate_name",
                rate.get("name")));
        v.put("customer_name", customer.get("name"));
        v.put("asset_name", asset.get("name"));
        v.put("asset_serial", asset.get("serial"));
        v.put("state", "DRAFT");
      }
      case "receipts" -> {
        long org = org(a, b);
        var bill =
            db.one(
                "SELECT * FROM bill WHERE id=? AND org_id=?", Rules.number(b.get("bill_id")), org);
        long amount = Rules.money(b.get("amount_cents"));
        Rules.check(
            amount <= Database.id(bill, "amount_cents") - Database.id(bill, "paid_cents"),
            "金额超过未收余额");
        LocalDate date = Rules.date(b.get("received_on"));
        Rules.check(!date.isAfter(Rules.today()), "收款日不可晚于今天");
        v.putAll(
            Map.of(
                "org_id",
                org,
                "customer_id",
                bill.get("customer_id"),
                "bill_id",
                bill.get("id"),
                "amount_cents",
                amount,
                "received_on",
                date,
                "reference",
                Rules.text(b.get("reference"), 120),
                "note",
                Rules.text(b.get("note"), 400),
                "state",
                "PENDING",
                "creator_id",
                a.get("id")));
      }
      case "costs" -> {
        long org = org(a, b);
        var asset =
            db.one(
                "SELECT * FROM asset WHERE id=? AND org_id=?",
                Rules.number(b.get("asset_id")),
                org);
        String cat = Rules.text(b.get("category"), 60);
        dictionary("cost_category", cat);
        LocalDate date = Rules.date(b.get("incurred_on"));
        Rules.check(!date.isAfter(Rules.today()), "费用日不可晚于今天");
        v.putAll(
            Map.of(
                "org_id",
                org,
                "asset_id",
                asset.get("id"),
                "category",
                cat,
                "amount_cents",
                Rules.money(b.get("amount_cents")),
                "incurred_on",
                date,
                "reference",
                Rules.text(b.get("reference"), 120),
                "note",
                Rules.text(b.get("note"), 400),
                "state",
                "PENDING",
                "creator_id",
                a.get("id")));
      }
      case "settlements" -> {
        return settle(a, b);
      }
      case "risks" -> {
        long org = org(a, b), owner = Rules.number(b.get("owner_id"));
        var ownerRow =
            db.one(
                "SELECT a.*,r.scope,r.permissions FROM account a JOIN access_role r ON r.id=a.role_id WHERE a.id=? AND a.enabled=TRUE",
                owner);
        Rules.check(
            Arrays.asList(ownerRow.get("permissions").toString().split(",")).contains("risk")
                && !"CUSTOMER".equals(ownerRow.get("scope"))
                && ("HQ".equals(ownerRow.get("scope"))
                    || Objects.equals(ownerRow.get("org_id"), org)),
            "负责人不属于可处理范围");
        v.putAll(
            Map.of(
                "org_id",
                org,
                "title",
                Rules.text(b.get("title"), 120),
                "owner_id",
                owner,
                "next_on",
                Rules.date(b.get("next_on")),
                "state",
                "OPEN"));
        if (b.get("asset_id") != null && !b.get("asset_id").toString().isBlank()) {
          var asset =
              db.one(
                  "SELECT id FROM asset WHERE id=? AND org_id=?",
                  Rules.number(b.get("asset_id")),
                  org);
          v.put("asset_id", asset.get("id"));
        }
      }
      default -> throw new Problem(400, "此资源不可新增");
    }
    long id = db.insert(TABLES.get(resource), v);
    access.audit(a, "CREATE", resource, id, "");
    return Map.of("id", id);
  }

  /** 更新主数据，禁止覆盖费率、财务流水和合同快照。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional
  public Map<String, Boolean> edit(String resource, long id, Map<String, Object> b) {
    db.lock();
    var a = viewer(resource);
    var old = row(resource, id, a);
    version(old, b);
    switch (resource) {
      case "organizations" -> {
        db.update(
            "UPDATE organization SET name=?,enabled=?,version=version+1 WHERE id=?",
            Rules.text(b.get("name"), 120),
            Boolean.TRUE.equals(b.get("enabled")),
            id);
      }
      case "accounts" -> {
        Rules.check(id != 1, "初始管理员不可停用或改归属");
        long role = Rules.number(b.get("role_id"));
        var newRole = db.one("SELECT * FROM access_role WHERE id=?", role);
        var oldRole = db.one("SELECT * FROM access_role WHERE id=?", old.get("role_id"));
        Rules.check(
            Objects.equals(oldRole.get("scope"), newRole.get("scope")), "账号角色范围不可更换；请创建独立账号保留历史归属");
        db.update(
            "UPDATE account SET display_name=?,role_id=?,enabled=?,version=version+1 WHERE id=?",
            Rules.text(b.get("display_name"), 120),
            role,
            Boolean.TRUE.equals(b.get("enabled")),
            id);
      }
      case "roles" -> {
        Rules.check(id != 1, "初始管理员角色不可修改");
        String scope = old.get("scope").toString(),
            permissions = Rules.text(b.get("permissions"), 1000);
        Set<String> p = new HashSet<>(Arrays.asList(permissions.split(",")));
        Rules.check(PERMS.containsAll(p), "权限无效");
        Rules.check(
            scope.equals("HQ") || !p.contains("admin") && !p.contains("approve"), "跨范围权限无效");
        Rules.check(!scope.equals("CUSTOMER") || p.equals(Set.of("portal")), "客户仅允许portal权限");
        Rules.check(scope.equals("CUSTOMER") || !p.contains("portal"), "员工角色不可混入客户权限");
        db.update(
            "UPDATE access_role SET name=?,permissions=?,version=version+1 WHERE id=?",
            Rules.text(b.get("name"), 120),
            String.join(",", new TreeSet<>(p)),
            id);
      }
      case "dictionary" -> {
        db.update(
            "UPDATE dictionary_entry SET label=?,enabled=?,version=version+1 WHERE id=?",
            Rules.text(b.get("label"), 120),
            Boolean.TRUE.equals(b.get("enabled")),
            id);
      }
      case "menus" -> {
        db.update(
            "UPDATE nav_menu SET label=?,position=?,enabled=?,version=version+1 WHERE id=?",
            Rules.text(b.get("label"), 120),
            Rules.number(b.get("position")),
            Boolean.TRUE.equals(b.get("enabled")),
            id);
      }
      case "settings" -> {
        String value = Rules.text(b.get("value"), 200);
        if ("idle_days".equals(old.get("code"))) {
          long n = Rules.number(value);
          Rules.check(n >= 1 && n <= 365, "闲置天数应为1–365");
        }
        db.update("UPDATE system_setting SET value=?,version=version+1 WHERE id=?", value, id);
      }
      case "rates" -> {
        access.hq(a);
        db.update(
            "UPDATE rate_version SET enabled=?,version=version+1 WHERE id=?",
            Boolean.TRUE.equals(b.get("enabled")),
            id);
      }
      case "customers" -> {
        db.update(
            "UPDATE customer SET name=?,contact=?,version=version+1 WHERE id=?",
            Rules.text(b.get("name"), 120),
            Rules.text(b.get("contact"), 120),
            id);
      }
      default -> throw new Problem(400, "此记录只能通过业务动作修改");
    }
    access.audit(a, "EDIT", resource, id, "");
    return Map.of("ok", true);
  }

  /** 生成明细冻结的分成结算，严禁同一来源被多次结算。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  Map<String, Object> settle(Map<String, Object> a, Map<String, Object> b) {
    long org = org(a, b);
    LocalDate month = Rules.date(b.get("month_on"));
    Rules.check(month.getDayOfMonth() == 1 && !month.isAfter(Rules.today()), "结算月份须为不晚于当前月的月初日期");
    Rules.check(
        db.rows(
                "SELECT id FROM settlement WHERE org_id=? AND month_on=? AND state IN ('DRAFT','APPROVED','PAID')",
                org,
                month)
            .isEmpty(),
        "该月已有有效结算；补录或冲正前先撤销旧结算");
    var receipts =
        db.rows(
            "SELECT r.*,c.hq_bps FROM receipt r JOIN bill b ON b.id=r.bill_id JOIN rental_contract c ON c.id=b.contract_id WHERE r.org_id=? AND r.state='APPROVED' AND r.received_on>=? AND r.received_on<? AND NOT EXISTS(SELECT 1 FROM settlement_item i WHERE i.item_kind='RECEIPT' AND i.source_id=r.id AND i.active=TRUE)",
            org,
            month,
            month.plusMonths(1));
    var costs =
        db.rows(
            "SELECT c.* FROM operating_cost c WHERE c.org_id=? AND c.state='APPROVED' AND c.incurred_on>=? AND c.incurred_on<? AND NOT EXISTS(SELECT 1 FROM settlement_item i WHERE i.item_kind='COST' AND i.source_id=c.id AND i.active=TRUE)",
            org,
            month,
            month.plusMonths(1));
    Rules.check(!receipts.isEmpty() || !costs.isEmpty(), "该月没有可结算的核销或批准成本");
    long collected = 0, gross = 0, cost = 0;
    for (var r : receipts) {
      collected = Math.addExact(collected, Database.id(r, "amount_cents"));
      gross =
          Math.addExact(
              gross, Rules.share(Database.id(r, "amount_cents"), Database.id(r, "hq_bps")));
    }
    for (var c : costs) cost = Math.addExact(cost, Database.id(c, "amount_cents"));
    long id =
        db.insert(
            "settlement",
            Map.of(
                "org_id",
                org,
                "month_on",
                month,
                "collected_cents",
                collected,
                "hq_gross_cents",
                gross,
                "cost_cents",
                cost,
                "hq_net_cents",
                gross - cost,
                "state",
                "DRAFT",
                "creator_id",
                a.get("id")));
    for (var r : receipts)
      db.insert(
          "settlement_item",
          Map.of(
              "settlement_id",
              id,
              "item_kind",
              "RECEIPT",
              "source_id",
              r.get("id"),
              "amount_cents",
              r.get("amount_cents"),
              "hq_share_cents",
              Rules.share(Database.id(r, "amount_cents"), Database.id(r, "hq_bps"))));
    for (var c : costs)
      db.insert(
          "settlement_item",
          Map.of(
              "settlement_id",
              id,
              "item_kind",
              "COST",
              "source_id",
              c.get("id"),
              "amount_cents",
              c.get("amount_cents"),
              "hq_share_cents",
              0));
    access.audit(a, "CREATE", "settlements", id, "");
    return Map.of("id", id);
  }

  void unlocked(String kind, long id) {
    Rules.check(
        db.rows(
                "SELECT id FROM settlement_item WHERE item_kind=? AND source_id=? AND active=TRUE",
                kind,
                id)
            .isEmpty(),
        "明细已被结算锁定，请先撤销结算");
  }

  void event(Map<String, Object> a, long id, Long org, String action, String note) {
    var v = new LinkedHashMap<String, Object>();
    v.put("asset_id", id);
    v.put("org_id", org);
    v.put("action", action);
    v.put("note", note);
    v.put("actor_id", a.get("id"));
    db.insert("asset_event", v);
  }

  /** 状态变更、核销及冲正原子执行，审批与制单分离。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional
  public Map<String, Boolean> action(
      String resource, long id, String action, Map<String, Object> b) {
    db.lock();
    var a = viewer(resource);
    var r = row(resource, id, a);
    version(r, b);
    String note = Rules.text(b.get("note"), 400);
    switch (resource) {
      case "assets" -> {
        switch (action) {
          case "allocate" -> {
            access.hq(a);
            state(r, "HQ_AVAILABLE");
            long org = Rules.number(b.get("org_id"));
            access.org(a, org);
            db.update(
                "UPDATE asset SET org_id=?,state='IN_TRANSIT',changed_on=?,version=version+1 WHERE id=?",
                org,
                Rules.today(),
                id);
          }
          case "accept" -> {
            Rules.check("PARTNER".equals(a.get("scope")), "须由合作商本人确认接收");
            state(r, "IN_TRANSIT");
            change("asset", id, "PARTNER_AVAILABLE");
          }
          case "maintain" -> {
            state(r, "HQ_AVAILABLE", "PARTNER_AVAILABLE");
            change("asset", id, "MAINTENANCE");
          }
          case "repair" -> {
            state(r, "MAINTENANCE");
            change("asset", id, r.get("org_id") == null ? "HQ_AVAILABLE" : "PARTNER_AVAILABLE");
          }
          case "recall" -> {
            access.hq(a);
            state(r, "PARTNER_AVAILABLE");
            change("asset", id, "IN_RETURN");
          }
          case "receive" -> {
            access.hq(a);
            state(r, "IN_RETURN");
            db.update(
                "UPDATE asset SET state='HQ_AVAILABLE',org_id=NULL,version=version+1 WHERE id=?",
                id);
          }
          case "dispose" -> {
            access.hq(a);
            state(r, "HQ_AVAILABLE");
            change("asset", id, "DISPOSED");
          }
          default -> throw new Problem(400, "无效设备操作");
        }
        db.update("UPDATE asset SET changed_on=? WHERE id=?", Rules.today(), id);
        Long eventOrg = null;
        if (r.get("org_id") != null) eventOrg = Database.id(r, "org_id");
        else if (b.get("org_id") != null) eventOrg = Rules.number(b.get("org_id"));
        event(a, id, eventOrg, action, note);
      }
      case "contracts" -> {
        if (action.equals("accept-delivery") || action.equals("confirm-return")) {
          Rules.check("CUSTOMER".equals(a.get("scope")), "须由客户本人确认设备交接");
          state(r, "ACTIVE");
          if (action.equals("accept-delivery")) {
            Rules.check("DISPATCHED".equals(r.get("delivery_state")), "设备尚未登记交付或已经确认");
            db.update(
                "UPDATE rental_contract SET delivery_state='ACCEPTED',version=version+1 WHERE id=?",
                id);
            change("asset", Database.id(r, "asset_id"), "RENTED");
          } else {
            Rules.check("RETURN_PENDING".equals(r.get("delivery_state")), "尚无待确认归还");
            db.update(
                "UPDATE rental_contract SET state='RETURNED',delivery_state='RETURNED',version=version+1 WHERE id=?",
                id);
            change("asset", Database.id(r, "asset_id"), "PARTNER_AVAILABLE");
          }
        } else if (action.equals("confirm")) {
          Rules.check("CUSTOMER".equals(a.get("scope")), "须由合同客户本人确认");
          state(r, "PENDING");
          var asset = db.one("SELECT * FROM asset WHERE id=?", r.get("asset_id"));
          state(asset, "RESERVED");
          change("rental_contract", id, "ACTIVE");

          for (var p :
              Rules.periods(
                  Rules.date(r.get("start_on")),
                  Rules.date(r.get("end_on")),
                  Database.id(r, "daily_cents"))) {
            var v = new LinkedHashMap<String, Object>(p);
            v.put("org_id", r.get("org_id"));
            v.put("customer_id", r.get("customer_id"));
            v.put("contract_id", id);
            db.insert("bill", v);
          }
        } else {
          Rules.check(!"CUSTOMER".equals(a.get("scope")), "客户只能确认自己的合同");
          switch (action) {
            case "submit" -> {
              state(r, "DRAFT");
              var asset =
                  db.one(
                      "SELECT * FROM asset WHERE id=? AND org_id=?",
                      r.get("asset_id"),
                      r.get("org_id"));
              state(asset, "PARTNER_AVAILABLE");
              Rules.check(
                  !db.rows(
                          "SELECT a.id FROM account a JOIN access_role x ON x.id=a.role_id WHERE a.customer_id=? AND a.enabled=TRUE AND x.scope='CUSTOMER'",
                          r.get("customer_id"))
                      .isEmpty(),
                  "客户尚无可用确认账号");
              var rate = db.one("SELECT * FROM rate_version WHERE id=?", r.get("rate_id"));
              Rules.check(Database.enabled(rate), "费率已停用");
              change("asset", Database.id(asset, "id"), "RESERVED");
              change("rental_contract", id, "PENDING");
            }
            case "cancel" -> {
              state(r, "DRAFT", "PENDING");
              if (r.get("state").equals("PENDING"))
                change("asset", Database.id(r, "asset_id"), "PARTNER_AVAILABLE");
              change("rental_contract", id, "CANCELLED");
            }
            case "deliver" -> {
              Rules.check("PARTNER".equals(a.get("scope")), "须由合作商登记实际交付");
              state(r, "ACTIVE");
              Rules.check("WAITING".equals(r.get("delivery_state")), "交付已经登记");
              LocalDate day = Rules.date(b.get("delivered_on"));
              Rules.check(
                  !day.isAfter(Rules.today())
                      && !day.isBefore(Rules.date(r.get("start_on")))
                      && day.isBefore(Rules.date(r.get("end_on"))),
                  "实际交付日须在合同期内且不晚于今天");
              db.update(
                  "UPDATE rental_contract SET delivery_state='DISPATCHED',delivered_on=?,delivery_note=?,version=version+1 WHERE id=?",
                  day,
                  note,
                  id);
            }
            case "return" -> {
              Rules.check("PARTNER".equals(a.get("scope")), "须由合作商登记实际归还");
              state(r, "ACTIVE");
              Rules.check("ACCEPTED".equals(r.get("delivery_state")), "设备尚未确认交付或归还待确认");
              LocalDate day = Rules.date(b.get("return_on"));
              Rules.check(
                  !day.isAfter(Rules.today()) && !day.isBefore(Rules.date(r.get("delivered_on"))),
                  "归还日期不可早于交付或晚于今天");
              db.update(
                  "UPDATE rental_contract SET delivery_state='RETURN_PENDING',return_on=?,return_note=?,version=version+1 WHERE id=?",
                  day,
                  note,
                  id);
            }
            case "cancel-return" -> {
              Rules.check("PARTNER".equals(a.get("scope")), "须由合作商撤销归还登记");
              state(r, "ACTIVE");
              Rules.check("RETURN_PENDING".equals(r.get("delivery_state")), "无待确认归还");
              db.update(
                  "UPDATE rental_contract SET delivery_state='ACCEPTED',return_on=NULL,return_note=NULL,version=version+1 WHERE id=?",
                  id);
            }
            default -> throw new Problem(400, "无效合同操作");
          }
        }
        db.insert(
            "contract_event",
            Map.of("contract_id", id, "action", action, "note", note, "actor_id", a.get("id")));
      }
      case "receipts" -> {
        access.require("approve");
        access.hq(a);
        if (action.equals("approve")) {
          state(r, "PENDING");
          var bill = db.one("SELECT * FROM bill WHERE id=?", r.get("bill_id"));
          Rules.check(
              Database.id(r, "amount_cents")
                  <= Database.id(bill, "amount_cents") - Database.id(bill, "paid_cents"),
              "金额超过未收余额");
          db.update(
              "UPDATE bill SET paid_cents=paid_cents+? WHERE id=?",
              r.get("amount_cents"),
              bill.get("id"));
          change("receipt", id, "APPROVED");
        } else if (action.equals("reject")) {
          state(r, "PENDING");
          change("receipt", id, "REJECTED");
        } else if (action.equals("reverse")) {
          state(r, "APPROVED");
          unlocked("RECEIPT", id);
          db.update(
              "UPDATE bill SET paid_cents=paid_cents-? WHERE id=?",
              r.get("amount_cents"),
              r.get("bill_id"));
          change("receipt", id, "REVERSED");
        } else throw new Problem(400, "无效核销操作");
        db.update("UPDATE receipt SET reviewer_id=? WHERE id=?", a.get("id"), id);
      }
      case "costs" -> {
        access.require("approve");
        access.hq(a);
        if (action.equals("approve") || action.equals("reject")) {
          state(r, "PENDING");
          change("operating_cost", id, action.equals("approve") ? "APPROVED" : "REJECTED");
        } else if (action.equals("reverse")) {
          state(r, "APPROVED");
          unlocked("COST", id);
          change("operating_cost", id, "REVERSED");
        } else throw new Problem(400, "无效成本操作");
        db.update("UPDATE operating_cost SET reviewer_id=? WHERE id=?", a.get("id"), id);
      }
      case "settlements" -> {
        switch (action) {
          case "approve" -> {
            access.require("approve");
            access.hq(a);
            state(r, "DRAFT");
            Rules.check(!Objects.equals(a.get("id"), r.get("creator_id")), "制单人不得审核自己的结算");
            change("settlement", id, "APPROVED");
            db.update("UPDATE settlement SET reviewer_id=? WHERE id=?", a.get("id"), id);
          }
          case "cancel" -> {
            state(r, "DRAFT");
            if (!Objects.equals(a.get("id"), r.get("creator_id"))) {
              access.require("approve");
              access.hq(a);
            }
            change("settlement", id, "CANCELLED");
            db.update("UPDATE settlement_item SET active=FALSE WHERE settlement_id=?", id);
          }
          case "reverse" -> {
            access.require("approve");
            access.hq(a);
            state(r, "APPROVED");
            change("settlement", id, "REVERSED");
            db.update("UPDATE settlement_item SET active=FALSE WHERE settlement_id=?", id);
          }
          case "pay", "unpay" -> {
            access.require("approve");
            access.hq(a);
            state(r, action.equals("pay") ? "APPROVED" : "PAID");
            LocalDate day = Rules.date(b.get("transferred_on"));
            Rules.check(!day.isAfter(Rules.today()), "转款日期不可晚于今天");
            long amount = Database.id(r, "hq_net_cents");
            String direction = amount > 0 ? "PARTNER_TO_HQ" : amount < 0 ? "HQ_TO_PARTNER" : "ZERO";
            db.insert(
                "transfer_entry",
                Map.of(
                    "org_id",
                    r.get("org_id"),
                    "settlement_id",
                    id,
                    "action",
                    action.equals("pay") ? "PAYMENT" : "REVERSAL",
                    "direction",
                    direction,
                    "amount_cents",
                    Math.abs(amount),
                    "transferred_on",
                    day,
                    "reference",
                    Rules.text(b.get("reference"), 120),
                    "note",
                    note,
                    "actor_id",
                    a.get("id")));
            change("settlement", id, action.equals("pay") ? "PAID" : "APPROVED");
          }
          default -> throw new Problem(400, "无效结算操作");
        }
      }
      case "risks" -> {
        switch (action) {
          case "follow" -> {
            state(r, "OPEN");
            db.insert("case_note", Map.of("case_id", id, "note", note, "actor_id", a.get("id")));
            db.update(
                "UPDATE risk_case SET next_on=?,version=version+1 WHERE id=?",
                Rules.date(b.get("next_on")),
                id);
          }
          case "close" -> {
            state(r, "OPEN");
            change("risk_case", id, "CLOSED");
            db.insert("case_note", Map.of("case_id", id, "note", note, "actor_id", a.get("id")));
          }
          case "reopen" -> {
            state(r, "CLOSED");
            change("risk_case", id, "OPEN");
            db.insert("case_note", Map.of("case_id", id, "note", note, "actor_id", a.get("id")));
          }
          default -> throw new Problem(400, "无效风险操作");
        }
      }
      case "accounts" -> {
        access.hq(a);
        Rules.check(id != 1, "初始管理员请通过本人修改密码");
        Rules.check(action.equals("reset-password"), "无效账号操作");
        String password = Rules.text(b.get("password"), 128);
        Rules.password(password);
        db.update(
            "UPDATE account SET password_hash=?,version=version+1 WHERE id=?",
            encoder.encode(password),
            id);
      }
      default -> throw new Problem(400, "此记录无业务动作");
    }
    access.audit(a, action.toUpperCase(Locale.ROOT), resource, id, note);
    return Map.of("ok", true);
  }

  /** 对象历史与关联明细同样验证作用域。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Map<String, Object> detail(String resource, long id) {
    var a = viewer(resource);
    var r = new LinkedHashMap<>(row(resource, id, a));
    customerView(resource, a, r);
    List<Map<String, Object>> items =
        switch (resource) {
          case "assets" -> db.rows("SELECT * FROM asset_event WHERE asset_id=? ORDER BY id", id);
          case "contracts" -> {
            var bills = db.rows("SELECT * FROM bill WHERE contract_id=? ORDER BY start_on", id);
            r.put(
                "total_cents",
                Rules.periods(
                        Rules.date(r.get("start_on")),
                        Rules.date(r.get("end_on")),
                        Database.id(r, "daily_cents"))
                    .stream()
                    .mapToLong(p -> Database.id(p, "amount_cents"))
                    .sum());
            if (bills.isEmpty() && List.of("DRAFT", "PENDING").contains(r.get("state")))
              yield Rules.periods(
                  Rules.date(r.get("start_on")),
                  Rules.date(r.get("end_on")),
                  Database.id(r, "daily_cents"));
            yield bills;
          }
          case "settlements" ->
              db.rows("SELECT * FROM settlement_item WHERE settlement_id=? ORDER BY id", id);
          case "risks" -> db.rows("SELECT * FROM case_note WHERE case_id=? ORDER BY id", id);
          default -> List.of();
        };
    return Map.of(
        "record",
        r,
        "items",
        items,
        "events",
        resource.equals("contracts")
            ? db.rows("SELECT * FROM contract_event WHERE contract_id=? ORDER BY id", id)
            : List.of(),
        "transfers",
        resource.equals("settlements")
            ? db.rows("SELECT * FROM transfer_entry WHERE settlement_id=? ORDER BY id", id)
            : List.of());
  }

  /** 根据当前账号数据计算统计与预警，不返回跨机构数据。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Map<String, Object> report() {
    var a = access.require("reports");
    String f = access.filter(a, false);
    long idle =
        Long.parseLong(
            db.one("SELECT value FROM system_setting WHERE code='idle_days'")
                .get("value")
                .toString());
    var totals =
        db.one(
            "SELECT COALESCE(SUM(amount_cents),0) AS billed,COALESCE(SUM(paid_cents),0) AS collected,COALESCE(SUM(CASE WHEN due_on<? THEN amount_cents-paid_cents ELSE 0 END),0) AS overdue FROM bill WHERE "
                + f,
            Rules.today());
    var states =
        db.rows(
            "SELECT state,COUNT(*) AS total FROM asset WHERE "
                + f
                + " GROUP BY state ORDER BY state");
    var months =
        db.rows(
            "SELECT month_on,SUM(collected_cents) AS collected_cents,SUM(hq_net_cents) AS hq_net_cents,SUM(cost_cents) AS cost_cents FROM settlement WHERE "
                + f
                + " AND state IN ('APPROVED','PAID') GROUP BY month_on ORDER BY month_on DESC LIMIT 24");
    var overdue =
        db.rows(
            "SELECT * FROM bill WHERE "
                + f
                + " AND due_on<? AND amount_cents>paid_cents ORDER BY due_on LIMIT 100",
            Rules.today());
    var idleRows =
        db.rows(
            "SELECT * FROM asset WHERE "
                + f
                + " AND state IN ('HQ_AVAILABLE','PARTNER_AVAILABLE') AND changed_on<=? ORDER BY changed_on LIMIT 100",
            Rules.today().minusDays(idle));
    var due =
        db.rows(
            "SELECT * FROM risk_case WHERE "
                + f
                + " AND state='OPEN' AND next_on<=? ORDER BY next_on LIMIT 100",
            Rules.today());
    return Map.of(
        "totals",
        totals,
        "states",
        states,
        "months",
        months,
        "overdue",
        overdue,
        "idle",
        idleRows,
        "followups",
        due,
        "today",
        Rules.today());
  }

  /** 分页导出当前权限内的业务账表；不附广告或私人凭证。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public String export(String resource) {
    Rules.check(
        Set.of("assets", "contracts", "bills", "receipts", "costs", "settlements")
            .contains(resource),
        "此资源不可导出");
    var a = viewer(resource);
    access.require("reports");
    var rows =
        db.rows(
            "SELECT * FROM "
                + TABLES.get(resource)
                + " WHERE "
                + predicate(resource, a)
                + " ORDER BY id LIMIT 10001");
    Rules.check(rows.size() <= 10000, "导出超过10000条，请分机构处理");
    if (rows.isEmpty()) return "id\r\n";
    var keys = new ArrayList<>(rows.getFirst().keySet());
    StringBuilder out = new StringBuilder("\ufeff");
    out.append(String.join(",", keys)).append("\r\n");
    for (var row : rows) {
      List<String> cells = new ArrayList<>();
      for (String key : keys) cells.add(Rules.csv(row.get(key)));
      out.append(String.join(",", cells)).append("\r\n");
    }
    return out.toString();
  }
}
