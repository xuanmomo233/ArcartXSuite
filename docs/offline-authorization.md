# Suite 离线授权核验

生产加密 JAR 包含可直接读取的英文授权声明。JAR 开头的 `META-INF/MANIFEST.MF` 通过 `AXS-Security-Notice` 指向 `META-INF/AXS-SECURITY-NOTICE.txt`。随后是声明及其独立 Ed25519 签名 `META-INF/AXS-SECURITY-NOTICE.sig`。签名覆盖带有 `ArcartX-Suite/authorization-notice/v1` 域标识的声明摘要。宿主 `BOOTSTRAP.MF` v2 也记录了这两个文件的哈希。

声明保留可读正文，供文件检查工具在接触受保护载荷前展示。签名用于核对发布者与内容。维护者为每个被授权人签发单独的 JSON，绑定最终 JAR 的 SHA-256。签发后可分别交付 JAR 和凭证。即使重打包后原始文件内容仍通过发行签名校验，旧凭证也只匹配原 JAR 的完整字节。

以下命令使用仓库内的 `scripts/suite_authorization.py`，需要 Python 3.11 或更新版本和预先安装的 `cryptography`。核验全程使用本地文件。示例中的 `suite.jar` 是已经完成生产签名的宿主包。

向核验方单独交付工具时，将 `suite_authorization.py` 和 `host_seal.py` 放在同一目录。

- `release-public.pem` 是已从可信渠道校对的 Suite 发行公钥。
- `maintainer-public.pem` 是发布方指定、由核验方独立保存的授权签发公钥。对应私钥由维护者持有。
- `reviewer-public.pem` 是维护者核对身份后登记的被授权人公钥。对应私钥由被授权人持有。

公私钥使用 Ed25519 PEM 格式。工具使用来自核验方信任配置的公钥。授权凭证、JAR 和请求人提供的材料均作为待验证输入。

核验方启动预检并展示已验签的声明。

```text
python -X utf8 scripts/suite_authorization.py notice --jar suite.jar --release-public-key release-public.pem
```

维护者确认被授权人及其公钥后签发凭证。请将示例 UTC 时间替换为实际截止时间。每项操作用单独的 `--allow` 参数。

```text
python -X utf8 scripts/suite_authorization.py issue --jar suite.jar --release-public-key release-public.pem --maintainer-private-key maintainer-private.pem --subject reviewer:example --subject-public-key reviewer-public.pem --allow static-review --expires-at 2026-12-01T12:00:00Z --output suite-authorization.json
```

每次请求由核验方创建一份新挑战，并保留核验方自己的副本。被授权人拿到挑战和凭证后，用自己的私钥生成应答。

```text
python -X utf8 scripts/suite_authorization.py challenge --output challenge.txt
python -X utf8 scripts/suite_authorization.py prove --grant suite-authorization.json --challenge challenge.txt --action static-review --subject-private-key reviewer-private.pem --output proof.txt
```

核验方使用本次预期的请求人、操作和挑战验证应答。

```text
python -X utf8 scripts/suite_authorization.py verify --jar suite.jar --release-public-key release-public.pem --maintainer-public-key maintainer-public.pem --subject reviewer:example --action static-review --grant suite-authorization.json --challenge challenge.txt --proof proof.txt --json
```

核验成功时退出代码为 `0`，JSON 为 `status: confirmed`，含已核验的英文声明、请求人、操作、有效期和 JAR 的 SHA-256。校验失败时退出代码为 `1`，JSON 为 `status: unconfirmed`，消息为「授权未确认」。只有已经验签的声明会出现在工具输出中。签发、挑战和身份证明命令会拒绝覆盖已有输出文件。

受控 AI 工具可在启用受保护操作前运行上述两步核验。核验方从实际任务确定 `--subject` 和 `--action`，并针对每次请求生成新的挑战。身份证明同时绑定维护者签名的凭证、操作和本次挑战。过期判断使用核验机的 UTC 时钟。离线核验只掌握本地信任状态，授权有效期也限定了它能持续被核验的时间。

发行方声明公开可见。外部 AI 是否展示它取决于对方的工具流程。授权校验结果描述 Suite 维护者签发的权限，模型平台仍适用各自的安全规则。
