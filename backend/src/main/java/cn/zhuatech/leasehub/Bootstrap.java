// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.leasehub;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** 初始化权限和唯一总部管理员，不生成业务演示数据。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Component
public class Bootstrap implements CommandLineRunner {
  final Database db;
  final TransactionTemplate tx;
  final BCryptPasswordEncoder encoder;
  final String username, password;

  public Bootstrap(
      Database db,
      TransactionTemplate tx,
      BCryptPasswordEncoder encoder,
      @Value("${leasehub.admin-username}") String username,
      @Value("${leasehub.admin-password}") String password) {
    this.db = db;
    this.tx = tx;
    this.encoder = encoder;
    this.username = username;
    this.password = password;
  }

  @Override
  public void run(String... args) {
    tx.executeWithoutResult(
        s -> {
          db.lock();
          if (!db.rows("SELECT id FROM account").isEmpty()) return;
          Rules.password(password);
          Rules.check(username.matches("[a-z0-9_.-]{3,60}"), "管理员用户名无效");
          long root =
              db.insert(
                  "access_role",
                  Map.of(
                      "name",
                      "总部管理员",
                      "scope",
                      "HQ",
                      "permissions",
                      "admin,assets,contracts,finance,approve,risk,reports"));
          db.insert(
              "access_role",
              Map.of(
                  "name", "总部运营", "scope", "HQ", "permissions", "assets,contracts,risk,reports"));
          db.insert(
              "access_role",
              Map.of("name", "总部财务", "scope", "HQ", "permissions", "finance,approve,reports"));
          db.insert(
              "access_role",
              Map.of(
                  "name",
                  "合作商经理",
                  "scope",
                  "PARTNER",
                  "permissions",
                  "assets,contracts,finance,risk,reports"));
          db.insert(
              "access_role", Map.of("name", "终端客户", "scope", "CUSTOMER", "permissions", "portal"));
          db.insert(
              "account",
              Map.of(
                  "username",
                  username,
                  "display_name",
                  "总部管理员",
                  "password_hash",
                  encoder.encode(password),
                  "role_id",
                  root));
          String[][] menus = {
            {"overview", "经营总览", "reports"},
            {"assets", "设备与投放", "assets"},
            {"customers", "客户资料", "contracts"},
            {"rates", "费率版本", "contracts"},
            {"contracts", "租赁合同", "contracts"},
            {"bills", "分期账单", "finance"},
            {"receipts", "收款核销", "finance"},
            {"costs", "运营成本", "finance"},
            {"settlements", "分成结算", "finance"},
            {"risks", "风险跟进", "risk"},
            {"portal", "我的租赁", "portal"},
            {"reports", "经营报表", "reports"},
            {"admin", "系统管理", "admin"}
          };
          int p = 0;
          for (var m : menus)
            db.insert(
                "nav_menu",
                Map.of("code", m[0], "label", m[1], "permission", m[2], "position", ++p));
          for (var v :
              List.of(
                  new String[] {"asset_category", "engineering", "工程机械"},
                  new String[] {"asset_category", "industrial", "工业设备"},
                  new String[] {"asset_category", "energy", "能源设备"},
                  new String[] {"cost_category", "maintenance", "维保费用"},
                  new String[] {"cost_category", "transport", "运输费用"}))
            db.insert("dictionary_entry", Map.of("kind", v[0], "code", v[1], "label", v[2]));
          db.insert(
              "system_setting", Map.of("code", "idle_days", "label", "闲置预警天数", "value", "30"));
          db.insert(
              "system_setting",
              Map.of("code", "platform_name", "label", "平台名称", "value", "知华 LeaseHub"));
        });
  }
}
