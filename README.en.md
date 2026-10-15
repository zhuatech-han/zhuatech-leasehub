[中文](README.md) | [English](README.en.md)

# ZhiHua LeaseHub · Equipment Leasing Headquarters and Partner Operations

**ZhiHua Technology (Shanghai Rujing Zhihua Information Technology Co., Ltd.)** · [Official website](https://www.zhuatech.cn/)

![ZhiHua Technology](frontend/public/brand/logo.jpg)

LeaseHub supports an operating lease model in which headquarters owns equipment, regional partners operate local rental businesses, and end customers use the equipment. It covers deployment, two-party handover, contracts, installment bills, receipt verification, operating costs, revenue sharing and risk follow-up. Java 21 / Spring Boot / Vue 3 / MySQL / Flyway / Docker Compose.

**Source available for non-commercial personal learning and exchange only. Commercial use requires prior written authorization from Shanghai Rujing Zhihua Information Technology Co., Ltd.** The application's own code is not offered under MIT, Apache or an OSI-approved open-source license. See [LICENSE](LICENSE); third-party components retain their licenses, indexed in [NOTICE](NOTICE). Enterprise deployment, project delivery and customization require separate authorization.

## Use cases and workspaces

- Headquarters: equipment investors, regional rental networks of equipment manufacturers, and operating lease departments; manage asset ownership, partner deployment, rate versions, verification and settlement approval.
- Partners: manage their own customers, contracts, handovers, offline receipt records, costs and follow-up work.
- End customers: access only their own contracts, frozen pricing, bills and verified receipts; confirm contracts, actual equipment receipt and return.

The scope is one headquarters with multiple partner organizations, not a multi-headquarters SaaS platform. It is a learning and evaluation application and a potential basis for authorized customization, not a claim of enterprise production readiness. The current application UI is Chinese.

## Implemented features

| Module | Capabilities |
| --- | --- |
| Assets and deployment | Unique serial numbers, investment cost and categories; headquarters allocation and partner acceptance; available/reserved/rented/maintenance/recall/disposal states; version checks and handover history |
| Contracts and fulfillment | Validate customer/asset/rate ownership; reserve on submission and require the actual customer to confirm; freeze customer and asset names, serial number, daily rate and headquarters share; partner delivery/return records followed by customer confirmation |
| Installment bills | Calendar-month periods anchored to the contract start day, charged by actual days; inclusive start and exclusive end, maximum 366 days; total and period preview before confirmation |
| Receipt verification | Partial receipts against one bill, unique partner reference numbers, headquarters verification, overpayment prevention and reversals; pending records do not count as collected money |
| Costs and settlement | Headquarters-funded operating costs require approval; frozen per-receipt share rounded down to cents; exclusive source allocation; independent approval; positive/negative net direction, manual transfer records and appended reversal history |
| Operations and risk | Actual overdue balances, idle assets, due follow-ups, assigned owners and notes; approved settlement reporting and safe CSV exports |
| Administration and security | Organizations, customers, accounts, roles within fixed scopes, permissions, menus, dictionaries, settings and audit; sessions, CSRF, BCrypt, login throttling and account/password invalidation |

Receipt and transfer entries are **manually verified offline records**; the system neither initiates nor verifies bank transfers. A customer page confirmation is not an electronic signature. Reminders are shown in application lists; email, SMS and WeChat delivery are not implemented.

## Actual running screenshots

These pages use isolated TEST data. They are not real customers, revenue or sales references. No passwords, secrets or customer records are published.

### Sign-in

![Sign-in](docs/screenshots/01-login.jpg)

### Headquarters overview: receivables, verified receipts and follow-ups

![Overview](docs/screenshots/02-overview.jpg)

### Assets and deployment: serial numbers, partners and lifecycle

![Assets](docs/screenshots/03-assets.jpg)

### Settlement: frozen sources and transfer reversal history

![Settlement](docs/screenshots/04-settlement.jpg)

### Reports: actual bill balances and approved settlements

![Reports](docs/screenshots/05-reports.jpg)

### Role permissions and settings

![Role permissions](docs/screenshots/06-roles.jpg)
![Settings](docs/screenshots/07-settings.jpg)

### Customer and partner workspaces

![Customer confirmation](docs/screenshots/08-customer.jpg)
![Partner workspace](docs/screenshots/09-partner.jpg)

## Architecture and persistence

Vue 3.5.43 / Vite 8.1.5 → same-origin Nginx → Spring Boot 4.0.7 / Spring JDBC / Spring Security → MySQL 8.4, using Java 21 and Flyway migrations. Backend checks enforce both permissions and organization/customer ownership; hidden menus do not replace authorization.

Writes first acquire a database coordination row lock, then validate versions and states. This protects equipment occupancy, receipt allocation and settlement source uniqueness. Monetary values are integer cents; JSON amounts and totals are decimal strings to preserve browser precision. Business dates use Asia/Shanghai, while database connections use UTC. Sessions are in memory and require sign-in after restart; business records, history, audit and configuration persist in MySQL.

```text
backend/                 Java 21 backend, state machines and tests
  src/main/resources/db/migration/  Versioned Flyway migrations
frontend/                Vue workspaces, request client and tests
  public/brand/          Official logo
scripts/                 Environment, verification, backup/restore and release checks
docs/                    Business, architecture, API, operations, security and deployment
  images/                Original Chinese contact assets
  screenshots/           Actual running pages
compose.yaml             MySQL, backend and frontend
compose.storage.yaml     Optional dedicated host data directory
```

## Requirements and startup

Docker Engine with Compose v2; at least 6 GB available memory and 10 GB free disk space are recommended. Python 3.12/3.13 runs helper scripts. Container builds do not require host Java/Node; source development uses Java 21, Maven 3.9 and Node 24.19.0.

```sh
python3 scripts/init-env.py
docker compose -p leasehub config --quiet
docker compose -p leasehub up --build -d --wait --wait-timeout 1200
```

Application: `http://127.0.0.1:8162/`; health: `http://127.0.0.1:8162/actuator/health`.

The initial username defaults to `admin`. **A random password is generated into the private `.env` file, readable only by its owner.** There is no published default password. The generator refuses to overwrite existing configuration. Existing databases are not reinitialized; changing ADMIN_PASSWORD does not reset an existing account. No fake partners, customers, assets, contracts or income are inserted automatically. Headquarters must create organizations, customers and accounts before use.

### Database initialization and upgrades

`backend/src/main/resources/db/migration/V1__leasehub.sql` runs automatically on first startup. Bootstrap creates the administrator, roles, menus, dictionaries and settings. Do not execute initialization SQL repeatedly. Add new migration versions for upgrades; do not edit applied migrations or Flyway history. Back up and rehearse restoration first. No historical enterprise data migration has been accepted for this first release.

### Configuration

See [.env.example](.env.example). MYSQL_ROOT_PASSWORD, DATABASE_PASSWORD and ADMIN_PASSWORD belong only in private configuration. WEB_PORT/BIND_ADDRESS default to 8162/127.0.0.1. COOKIE_SECURE is false for local HTTP and must be true with production HTTPS. Optional DATABASE_URL/DATABASE_USER support an external MySQL instance. Platform name, idle threshold, dictionaries, menus and rate versions are maintained in the application.

Named volumes use Docker's configured data disk. For a dedicated host disk, explicitly use `compose.storage.yaml` and an absolute LEASEHUB_MYSQL_DATA_DIR as described in [Deployment](docs/deployment.md). Switching paths is not a migration of existing data. There are no bank, e-signature or notification provider secret settings.

## Testing

```sh
mvn -B -f backend/pom.xml spotless:check clean verify
cd frontend
npm ci --no-audit --no-fund
npm run format:check
npm run lint
npm test
npm run build
```

Run helper checks from the repository root:

```sh
python3 -m pip install -r scripts/requirements-quality.txt
python3 -m black --check scripts
python3 -m unittest discover -s scripts -p 'test_*.py'
python3 scripts/release-check.py
```

The HTTP verification script is for a **new isolated TEST database** and creates test organizations, accounts, contracts and financial records. Its private state file must be outside the repository:

```sh
python3 scripts/smoke.py --url http://127.0.0.1:8162 --env-file .env --private-state /absolute/private/leasehub-test-state.json
```

The backend has 23 tests, frontend 2 tests and restore guards 6 tests. Actual HTTP, restore and UI results are recorded in [Verification](docs/verification.md). Image builds execute tests, including Maven tests. Release checks inspect Git-tracked or staged files; private configuration, generated directories and backups must not be committed.

## Deployment and operation

Authorized production deployment requires HTTPS, network isolation, dedicated accounts, trusted MySQL CA verification, backup/restore procedures and acceptance against the actual business environment. Defaults expose only a local webpage, with no published database or backend host ports. Backup requires an explicit project and private output. Restore is restricted to an independent empty `leasehub-restore-*` database and refuses to overwrite business tables.

- [Business rules](docs/business.md) · [Architecture](docs/architecture.md) · [API](docs/api.md)
- [Operations](docs/operations.md) · [Deployment and restore](docs/deployment.md) · [Security](docs/security.md)

The detailed manuals are currently Chinese; the application features, setup and limitations summarized here match the Chinese README.

## Known limitations and unimplemented integrations

- One headquarters, one application instance and low-volume use. Writes are serialized and sessions are not shared. No high-concurrency, multi-headquarters SaaS or production availability claims.
- CNY daily rent, maximum 366 days. No financing interest, tax invoices, multiple currencies, deposits, insurance, automatic penalties or early-return refunds. Return does not waive existing bills.
- No bank/payment, e-signature, financial-system, telemetry, email/SMS/WeChat integration. No location, qualification or credit guarantees.
- Lookup lists are limited to 1000 records per type; CSV exports cover all records in the current scope, at most 10000, and do not reuse page search filters. Risk lists show at most 100 entries.
- Additional records for an already settled month require cancellation/reversal and regeneration. Recorded transfers require reconciliation of actual funds before correcting the ledger.
- No acceptance for Xinchuang databases, 500 concurrent users, actual enterprise historical data, RPO, audit retention years, disk encryption or uptime commitments. Host storage performance affects startup and request latency.

## License and contact

Personal learning and non-commercial exchange only. Commercial delivery, paid deployment, SaaS operation, resale or paid services for third parties require written authorization from Shanghai Rujing Zhihua Information Technology Co., Ltd. See [LICENSE](LICENSE). Source availability does not grant free commercial use.

For commercial licensing, enterprise deployment or in-depth customization, contact **ZhiHua Technology (Shanghai Rujing Zhihua Information Technology Co., Ltd.)**.

- Website: [https://www.zhuatech.cn/](https://www.zhuatech.cn/)
- Email: [han@zhuatech.cn](mailto:han@zhuatech.cn)
- Email: [jack@zhuatech.cn](mailto:jack@zhuatech.cn)
- WhatsApp: [+86 17521234993](https://wa.me/8617521234993)
- Services: licensing, customization, private deployment, system integration and maintenance
