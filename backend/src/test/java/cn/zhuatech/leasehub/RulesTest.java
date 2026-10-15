// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.leasehub;

import static org.junit.jupiter.api.Assertions.*;

import java.time.*;
import org.junit.jupiter.api.Test;

/** 金额和日历边界测试。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
class RulesTest {
  @Test
  void monthEndAndLeapYear() {
    var p = Rules.periods(LocalDate.of(2024, 1, 31), LocalDate.of(2024, 4, 2), 100);
    assertEquals(3, p.size());
    assertEquals(2900L, p.get(0).get("amount_cents"));
    assertEquals(LocalDate.of(2024, 2, 29), p.get(1).get("start_on"));
    assertEquals(LocalDate.of(2024, 3, 31), p.get(1).get("end_on"));
    assertEquals(200L, p.get(2).get("amount_cents"));
  }

  @Test
  void invalidRangeAndMoney() {
    assertThrows(Problem.class, () -> Rules.periods(LocalDate.now(), LocalDate.now(), 100));
    assertThrows(
        Problem.class, () -> Rules.periods(LocalDate.now(), LocalDate.now().plusDays(367), 100));
    assertThrows(Problem.class, () -> Rules.money("1.2"));
    assertThrows(Problem.class, () -> Rules.money(-1));
    assertThrows(Problem.class, () -> Rules.share(100, 10001));
  }

  @Test
  void fractionalSplit() {
    assertEquals(33, Rules.share(101, 3333));
    assertEquals(0, Rules.share(1, 9999));
    assertEquals(101, Rules.share(101, 10000));
  }

  @Test
  void exportFormulaProtection() {
    assertEquals("\"'=1+1\"", Rules.csv("=1+1"));
    assertEquals("\"'  @SUM(A1)\"", Rules.csv("  @SUM(A1)"));
    assertEquals("\"hello\"\"world\"", Rules.csv("hello\"world"));
    assertTrue(Rules.csv("=1\n+1").startsWith("\"'"));
  }

  @Test
  void publicMoneyAvoidsBrowserIntegerPrecisionLoss() {
    var view =
        Api.publicMoney(
            java.util.Map.of(
                "totals",
                java.util.Map.of("collected", 9007199254740993L),
                "items",
                java.util.List.of(java.util.Map.of("amount_cents", 101L, "id", 7L))));
    assertEquals("9007199254740993", ((java.util.Map<?, ?>) view.get("totals")).get("collected"));
    assertEquals(
        7L, ((java.util.Map<?, ?>) ((java.util.List<?>) view.get("items")).getFirst()).get("id"));
  }
}
