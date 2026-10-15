#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""公开文件、品牌原图、许可、文档链接与敏感字段核查。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
import hashlib
import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def main():
    """严格核对待发布文件；私有环境不输出。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
    files = (
        subprocess.check_output(["git", "ls-files", "-z"], cwd=ROOT)
        .decode()
        .split("\0")
    )
    files = [f for f in files if f]
    assert files, "Stage the reviewed public source before checking"
    protected = {
        "LICENSE": "41e43385c4e6d08a272891060ab73695e5de1f63ab1d2405ba79f23284cc580f",
        "frontend/public/brand/logo.jpg": "90bf87ff294f5ee8cdaef4b10155139173d97d93a88d7ea9679b9721ba05c962",
        "docs/images/wechat-zhuatech.png": "a1205aeec110016ca889693892250a11d449489f64d27c714816b73c3fc645e1",
        "docs/images/wechat-zhuatech2.png": "98df6f15d17f94b88bc8bc115262b264fab0cfb5e6ca9443aaaf4143c5275215",
    }
    for name, sha in protected.items():
        assert name in files, name + " is not tracked"
        assert hashlib.sha256((ROOT / name).read_bytes()).hexdigest() == sha, (
            name + " changed"
        )
    zh = (ROOT / "README.md").read_text()
    en = (ROOT / "README.en.md").read_text()
    assert "[English](README.en.md)" in zh and "[中文](README.md)" in en
    assert "上海如静知华信息科技有限公司" in zh
    assert (
        "ZhiHua Technology (Shanghai Rujing Zhihua Information Technology Co., Ltd.)"
        in en
    )
    for s in [
        "https://www.zhuatech.cn/",
        "zhuatech",
        "zhuatech2",
        "商业授权或深度定制开发请联系知华科技",
    ]:
        assert s in zh, s
    for s in [
        "han@zhuatech.cn",
        "jack@zhuatech.cn",
        "https://wa.me/8617521234993",
        "non-commercial",
    ]:
        assert s in en, s
    assert zh.count("docs/images/wechat-") == 2
    assert "docs/images/wechat-" not in en
    screenshots = [
        name
        for name in files
        if name.startswith("docs/screenshots/") and name.endswith(".jpg")
    ]
    assert len(screenshots) >= 6, "At least six actual screenshot categories required"
    for s in screenshots:
        raw = (ROOT / s).read_bytes()
        assert raw.startswith(b"\xff\xd8") and raw.endswith(b"\xff\xd9"), (
            s + " is not JPEG"
        )
    env = ROOT / ".env"
    private = []
    if env.exists():
        private = [
            line.split("=", 1)[1]
            for line in env.read_text().splitlines()
            if "=" in line
            and line.split("=", 1)[0]
            in {"DATABASE_PASSWORD", "MYSQL_ROOT_PASSWORD", "ADMIN_PASSWORD"}
            and len(line.split("=", 1)[1]) >= 12
        ]
    for name in files:
        assert (
            name != ".env"
            and not name.endswith((".dump", ".sql.private"))
            and not any(
                s in name for s in ["node_modules/", "target/", "private-backups/"]
            )
        ), (name + " must not be published")
        p = ROOT / name
        if p.suffix in {".png", ".jpg"}:
            continue
        text = p.read_text()
        assert not any(s in text for s in private), "Private credential found: " + name
        assert not re.search(
            r"gh[pousr]_[A-Za-z0-9]{30,}|-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----",
            text,
        ), (
            "Secret pattern: " + name
        )
        if (
            p.suffix in {".java", ".js", ".vue", ".css", ".py", ".sql", ".yml", ".yaml"}
            or p.name == "Dockerfile"
        ):
            assert (
                "上海如静知华信息科技有限公司" in text[:500]
                and "https://www.zhuatech.cn/" in text[:500]
                and "zhuatech2" in text[:500]
            ), ("Missing source attribution: " + name)
        if p.suffix == ".md":
            for target in re.findall(r"!?\[[^\]]*\]\(([^)]+)\)", text):
                if target.startswith(("https:", "http:", "mailto:", "#")):
                    continue
                assert (p.parent / target.split("#", 1)[0]).resolve().exists(), (
                    "Broken link: " + name + " -> " + target
                )
    subprocess.run(["git", "diff", "--cached", "--check"], cwd=ROOT, check=True)
    print(
        "Release checks passed: public source, attribution, license, links, screenshots, contacts and credential scan"
    )


if __name__ == "__main__":
    main()
