# 模块水印与查验

`scripts/watermark-module.py` 为模块 JAR 写入收件人、许可证标识、发行标签和随机水印 ID，并使用维护者的 Ed25519 密钥签名。水印还绑定模块条目的内容摘要，修改业务载荷、替换收件人或删除签名都会导致查验失败。

授权由云端许可证决定；水印记录“这一份构件交付给谁”，不单独授予使用权。建议用 `customer-0001` 等内部收件人编号，并在私有交付记录中保存其账号对应关系。

工具需要 Python 3.11 或更新版本及 `cryptography`。以下 Windows 命令在按项目规范初始化 UTF-8 的 PowerShell 7 中执行。

## 生成一次水印密钥

```powershell
python -X utf8 scripts/watermark-module.py keygen --private-key 'D:\AXS-Private\watermark-private.pem' --public-key 'D:\AXS-Private\watermark-public.pem'
```

两个输出路径必须尚不存在。私钥保存在限制访问的发行环境中，不进入 Git、安装包或公开 CI Artifact。查验使用维护者独立保存的可信公钥，不使用待检查文件自带的公钥。

## 在现有生产流程中打水印

### 从 GitHub 正式导出并登记

1. 在 Suite 仓库的 Actions 中手动运行 `Encrypted CI Pipeline`，选择 `export_only`。使用 `main`，或选择指向已合入 `main` 的 `suite-export-*` 标签，以便以后再次打出同一版本。提交不变时发行批次号不变。
2. 填写 `export_account`（客户云端 QQ）、`export_recipient`（内部客户编号）、`export_delivery_id`（内部交付编号）及 `export_modules`（逗号分隔的模块 ID，包含客户实际需要的硬依赖）。一个交付编号可对应两份或更多席位。一个客户的不同平台安装包共用同一组模块水印。
3. 从同一次构建尝试下载 `axs-customer-installers-attempt-N` 和 `axs-customer-registration-attempt-N`。主云后台打开「授权管理 → 登记客户水印包」，导入 `register-module-variants.json`。核对显示的账号、模块和水印 ID。再次提交同一登记文件可查看备云同步状态。若显示某个模块的备云摘要冲突，核对该模块的交付记录，其他模块会继续同步。
4. 只交付客户操作系统对应的安装 ZIP。若客户需要代理端插件，可另交付 `axs-customer-proxy-attempt-N` 中对应的 JAR。登记文件留在管理员侧。
5. 在「发放授权」中按实际装备的后端数量分配席位。如果同一客户两台后端都装 Market，就共需两份 Market 授权。客户在两台后端各自生成安装身份，并在两个服务器码上分别装备。两台后端可以各安装同一个客户的同一份 `.axb`。

导出模式使用现有正式签名和加密 Secrets，即使仓库暂停 AXS 云端发布也可运行。`skip_upload` 用于验证构建，与 `export_only` 互斥。正式导出在构建前检查所需的 Secrets。Windows 和 Linux 安装包以及登记文件全部生成后，作业才会导出 GitHub Artifacts。管理员导入登记文件后，客户通过安装包放置模块文件，宿主启动时向云端验权取钥。

### 发行环境配置

- `AXS_CUSTOMER_EXPORT_SEED_PARTS_B64` 是客户正式导出的独立 32 字节 Base64 构建种子。客户安装包中的宿主与 Native 使用同一份导出种子，发行签名继续使用现有正式签名密钥。
- 在 GitHub Secrets 中配置 `AXS_WATERMARK_PRIVATE_PEM`，值为水印签名私钥 PEM。查验用公钥单独保管。
- 为登记元数据配置独立的 32 字节随机密钥，将标准 Base64 值作为 `AXS_VARIANT_REGISTRATION_KEY_B64`，在 GitHub Secrets、主云和备云使用同一值。已登记的旧包仍依赖这把密钥，轮换时需先安排旧包数据迁移或重新发行。
- 启用前完成[本地模块授权的配套升级](local-module-authorization.md#配套升级)，并按顺序应用主云的 `0023_module_variants.sql`、`0024_module_variant_replication_status.sql`，以及备云的 `0014_module_variants.sql`。备云接收加密登记记录，安装 ZIP 由管理员交付。

### 单独给已有中间 JAR 打水印

水印任务位于“逐类加密之后、外层 AXB 封装及最终发行签名之前”。以下生产任务只在具备项目生产密钥与 Native 环境的 CI 中执行，本地开发不需要运行完整生产混淆加密：

```powershell
$watermarkArgs = @(
  ':modules:market:encryptModuleAxb'
  '-Paxs.protectModules'
  '-Paxs.watermark.recipient=customer-0001'
  '-Paxs.watermark.license=实际许可证ID'
  '-Paxs.watermark.release=实际发行标签'
  '-Paxs.watermark.privateKey=D:\AXS-Private\watermark-private.pem'
)
& .\gradlew.bat @watermarkArgs
```

`recipient`、`license`、`privateKey` 必须同时提供；缺一项会拒绝构建。需要指定 Python 时增加 `-Paxs.python=<解释器绝对路径>`。未提供水印参数的构建沿用原流程。

中间水印 JAR 输出到 `build/ArcartX-Suite/modules-watermarked/`，最终 AXB 仍输出到 `build/ArcartX-Suite/module-axb/`。

已完成逐类变换的模块 JAR 也可以单独打水印：

```powershell
python -X utf8 scripts/watermark-module.py stamp --input '发行目录\market-before-axb.jar' --output '发行目录\market-customer-0001.jar' --module-id market --release '实际发行标签' --recipient customer-0001 --license-id '实际许可证ID' --private-key 'D:\AXS-Private\watermark-private.pem'
```

该输出仍需接入原 AXB 封装和发行签名流程。不要修改已签名的最终 AXB，不要在水印之后再变换 JAR 内业务类。工具默认拒绝覆盖，Gradle 仅对自身中间构建产物显式启用覆盖。

## 查验水印

检查水印 JAR：

```powershell
python -X utf8 scripts/watermark-module.py inspect --jar '发行目录\market-customer-0001.jar' --public-key 'D:\AXS-Private\watermark-public.pem'
```

维护者持有相应模块构建密钥时，可以直接检查最终 AXB；解密后的 JAR 只在内存中读取，不落盘：

```powershell
python -X utf8 scripts/watermark-module.py inspect --axb '发行目录\market.axb' --module-key-file '私有构建目录\market.key' --public-key 'D:\AXS-Private\watermark-public.pem'
```

构建密钥文件是 Base64 编码的 32 字节模块密钥，不是账号 apiKey 或安装密钥。它不能随模块一起分发。验证成功输出已验签的水印 ID、收件人、许可证、模块、发行标签和内容摘要；验证失败不输出未确认的收件人结论。

每次水印会改变最终文件摘要，登记必须使用与安装包配对的元数据。客户发行的登记记录按批次、模块、摘要和账号匹配，同一批次可以登记多个账号的不同包。常规云端发布仍按自己的已激活批次取钥。

水印可以验证来源和内容绑定，但不能保证面对主动破解者永远无法移除或绕过。保留私有交付索引，并结合云端授权和访问记录判断泄漏来源。
