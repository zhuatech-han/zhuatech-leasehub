# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""仅定位显式指定的LeaseHub实例，不读取其他项目配置。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
import os
import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def command(env_file, project, restore=False):
    """验证名称、私有配置和数据库实例。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
    if not re.fullmatch(r"leasehub(?:-[a-z0-9]+)*", project):
        raise ValueError("Project must be an explicit leasehub instance")
    if restore and not project.startswith("leasehub-restore-"):
        raise ValueError(
            "Restore is limited to an independent leasehub-restore-* instance"
        )
    env = Path(env_file).resolve()
    if not env.is_file() or env.stat().st_mode & 0o077:
        raise ValueError("Environment file must exist and be private (mode 600)")
    return [
        "docker",
        "compose",
        "--project-directory",
        str(ROOT),
        "--env-file",
        str(env),
        "-p",
        project,
    ]


def private_output(path):
    """拒绝覆盖及仓库内备份，凭证不得进入Git。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
    out = Path(path).resolve()
    if out.is_relative_to(ROOT) or out.exists():
        raise ValueError("Backup must be a new file outside the repository")
    return out


def run_mysql(base, sql=None, dump=False):
    """凭证仅在容器环境中引用，不出现在参数或输出。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
    program = (
        "mysqldump --single-transaction --no-tablespaces --set-gtid-purged=OFF --hex-blob --routines --events"
        if dump
        else "mysql --batch --skip-column-names --binary-mode=1"
    )
    script = (
        'export MYSQL_PWD="$MYSQL_PASSWORD"; exec '
        + program
        + ' -u"$MYSQL_USER" "$MYSQL_DATABASE"'
    )
    result = subprocess.run(
        base + ["exec", "-T", "mysql", "sh", "-c", script],
        input=sql,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    if result.returncode:
        raise RuntimeError(
            "Database command failed; inspect the selected instance without exposing credentials"
        )
    return result.stdout
