// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.leasehub;

import java.time.*;
import java.util.*;

/** 金额、日期和输入边界。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
public final class Rules {
  private Rules() {}

  static void check(boolean ok, String message) {
    if (!ok) throw new Problem(400, message);
  }

  static String text(Object value, int max) {
    String s = value == null ? "" : value.toString().trim();
    check(!s.isEmpty() && s.length() <= max, "请填写有效内容（最多" + max + "字）");
    return s;
  }

  static long number(Object o) {
    try {
      if (o == null || !o.toString().matches("-?[0-9]{1,15}")) throw new IllegalArgumentException();
      return Long.parseLong(o.toString());
    } catch (Exception e) {
      throw new Problem(400, "请输入整数");
    }
  }

  static long money(Object o) {
    long n = number(o);
    check(n > 0 && n <= 100_000_000_000L, "金额须大于零且不超过10亿元");
    return n;
  }

  static LocalDate date(Object o) {
    try {
      return LocalDate.parse(o.toString());
    } catch (Exception e) {
      throw new Problem(400, "日期格式应为 yyyy-MM-dd");
    }
  }

  static LocalDate today() {
    return LocalDate.now(ZoneId.of("Asia/Shanghai"));
  }

  static void password(String p) {
    check(
        p.length() >= 12
            && p.length() <= 128
            && p.matches(".*[a-z].*")
            && p.matches(".*[A-Z].*")
            && p.matches(".*[0-9].*"),
        "密码须12–128位并含大小写字母及数字");
  }

  /** 按合同起日逐月分段并冻结日租金，含开始不含结束。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static List<Map<String, Object>> periods(LocalDate start, LocalDate end, long daily) {
    check(end.isAfter(start) && !end.isAfter(start.plusDays(366)), "合同期限须1–366天");
    List<Map<String, Object>> out = new ArrayList<>();
    for (int month = 0; ; month++) {
      LocalDate from = start.plusMonths(month);
      if (!from.isBefore(end)) break;
      LocalDate to = start.plusMonths(month + 1);
      if (to.isAfter(end)) to = end;
      out.add(
          Map.of(
              "start_on",
              from,
              "end_on",
              to,
              "due_on",
              to,
              "amount_cents",
              Math.multiplyExact(java.time.temporal.ChronoUnit.DAYS.between(from, to), daily)));
    }
    return out;
  }

  /** 每笔总部份额向下取整，合作商保留余数。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static long share(long cents, long bps) {
    check(cents >= 0 && bps >= 0 && bps <= 10000, "无效分成参数");
    return Math.multiplyExact(cents, bps) / 10000;
  }

  /** 防止表格软件将业务文字解释成公式。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static String csv(Object value) {
    String s = value == null ? "" : value.toString();
    if (!s.stripLeading().isEmpty() && "=+@-".indexOf(s.stripLeading().charAt(0)) >= 0) s = "'" + s;
    return "\"" + s.replace("\"", "\"\"") + "\"";
  }
}
