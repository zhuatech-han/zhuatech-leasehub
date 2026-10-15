// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.leasehub;

import java.util.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/** 当前权限、机构与客户范围每次重新验证。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
public class Access {
  final Database db;

  public Access(Database db) {
    this.db = db;
  }

  Map<String, Object> current() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName()))
      throw new Problem(401, "请先登录");
    var a =
        db.one(
            "SELECT a.*,r.scope,r.permissions,r.name AS role_name FROM account a JOIN access_role r ON r.id=a.role_id WHERE a.username=?",
            auth.getName());
    if (!Database.enabled(a) || !Objects.equals(auth.getDetails(), a.get("password_hash")))
      throw new Problem(401, "登录已失效，请重新登录");
    if (a.get("org_id") != null
        && !Database.enabled(db.one("SELECT * FROM organization WHERE id=?", a.get("org_id"))))
      throw new Problem(403, "合作商已停用");
    return a;
  }

  boolean can(Map<String, Object> a, String permission) {
    return Arrays.asList(a.get("permissions").toString().split(",")).contains(permission);
  }

  Map<String, Object> require(String permission) {
    var a = current();
    if (!can(a, permission)) throw new Problem(403, "无此操作权限");
    return a;
  }

  void hq(Map<String, Object> a) {
    if (!"HQ".equals(a.get("scope"))) throw new Problem(403, "仅总部可操作");
  }

  void scope(Map<String, Object> a, Map<String, Object> row) {
    if ("HQ".equals(a.get("scope"))) return;
    if (!Objects.equals(a.get("org_id"), row.get("org_id"))) throw new Problem(404, "记录不存在或不可访问");
    if ("CUSTOMER".equals(a.get("scope"))
        && !Objects.equals(a.get("customer_id"), row.get("customer_id")))
      throw new Problem(404, "记录不存在或不可访问");
  }

  void org(Map<String, Object> a, long id) {
    var row = db.one("SELECT * FROM organization WHERE id=?", id);
    Rules.check(Database.enabled(row), "合作商已停用");
    if (!"HQ".equals(a.get("scope")) && !Objects.equals(a.get("org_id"), id))
      throw new Problem(404, "记录不存在或不可访问");
  }

  String filter(Map<String, Object> a, boolean customer) {
    if ("HQ".equals(a.get("scope"))) return "1=1";
    if ("CUSTOMER".equals(a.get("scope")) && !customer) throw new Problem(403, "无此数据权限");
    return "org_id="
        + Database.id(a, "org_id")
        + ("CUSTOMER".equals(a.get("scope"))
            ? " AND customer_id=" + Database.id(a, "customer_id")
            : "");
  }

  void audit(Map<String, Object> a, String action, String type, long id, String note) {
    var v = new LinkedHashMap<String, Object>();
    v.put("actor_id", a.get("id"));
    v.put("org_id", a.get("org_id"));
    v.put("action", action);
    v.put("object_type", type);
    v.put("object_id", id);
    v.put("note", note);
    db.insert("audit_event", v);
  }

  Map<String, Object> profile() {
    var a = new LinkedHashMap<>(current());
    a.remove("password_hash");
    var perms = List.of(a.get("permissions").toString().split(","));
    var menus =
        db.rows("SELECT * FROM nav_menu WHERE enabled=TRUE ORDER BY position,id").stream()
            .filter(m -> perms.contains(m.get("permission")))
            .toList();
    return Map.of(
        "account",
        a,
        "permissions",
        perms,
        "menus",
        menus,
        "settings",
        db.rows("SELECT code,label,value,version,id FROM system_setting"));
  }
}
