#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""全新数据库真实 HTTP 业务与隔离验收；仅创建 TEST 数据。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
import argparse
import datetime as dt
import http.cookiejar
import json
import os
import secrets
import urllib.request
import urllib.error
from pathlib import Path
from zoneinfo import ZoneInfo

checks = 0


def check(ok, message):
    """记录真实断言，不输出密码。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
    global checks
    assert ok, message
    checks += 1


def normalize_money(value):
    """将服务端整数分字符串还原为精确Python整数。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
    if isinstance(value, dict):
        return {
            key: (
                int(item)
                if isinstance(item, str)
                and (
                    key.endswith("_cents") or key in {"billed", "collected", "overdue"}
                )
                else normalize_money(item)
            )
            for key, item in value.items()
        }
    if isinstance(value, list):
        return [normalize_money(item) for item in value]
    return value


class Client:
    """独立会话与 CSRF 请求。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""

    def __init__(self, base):
        self.base = base.rstrip("/")
        self.opener = urllib.request.build_opener(
            urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar())
        )

    def call(self, path, method="GET", body=None, expected=200):
        headers = {}
        if method != "GET":
            csrf = self.call("/api/auth/csrf")
            headers[csrf["header"]] = csrf["token"]
            headers["Content-Type"] = "application/json"
        req = urllib.request.Request(
            self.base + path,
            data=None if body is None else json.dumps(body).encode(),
            headers=headers,
            method=method,
        )
        try:
            with self.opener.open(req, timeout=40) as res:
                status, data = res.status, res.read().decode()
        except urllib.error.HTTPError as e:
            status, data = e.code, e.read().decode()
        check(
            status == expected,
            f"{method} {path}: expected {expected}, got {status}: {data[:180]}",
        )
        try:
            return normalize_money(json.loads(data))
        except json.JSONDecodeError:
            return data

    def login(self, user, password):
        return self.call(
            "/api/auth/login", "POST", {"username": user, "password": password}
        )

    def create(self, resource, body, expected=200):
        result = self.call("/api/data/" + resource, "POST", body, expected)
        return result.get("id")

    def detail(self, resource, ident):
        return self.call(f"/api/data/{resource}/{ident}")

    def action(self, resource, ident, action, more=None, expected=200):
        body = {
            "version": self.detail(resource, ident)["record"]["version"],
            "note": "TEST验收记录",
        }
        body.update(more or {})
        return self.call(
            f"/api/data/{resource}/{ident}/{action}", "POST", body, expected
        )


def main():
    """执行投放、合同、双方交接、资金核销与结算链路。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
    parser = argparse.ArgumentParser()
    parser.add_argument("--url", default="http://127.0.0.1:8162")
    parser.add_argument("--env-file", required=True)
    parser.add_argument("--private-state", required=True)
    args = parser.parse_args()
    state_path = Path(args.private_state).resolve()
    check(
        not state_path.is_relative_to(Path(__file__).resolve().parents[1]),
        "Private test state must be outside the repository",
    )
    check(
        not state_path.exists(),
        "Private state already exists; use a fresh test database",
    )
    env = dict(
        line.split("=", 1)
        for line in Path(args.env_file).read_text().splitlines()
        if "=" in line and not line.startswith("#")
    )
    today = dt.datetime.now(ZoneInfo("Asia/Shanghai")).date()
    month = today.replace(day=1).isoformat()
    pw = "Aa9" + secrets.token_hex(20)
    admin, partner, other, customer, customer2, finance = [
        Client(args.url) for _ in range(6)
    ]
    check(admin.call("/actuator/health")["status"] == "UP", "Health")
    admin.call("/api/data/assets", expected=401)
    me = admin.login(env.get("ADMIN_USERNAME", "admin"), env["ADMIN_PASSWORD"])
    check("password_hash" not in json.dumps(me), "Password hash leaked")
    check(admin.call("/api/data/assets")["total"] == 0, "Requires fresh database")
    org = admin.create(
        "organizations", {"name": "TEST 华东设备合作商", "code": "TEST_EAST"}
    )
    org2 = admin.create(
        "organizations", {"name": "TEST 华南设备合作商", "code": "TEST_SOUTH"}
    )
    cid = admin.create(
        "customers",
        {"org_id": org, "name": "TEST 苏州工程客户", "contact": "TEST 联系人"},
    )
    cid2 = admin.create(
        "customers",
        {"org_id": org, "name": "TEST 湖州施工客户", "contact": "TEST 联系人"},
    )
    cid3 = admin.create(
        "customers",
        {"org_id": org2, "name": "TEST 佛山工程客户", "contact": "TEST 联系人"},
    )

    def user(name, role, org_id=None, customer_id=None):
        body = {
            "username": name,
            "display_name": {
                "test_partner": "TEST 华东经理",
                "test_other": "TEST 华南经理",
                "test_customer": "TEST 苏州客户",
                "test_customer2": "TEST 湖州客户",
                "test_finance": "TEST 总部财务",
            }[name],
            "role_id": role,
            "password": pw,
        }
        if org_id:
            body["org_id"] = org_id
        if customer_id:
            body["customer_id"] = customer_id
        return admin.create("accounts", body)

    pid = user("test_partner", 4, org)
    user("test_other", 4, org2)
    user("test_customer", 5, org, cid)
    user("test_customer2", 5, org, cid2)
    user("test_finance", 3)
    for cli, name in [
        (partner, "test_partner"),
        (other, "test_other"),
        (customer, "test_customer"),
        (customer2, "test_customer2"),
        (finance, "test_finance"),
    ]:
        cli.login(name, pw)
    assets = []
    for serial, name, cat in [
        ("TEST-EX-001", "TEST 履带挖掘机", "engineering"),
        ("TEST-PL-002", "TEST 高空作业平台", "engineering"),
        ("TEST-GEN-003", "TEST 柴油发电机", "energy"),
        ("TEST-AIR-004", "TEST 工业空压机", "industrial"),
    ]:
        ident = admin.create(
            "assets",
            {
                "serial": serial,
                "name": name,
                "category": cat,
                "purchase_cents": 42000000,
            },
        )
        assets.append(ident)
        if len(assets) < 4:
            admin.action("assets", ident, "allocate", {"org_id": org})
            admin.action("assets", ident, "accept", expected=400)
            partner.action("assets", ident, "accept")
    asset = assets[0]
    other.call(f"/api/data/assets/{asset}", expected=404)
    check(other.call("/api/data/assets")["total"] == 0, "Partner asset isolation")
    check(other.call("/api/data/customers")["total"] == 1, "Partner customer isolation")
    check(
        "TEST 苏州工程客户"
        not in json.dumps(other.call("/api/lookup"), ensure_ascii=False),
        "Lookup isolation",
    )
    other.call("/api/data/accounts", expected=403)
    partner.create(
        "roles",
        {"name": "TEST越权", "scope": "HQ", "permissions": "admin"},
        expected=403,
    )
    rate = admin.create(
        "rates",
        {
            "org_id": org,
            "name": "TEST 华东日租标准 v1",
            "daily_cents": 120000,
            "hq_bps": 4500,
            "effective_on": (today - dt.timedelta(days=180)).isoformat(),
        },
    )
    start = (today - dt.timedelta(days=75)).isoformat()
    end = (today + dt.timedelta(days=15)).isoformat()
    contract = partner.create(
        "contracts",
        {
            "customer_id": cid,
            "asset_id": asset,
            "rate_id": rate,
            "name": "TEST 挖掘机工程租赁",
            "start_on": start,
            "end_on": end,
        },
    )
    duplicate = partner.create(
        "contracts",
        {
            "customer_id": cid2,
            "asset_id": asset,
            "rate_id": rate,
            "name": "TEST 占用冲突验证",
            "start_on": start,
            "end_on": end,
        },
    )
    partner.action("contracts", contract, "submit")
    partner.action("contracts", duplicate, "submit", expected=400)
    partner.action("contracts", duplicate, "cancel")
    customer2.call(f"/api/data/contracts/{contract}", expected=404)
    admin.action("contracts", contract, "confirm", expected=400)
    preview = customer.detail("contracts", contract)
    check(preview["record"]["total_cents"] == 10800000, "Frozen contract total")
    check(len(preview["items"]) == 3, "Calendar period preview")
    customer.action("contracts", contract, "confirm")
    partner.action(
        "contracts", contract, "return", {"return_on": today.isoformat()}, expected=400
    )
    partner.action("contracts", contract, "deliver", {"delivered_on": start})
    customer.action("contracts", contract, "accept-delivery")
    check(
        partner.detail("assets", asset)["record"]["state"] == "RENTED",
        "Actual delivery state",
    )
    partner.action("assets", asset, "maintain", expected=400)
    admin.action("assets", asset, "recall", expected=400)
    details = partner.detail("contracts", contract)
    bill = details["items"][0]["id"]
    receipt = partner.create(
        "receipts",
        {
            "bill_id": bill,
            "amount_cents": 1800000,
            "received_on": today.isoformat(),
            "reference": "TEST-R-001",
            "note": "TEST 已收到线下租金",
        },
    )
    receipt2 = partner.create(
        "receipts",
        {
            "bill_id": bill,
            "amount_cents": 2400000,
            "received_on": today.isoformat(),
            "reference": "TEST-R-002",
            "note": "TEST 部分收款审核",
        },
    )
    customer.call(f"/api/data/receipts/{receipt}")
    customer2.call(f"/api/data/receipts/{receipt}", expected=404)
    finance.action("receipts", receipt, "approve")
    finance.action("receipts", receipt2, "approve", expected=400)
    finance.action("receipts", receipt2, "reject")
    check(
        partner.detail("bills", bill)["record"]["paid_cents"] == 1800000,
        "Partial allocation",
    )
    cost = partner.create(
        "costs",
        {
            "asset_id": asset,
            "category": "maintenance",
            "amount_cents": 240000,
            "incurred_on": today.isoformat(),
            "reference": "TEST-C-001",
            "note": "TEST 总部承担维保费用",
        },
    )
    finance.action("costs", cost, "approve")
    settlement = partner.create("settlements", {"month_on": month})
    record = partner.detail("settlements", settlement)["record"]
    check(
        record["hq_gross_cents"] == 810000 and record["hq_net_cents"] == 570000,
        "Frozen settlement",
    )
    partner.create("settlements", {"month_on": month}, expected=400)
    finance.action("receipts", receipt, "reverse", expected=400)
    finance.action("settlements", settlement, "approve")
    finance.action(
        "settlements",
        settlement,
        "pay",
        {"transferred_on": today.isoformat(), "reference": "TEST-P-001"},
    )
    finance.action("settlements", settlement, "reverse", expected=400)
    finance.action(
        "settlements",
        settlement,
        "unpay",
        {"transferred_on": today.isoformat(), "reference": "TEST-P-REV-001"},
    )
    finance.action("settlements", settlement, "reverse")
    # Regenerate after reversal; same sources must appear exactly once.
    settlement2 = partner.create("settlements", {"month_on": month})
    finance.action("settlements", settlement2, "approve")
    check(
        len(partner.detail("settlements", settlement2)["items"]) == 2,
        "Settlement source uniqueness",
    )
    # Record another pending contract for customer UI acceptance.
    pending = partner.create(
        "contracts",
        {
            "customer_id": cid,
            "asset_id": assets[1],
            "rate_id": rate,
            "name": "TEST 高空平台租赁待确认",
            "start_on": today.isoformat(),
            "end_on": (today + dt.timedelta(days=30)).isoformat(),
        },
    )
    partner.action("contracts", pending, "submit")
    partner.action("assets", assets[2], "maintain")
    case = partner.create(
        "risks",
        {
            "title": "TEST 逾期租金跟进",
            "asset_id": asset,
            "owner_id": pid,
            "next_on": today.isoformat(),
        },
    )
    partner.action("risks", case, "follow", {"next_on": today.isoformat()})
    partner.action("risks", case, "close")
    partner.action("risks", case, "reopen")
    other.call(f"/api/data/risks/{case}", expected=404)
    report = admin.call("/api/reports")
    check(report["totals"]["collected"] == 1800000, "Statistics match allocations")
    check(report["totals"]["overdue"] > 0, "Overdue from actual dates")
    check(
        other.call("/api/reports")["totals"]["collected"] == 0, "Statistics isolation"
    )
    check("TEST-EX-001" not in other.call("/api/export/assets"), "CSV isolation")
    admin.call("/api/export/accounts", expected=400)
    admin.call("/api/data/assets?page=0", expected=400)
    admin.call("/api/data/assets?size=101", expected=400)
    admin.call("/api/data/assets?search=%25")
    profile = admin.call("/api/auth/me")
    check(len(profile["menus"]) >= 10, "Menus initialized")
    check(len(admin.call("/api/data/audit")["items"]) > 0, "Audit persisted")
    # Keep only private TEST credentials for browser acceptance; never publish this file.
    state = {
        "url": args.url,
        "test_password": pw,
        "admin_username": env.get("ADMIN_USERNAME", "admin"),
        "admin_password": env["ADMIN_PASSWORD"],
        "org": org,
        "org2": org2,
        "asset": asset,
        "assets": assets,
        "contract": contract,
        "pending_contract": pending,
        "settlement": settlement2,
        "receipt": receipt,
        "cost": cost,
        "checks": checks,
    }
    fd = os.open(state_path, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
    with os.fdopen(fd, "w") as f:
        json.dump(state, f)
    print(
        json.dumps(
            {"result": "PASS", "checks": checks, "private_state": str(state_path)},
            ensure_ascii=False,
        )
    )


if __name__ == "__main__":
    main()
