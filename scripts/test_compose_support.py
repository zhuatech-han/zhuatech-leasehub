# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""恢复隔离和文件安全边界测试。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
import os
import tempfile
import unittest
from pathlib import Path
from compose_support import ROOT, command, private_output


class Guards(unittest.TestCase):
    """防止错项目恢复或公开备份。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""

    def test_foreign_project_rejected(self):
        with self.assertRaises(ValueError):
            command("/tmp/missing", "ticketdesk")

    def test_restore_production_rejected(self):
        with self.assertRaises(ValueError):
            command("/tmp/missing", "leasehub-production", restore=True)

    def test_repo_backup_rejected(self):
        with self.assertRaises(ValueError):
            private_output(ROOT / "backup.sql")

    def test_existing_file_rejected(self):
        with tempfile.NamedTemporaryFile() as f:
            with self.assertRaises(ValueError):
                private_output(f.name)

    def test_public_env_rejected(self):
        with tempfile.NamedTemporaryFile() as f:
            os.chmod(f.name, 0o644)
            with self.assertRaises(ValueError):
                command(f.name, "leasehub-qa")

    def test_valid_private_restore(self):
        with tempfile.NamedTemporaryFile() as f:
            os.chmod(f.name, 0o600)
            self.assertIn(
                "leasehub-restore-qa",
                command(f.name, "leasehub-restore-qa", restore=True),
            )


if __name__ == "__main__":
    unittest.main()
