@echo off
:: ═══════════════════════════════════════════════════════════════════
:: ArcartXSuite 安全启动脚本 (Windows)
::
:: 此脚本在标准 Minecraft 服务端启动参数基础上添加安全封锁：
::   -XX:+DisableAttachMechanism  彻底关闭 JVM Attach API，防止内存抓取
::   -javaagent:classfinal-agent.jar  ClassFinal VMP 解密 agent
::
:: 用法：将此脚本放到服务端根目录，与 server.jar 同级
:: ═══════════════════════════════════════════════════════════════════

set JAVA_OPTS=-Xms4G -Xmx4G
set SECURITY_OPTS=-XX:+DisableAttachMechanism -XX:+UseG1GC

:: ClassFinal agent（如果存在）
set CF_AGENT=
if exist "plugins\ArcartXSuite\classfinal-agent.jar" (
    set CF_AGENT=-javaagent:plugins\ArcartXSuite\classfinal-agent.jar="-pwd AXS-CF-KEY"
)

echo [ArcartXSuite] 安全启动模式
echo   DisableAttachMechanism = ON
echo   ClassFinal Agent = %CF_AGENT%
echo.

java %JAVA_OPTS% %SECURITY_OPTS% %CF_AGENT% -jar server.jar nogui
pause
