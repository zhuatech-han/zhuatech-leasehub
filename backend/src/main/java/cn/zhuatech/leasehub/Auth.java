// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.leasehub;

import jakarta.servlet.http.*;
import java.time.*;
import java.util.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** 同源会话登录、限流及密码失效。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@RestController
@RequestMapping("/api/auth")
public class Auth {
  final Database db;
  final Access access;
  final BCryptPasswordEncoder encoder;
  final Map<String, Attempt> attempts = new LinkedHashMap<>();

  record Attempt(int count, Instant until) {}

  public Auth(Database db, Access access, BCryptPasswordEncoder encoder) {
    this.db = db;
    this.access = access;
    this.encoder = encoder;
  }

  /** 会话CSRF令牌引导。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/csrf")
  public Map<String, String> csrf(CsrfToken token) {
    return Map.of("header", token.getHeaderName(), "token", token.getToken());
  }

  /** 登录、节流与会话标识更新。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/login")
  @Transactional
  public synchronized Map<String, Object> login(
      @RequestBody Map<String, Object> b, HttpServletRequest req, HttpServletResponse res) {
    String u = Rules.text(b.get("username"), 60).toLowerCase(Locale.ROOT),
        p = Rules.text(b.get("password"), 128);
    String key = req.getRemoteAddr() + ":" + u;
    Instant now = Instant.now();
    attempts.entrySet().removeIf(e -> e.getValue().until.isBefore(now));
    var prior = attempts.get(key);
    if (prior != null && prior.count >= 8) throw new Problem(429, "登录尝试过多，请5分钟后重试");
    var rows = db.rows("SELECT * FROM account WHERE username=?", u);
    var a = rows.isEmpty() ? null : rows.getFirst();
    if (a == null
        || !encoder.matches(p, a.get("password_hash").toString())
        || !Database.enabled(a)) {
      if (attempts.size() >= 2000) attempts.remove(attempts.keySet().iterator().next());
      attempts.put(key, new Attempt(prior == null ? 1 : prior.count + 1, now.plusSeconds(300)));
      throw new Problem(401, "用户名或密码错误");
    }
    if (a.get("org_id") != null
        && !Database.enabled(db.one("SELECT * FROM organization WHERE id=?", a.get("org_id"))))
      throw new Problem(401, "用户名或密码错误");
    attempts.remove(key);
    req.getSession();
    req.changeSessionId();
    var ctx = SecurityContextHolder.createEmptyContext();
    var auth = UsernamePasswordAuthenticationToken.authenticated(u, null, List.of());
    auth.setDetails(a.get("password_hash"));
    ctx.setAuthentication(auth);
    SecurityContextHolder.setContext(ctx);
    new HttpSessionSecurityContextRepository().saveContext(ctx, req, res);
    access.audit(a, "LOGIN", "account", Database.id(a, "id"), "");
    return access.profile();
  }

  /** 当前账号、角色、菜单和配置。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/me")
  @Transactional(readOnly = true)
  public Map<String, Object> me() {
    return access.profile();
  }

  /** 使当前会话失效。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/logout")
  public Map<String, Boolean> logout(HttpServletRequest req) {
    var s = req.getSession(false);
    if (s != null) s.invalidate();
    SecurityContextHolder.clearContext();
    return Map.of("ok", true);
  }

  /** 核验旧密码并撤销会话。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/password")
  @Transactional
  public Map<String, Boolean> password(@RequestBody Map<String, Object> b, HttpServletRequest req) {
    db.lock();
    var a = access.current();
    if (!encoder.matches(Rules.text(b.get("oldPassword"), 128), a.get("password_hash").toString()))
      throw new Problem(400, "旧密码不正确");
    var p = Rules.text(b.get("newPassword"), 128);
    Rules.password(p);
    db.update(
        "UPDATE account SET password_hash=?,version=version+1 WHERE id=?",
        encoder.encode(p),
        a.get("id"));
    access.audit(a, "PASSWORD_CHANGE", "account", Database.id(a, "id"), "");
    return logout(req);
  }
}
