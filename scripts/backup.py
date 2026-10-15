#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""显式实例SQL备份，私有文件不入仓库。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
import argparse
import os
from compose_support import command, private_output, run_mysql


def main():
    """备份仅此实例。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
    p = argparse.ArgumentParser()
    p.add_argument("--env-file", required=True)
    p.add_argument("--project", required=True)
    p.add_argument("--output", required=True)
    a = p.parse_args()
    out = private_output(a.output)
    base = command(a.env_file, a.project)
    data = run_mysql(base, dump=True)
    if not data or b"CREATE TABLE" not in data:
        raise RuntimeError("Backup is empty or invalid")
    fd = os.open(out, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, "wb") as f:
        f.write(data)
    print("Private backup created; keep outside source control")


if __name__ == "__main__":
    main()
