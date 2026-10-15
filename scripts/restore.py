#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""仅允许恢复至空的独立测试实例，不覆盖已有业务库。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
import argparse
from pathlib import Path
from compose_support import command, run_mysql


def main():
    """恢复前确认目标为空。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
    p = argparse.ArgumentParser()
    p.add_argument("--env-file", required=True)
    p.add_argument("--project", required=True)
    p.add_argument("--input", required=True)
    a = p.parse_args()
    base = command(a.env_file, a.project, restore=True)
    source = Path(a.input).resolve()
    if not source.is_file() or source.stat().st_mode & 0o077:
        raise ValueError("Backup must be a private local file (mode 600)")
    count = run_mysql(
        base,
        b"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE();",
    ).strip()
    if count != b"0":
        raise ValueError("Target database must be empty; restore aborted")
    data = source.read_bytes()
    if b"CREATE TABLE" not in data:
        raise ValueError("Invalid backup")
    run_mysql(base, data)
    print(
        "Restored into the empty independent instance; start backend to validate migrations"
    )


if __name__ == "__main__":
    main()
