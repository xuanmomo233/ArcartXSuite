# LoginView 登录入口

LoginView 展示当前连接的验证状态和准入要求。认证入口决定信任哪种身份凭据。账号通过验证后，仍可按服务器策略接受条款、完成绑定。

| 场景 | Minecraft 设置 | `authentication.yml` | 玩家如何验证 |
|---|---|---|---|
| 本机离线直连 | `online-mode=false`，`server-ip=127.0.0.1` 或 `::1`，关闭代理转发 | `backend: standalone`，`ingress.type: password` 或 `auto` | 在 LoginView 输入本服密码 |
| 微软在线直连 | `online-mode=true`，关闭代理转发，使用原生微软会话服务 | `backend: standalone`，`ingress.type: online` 或 `auto` | 通过 Minecraft 上线握手，免输本服密码 |
| 多来源混合登录 | `online-mode=true`，使用本机混合网关及对应的 authlib-injector | `backend: standalone`，`ingress.type: local-mixed` | 通过实际会话来源验证，免输本服密码 |
| 中心网页或启动器授权 | `online-mode=true`，关闭代理转发 | `backend: center`，`ingress.type: external`，见 `auth-center/config/node.example.yml` | 通过中心网页或启动器授权并确认当前连接，免输本服密码 |
| 可信代理后端 | 使用部署方的安全转发配置 | `backend: standalone`，`ingress.type: signed-proxy` | 校验可信代理对本次连接的签名，无游戏内密码 |

默认混合网关核验微软与 LittleSkin。要加入 Suite AuthCenter，在 `external` 下启用 `minecraftSessions: true`，在网关进程中用 `AXS_MIXED_CENTER_ORIGIN` 指向其 HTTPS 根地址。启动器完成中心授权后调用 `LauncherAuthorizationClient.minecraftSession(launchToken)`，以返回的档案、凭据和 API 地址启动游戏。该握手直接由中心验证。进服后再确认网页授权的模式仍使用 `external` 入口。

其他独立认证服务可通过 `AXS_MIXED_CENTER_HAS_JOINED` 配置完整 HTTPS `hasJoined` 地址。两个中心入口变量只能设置一个。

各来源的档案分别记录。不同来源提供同名账号时，不会自动合并，也不会仅凭在线模式、用户名或离线 UUID 推断第三方身份。
