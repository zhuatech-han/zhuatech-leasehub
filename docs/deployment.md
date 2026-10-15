# LeaseHub 部署与恢复

知华科技（上海如静知华信息科技有限公司） · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2。

## 环境

Docker Engine + Compose v2，建议可用内存6GB以上、至少10GB空闲磁盘。Java21、Maven3.9、MySQL8.4、Node24.19.0、Vue3.5.43、Vite8.1.5。Python3.12/3.13用于环境生成、验收、备份和恢复，格式化工具Black25.1.0。前后端以容器构建，无需在宿主安装Java和Node；源码开发或独立测试时需要对应运行时。

## 首次启动

```sh
python3 scripts/init-env.py
docker compose -p leasehub config --quiet
docker compose -p leasehub up --build -d --wait --wait-timeout 1200
```

init-env.py从.env.example生成仅当前用户可读的.env（0600），拒绝覆盖；随机生成数据库、root和admin强密码。首次账号默认admin，密码只在私有.env中，不在日志或README公开。妥善交给管理员后修改。已有库不重建或重置初始账号，修改ADMIN_PASSWORD环境变量不会重置库中密码。

默认网页 http://127.0.0.1:8162/，健康 http://127.0.0.1:8162/actuator/health。数据库与后端不映射宿主端口。初始化耗时取决于磁盘，特别是Docker数据盘在移动硬盘上的情况；检查指定项目日志，健康成功再登录，不使用恢复出厂或全局prune。

```sh
docker compose -p leasehub ps
docker compose -p leasehub logs --tail 100 mysql backend frontend
```

日志是私有诊断信息，对外分享前核查凭证和客户数据。先建机构、客户、员工和客户账号，再投放设备；数据库不自动生成示例业务数据。

## 环境配置

| 名称 | 用途 |
| --- | --- |
| MYSQL_ROOT_PASSWORD | 数据库容器初始化root凭证，仅私有环境文件 |
| DATABASE_PASSWORD | 应用数据库用户初始化及连接密码 |
| ADMIN_USERNAME / ADMIN_PASSWORD | 空库首次总部管理员；不会覆盖已有账号 |
| WEB_PORT / BIND_ADDRESS | 网页宿主端口与绑定地址，默认8162/127.0.0.1 |
| COOKIE_SECURE | 本机HTTP默认false；生产HTTPS设true |
| DATABASE_URL / DATABASE_USER | 可选外部MySQL连接；默认内部TLS连接 |
| LEASEHUB_MYSQL_DATA_DIR | 可选独立宿主数据目录，仅存储覆盖文件使用 |

闲置天数、平台名称、业务分类和菜单显示在后台设置，不使用.env存业务状态。费率是数据库版本记录。没有银行、短信、邮件或电子签章密钥配置项。

## 可选数据目录

默认命名卷，数据跟随Docker的数据盘。需要独立宿主磁盘时，可创建专属于本实例的新目录，在私有.env加入绝对路径LEASEHUB_MYSQL_DATA_DIR，并明确加载覆盖文件：

```sh
docker compose -p leasehub -f compose.yaml -f compose.storage.yaml config --quiet
docker compose -p leasehub -f compose.yaml -f compose.storage.yaml up --build -d --wait --wait-timeout 1200
```

禁止指向其他数据库目录；这不是搬迁现有卷的命令。现有数据迁移须先备份、恢复到新空实例并验收，不直接换路径丢失业务。目录权限由MySQL容器用户使用，不强行修改全机权限。

## 生产配置

在本机反向代理或网关提供受信任的HTTPS证书，设置COOKIE_SECURE=true，限制公网访问和登录总流量。使用生产CA核验MySQL身份（sslMode=VERIFY_IDENTITY），设置专用账号、数据库磁盘/备份加密、监控与恢复演练。默认内部数据库TLS为自签证书加密连接，不代替生产身份验证。

只支持一个总部、一个应用实例；会话保存在内存，重启重新登录。写协调锁适合低容量业务，会串行化写入，不宣称多实例SaaS或500并发/信创达标。

## 备份

输出必须是仓库外的新文件，权限0600。显式指定正在运行的leasehub实例和私有环境文件：

```sh
python3 scripts/backup.py --env-file .env --project leasehub --output /absolute/private/path/leasehub-backup.sql
```

使用一致性SQL快照；不要把备份、.env或真实客户资料放入源码包。备份含账号散列、合同和资金历史，按敏感数据保护。部署方决定备份调度、保留周期和异地存储。

## 独立恢复演练

restore.py只允许显式leasehub-restore-*项目且目标数据库为空；拒绝已有业务库。恢复输入必须私有可信本地SQL文件。先创建新的私有恢复环境文件，采用独立端口及全新卷/目录。仅启动该项目mysql，等待healthy；不要先启动后端，以免Flyway建表造成非空库。

```sh
docker compose --env-file /absolute/private/restore.env -p leasehub-restore-check up -d mysql
python3 scripts/restore.py --env-file /absolute/private/restore.env --project leasehub-restore-check --input /absolute/private/path/leasehub-backup.sql
docker compose --env-file /absolute/private/restore.env -p leasehub-restore-check up -d --wait --wait-timeout 1200
```

恢复完成后用原备份中账号密码登录，检查合同、账单、核销、结算明细和审计，启动验证Flyway校验和。若使用compose.storage.yaml，启动/停止均显式带-f compose.yaml -f compose.storage.yaml；备份脚本的exec操作按指定Compose项目定位已运行mysql，不启动或重新建卷。

只在确认恢复环境可丢弃后，停止并清理该独立项目。正式环境不得使用down -v；不要全局清理Docker。

## 数据库升级

backend/src/main/resources/db/migration/V1__leasehub.sql启动自动执行。升级新增V2/V3迁移文件，不修改已执行V1或Flyway历史。不使用Hibernate自动改表，不需要手工重复执行初始化SQL。先做备份和独立恢复演练，再使用维护窗口部署新版本。首版只有V1，没有经过历史客户数据迁移或旧版本生产升级验收。
