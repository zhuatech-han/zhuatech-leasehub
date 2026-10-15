// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.leasehub;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Component;

/** 参数化数据库访问与写事务协调。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Component
public class Database {
  final JdbcTemplate jdbc;

  public Database(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  List<Map<String, Object>> rows(String sql, Object... args) {
    return jdbc.query(
        sql,
        (rs, i) -> {
          Map<String, Object> m = new LinkedHashMap<>();
          for (int c = 1; c <= rs.getMetaData().getColumnCount(); c++) {
            Object value = rs.getObject(c);
            if (value instanceof java.sql.Date date) value = date.toLocalDate();
            else if (value instanceof java.sql.Timestamp timestamp) value = timestamp.toInstant();
            m.put(rs.getMetaData().getColumnLabel(c).toLowerCase(Locale.ROOT), value);
          }
          return m;
        },
        args);
  }

  Map<String, Object> one(String sql, Object... args) {
    var r = rows(sql, args);
    if (r.isEmpty()) throw new Problem(404, "记录不存在或不可访问");
    return r.getFirst();
  }

  long insert(String table, Map<String, Object> values) {
    var keys = new ArrayList<>(values.keySet());
    var holder = new GeneratedKeyHolder();
    jdbc.update(
        con -> {
          var s =
              con.prepareStatement(
                  "INSERT INTO "
                      + table
                      + " ("
                      + String.join(",", keys)
                      + ") VALUES ("
                      + String.join(",", Collections.nCopies(keys.size(), "?"))
                      + ")",
                  new String[] {"id"});
          for (int i = 0; i < keys.size(); i++) s.setObject(i + 1, values.get(keys.get(i)));
          return s;
        },
        holder);
    return Objects.requireNonNull(holder.getKey()).longValue();
  }

  int update(String sql, Object... args) {
    return jdbc.update(sql, args);
  }

  void lock() {
    one("SELECT id FROM write_guard WHERE id=1 FOR UPDATE");
  }

  static long id(Map<String, Object> row, String key) {
    return ((Number) row.get(key)).longValue();
  }

  static boolean enabled(Map<String, Object> row) {
    return Boolean.TRUE.equals(row.get("enabled"))
        || "1".equals(String.valueOf(row.get("enabled")));
  }
}
