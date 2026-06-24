#!/bin/bash
# ═══════════════════════════════════════════════════════════════════
# ArcartX-Suite 安全启动脚本 (Linux)
#
# 安全封锁参数：
#   -XX:+DisableAttachMechanism  彻底关闭 JVM Attach API，防止内存抓取
#   -javaagent:classfinal-agent.jar  ClassFinal VMP 解密 agent
#
# 用法：chmod +x start-secure.sh && ./start-secure.sh
# ═══════════════════════════════════════════════════════════════════

JAVA_OPTS="-Xms4G -Xmx4G"
SECURITY_OPTS="-XX:+DisableAttachMechanism -XX:+UseG1GC"

# ClassFinal agent（如果存在）
CF_AGENT=""
if [ -f "plugins/ArcartX-Suite/classfinal-agent.jar" ]; then
    CF_AGENT="-javaagent:plugins/ArcartX-Suite/classfinal-agent.jar=-pwd AXS-CF-KEY"
fi

echo "[ArcartX-Suite] 安全启动模式"
echo "  DisableAttachMechanism = ON"
echo "  ClassFinal Agent = ${CF_AGENT:-无}"
echo

java $JAVA_OPTS $SECURITY_OPTS $CF_AGENT -jar server.jar nogui
