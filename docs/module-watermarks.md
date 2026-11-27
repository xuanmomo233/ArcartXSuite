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
2. 填写 `export_account`（客户云端 QQ）和 `export_recipient`（内部客户编号，例如 `customer-0001`）。内部客户编号使用英文字母、数字、连字符或下划线。同一次导出的 Windows 和 Linux 安装包共用同一组模块水印。

   - `export_delivery_id` 可自填交付编号。留空时按 GitHub Actions 运行编号生成 `delivery-${GITHUB_RUN_ID}`，重跑同一次运行会沿用该编号。一个交付编号可对应多个席位。
   - `export_modules` 留空时导出全部正式模块，排除 Debug。只交付部分模块时，填写以逗号分隔的模块 ID，并包含所选模块的硬依赖。
3. 客户导出成功后下载 `axs-customer-delivery-attempt-N`。主云后台打开「授权管理 → 登记客户水印包」，导入交付包内的 `admin-only/register-module-variants.json`。核对显示的账号、模块和水印 ID。再次提交同一登记文件可查看备云同步状态。
4. 从 `customer/installers/` 取客户操作系统对应的安装 ZIP。客户需要代理端插件时，从 `customer/optional-proxy/` 取对应的 JAR。整份管理员交付包和登记文件由管理员保存。
5. 登记时填写整份授权允许绑定的服务器数量。客户在各后端填写自己的云端账号和 API 密钥。后端自行生成服务器码，首次在线验证通过时占用一个名额。客户可以在「我的授权」查看或解绑服务器。

首次使用需连接云端验证并取钥。验证成功后，同一安装在主备云均超时时可以使用有效的缓存授权，最长七天。云端明确拒绝授权时，模块停止使用缓存授权。

导出模式使用现有正式签名和加密 Secrets，即使仓库暂停 AXS 云端发布也可运行。`skip_upload` 用于验证构建，与 `export_only` 互斥。正式导出在构建前检查所需的 Secrets。管理员交付包上传成功后，工作流清理该次运行的构建中间产物。管理员登记文件后，客户通过安装包放置模块文件，宿主启动时向云端验权取钥。

客户导出构建使用 `customer` 版，包含跨服实现。云端发行构建使用 `free` 版，提供单服功能。两版分别打包并在发布步骤核验。

### 发行环境配置

- `AXS_CUSTOMER_EXPORT_SEED_PARTS_B64` 是客户正式导出的独立 32 字节 Base64 构建种子。客户安装包中的宿主与 Native 使用同一份导出种子，发行签名继续使用现有正式签名密钥。
- 在 GitHub Secrets 中配置 `AXS_WATERMARK_PRIVATE_PEM`，值为水印签名私钥 PEM。查验用公钥单独保管。
- 为登记元数据配置独立的 32 字节随机密钥，将标准 Base64 值作为 `AXS_VARIANT_REGISTRATION_KEY_B64`，在 GitHub Secrets、主云和备云使用同一值。已登记的旧包仍依赖这把密钥，轮换时需先安排旧包数据迁移或重新发行。
- 启用前完成[本地模块授权的配套升级](local-module-authorization.md#配套升级)。主云按迁移日志应用至 `0027_customer_package_authorizations.sql`，备云应用至 `0017_customer_package_seats.sql`。已有交付需要登记整包授权并重新在线绑定。备云接收加密登记记录，安装 ZIP 由管理员交付。

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
