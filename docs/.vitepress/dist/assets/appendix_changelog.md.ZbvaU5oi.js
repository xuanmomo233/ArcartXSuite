import { _ as _export_sfc, o as openBlock, c as createElementBlock, ag as createStaticVNode } from "./chunks/framework.aOZh4hG0.js";
const __pageData = JSON.parse('{"title":"变更日志","description":"","frontmatter":{},"headers":[],"relativePath":"appendix/changelog.md","filePath":"appendix/changelog.md","lastUpdated":1778398530000}');
const _sfc_main = { name: "appendix/changelog.md" };
function _sfc_render(_ctx, _cache, $props, $setup, $data, $options) {
  return openBlock(), createElementBlock("div", null, [..._cache[0] || (_cache[0] = [
    createStaticVNode('<h1 id="变更日志" tabindex="-1">变更日志 <a class="header-anchor" href="#变更日志" aria-label="Permalink to &quot;变更日志&quot;">​</a></h1><p>按版本倒序记录对<strong>外部接口契约</strong>的破坏性变更与重要新增。</p><hr><h2 id="_4-0-0-当前" tabindex="-1">4.0.0（当前） <a class="header-anchor" href="#_4-0-0-当前" aria-label="Permalink to &quot;4.0.0（当前）&quot;">​</a></h2><ul><li><strong>架构</strong> — 模块整合为 17 个主模块，EntityTracker（实体追踪）、CombatEffect（战斗特效）、Announcer（播报系统）、EventPacket（事件引擎）功能扩展</li><li><strong>新增</strong> — LoginView 登录界面模块（独立/AuthMe 兼容双模式）</li><li><strong>文档</strong> — 迁移至 VitePress，全新可视化文档站</li></ul><h2 id="_3-3-8" tabindex="-1">3.3.8 <a class="header-anchor" href="#_3-3-8" aria-label="Permalink to &quot;3.3.8&quot;">​</a></h2><ul><li><strong>架构</strong> — 资源加密协议固定为 <code>AES/GCM/NoPadding</code> + GZIP + magic <code>AXR1</code> + 12-byte IV</li><li><strong>架构</strong> — <code>ClientPacketGuard</code> 引入 <code>mode: silent / notify / punish</code></li><li><strong>Mail</strong> — <code>attachment-tax-rates.&lt;currency&gt;</code> 替代旧 <code>vault-tax-rate</code></li><li><strong>EventPacket</strong> — 引入 <code>rules.&lt;id&gt;</code> 模型（trigger + actions 链）</li><li><strong>Title</strong> — 新增 <code>craneattribute.enabled</code> 桥</li><li><strong>OnlineRewards</strong> — 管理命令固定为 <code>onlinereward</code>（单数）</li><li><strong>EntityTracker</strong> — rewards actions 支持四类；<code>inventory-full</code> 策略</li></ul><hr><h2 id="升级注意事项" tabindex="-1">升级注意事项 <a class="header-anchor" href="#升级注意事项" aria-label="Permalink to &quot;升级注意事项&quot;">​</a></h2><ol><li><strong>备份</strong> <code>plugins/ArcartXSuite/</code></li><li>启动新版本，让 <code>YamlConfigSynchronizer</code> 合并新增字段</li><li><code>/AXS status</code> 检查模块状态</li><li><code>/AXS reload all</code> 确认无报错</li></ol>', 10)
  ])]);
}
const changelog = /* @__PURE__ */ _export_sfc(_sfc_main, [["render", _sfc_render]]);
export {
  __pageData,
  changelog as default
};
