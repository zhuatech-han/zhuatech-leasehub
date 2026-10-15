# LeaseHub 技术架构

知华科技（上海如静知华信息科技有限公司） · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2。

Vue 3 / Vite → 同源 Nginx → Java 21 / Spring Boot 4.0.7 → MySQL 8.4；Flyway 版本化建表。角色和对象归属均在后端检查，菜单可见性只辅助操作。

数据库包含机构、角色、账号、客户、设备、交接历史、费率版本、合同、分期账单、收款、成本、结算及明细、转款流水、风险工单、跟进记录、字典、设置、菜单和审计。所有写事务先锁协调行，再检查当前账号/角色和对象版本。金额 long 整数分，JSON金额输出为十进制字符串；业务日期 LocalDate，时区 Asia/Shanghai，数据库时间 UTC。

会话 Cookie HttpOnly/SameSite，CSRF 保护写入，登录限流、BCrypt 密码散列、修改密码后失效；账号停用/密码重置在每次请求检查。首次启动仅创建强密码管理员和权限、菜单、配置；不植入假客户或假收入。

第三方源码未并入业务实现。基础构建、安全配置和品牌原图复用知华自有 PetStay，自有许可见根目录 LICENSE；依赖单独保留第三方许可。
