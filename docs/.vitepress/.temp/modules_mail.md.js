import { ssrRenderAttrs, ssrRenderStyle } from "vue/server-renderer";
import { useSSRContext } from "vue";
import { _ as _export_sfc } from "./plugin-vue_export-helper.1tPrXgE0.js";
const __pageData = JSON.parse('{"title":"Mail 邮箱","description":"","frontmatter":{},"headers":[],"relativePath":"modules/mail.md","filePath":"modules/mail.md","lastUpdated":1778398530000}');
const _sfc_main = { name: "modules/mail.md" };
function _sfc_ssrRender(_ctx, _push, _parent, _attrs, $props, $setup, $data, $options) {
  _push(`<div${ssrRenderAttrs(_attrs)}><h1 id="mail-邮箱" tabindex="-1">Mail 邮箱 <a class="header-anchor" href="#mail-邮箱" aria-label="Permalink to &quot;Mail 邮箱&quot;">​</a></h1><h2 id="功能定位" tabindex="-1">功能定位 <a class="header-anchor" href="#功能定位" aria-label="Permalink to &quot;功能定位&quot;">​</a></h2><p>完整的游戏内邮箱系统：玩家写信、管理员预设派发、CDK 兑换、物品附件、货币手续费、跨服广播。</p><h2 id="依赖" tabindex="-1">依赖 <a class="header-anchor" href="#依赖" aria-label="Permalink to &quot;依赖&quot;">​</a></h2><ul><li>必需：ArcartX</li><li>可选：Vault / PlayerPoints、NeigeItems / MythicMobs items、Redis（跨服）</li></ul><h2 id="启用步骤" tabindex="-1">启用步骤 <a class="header-anchor" href="#启用步骤" aria-label="Permalink to &quot;启用步骤&quot;">​</a></h2><div class="language-yaml vp-adaptive-theme"><button title="Copy Code" class="copy"></button><span class="lang">yaml</span><pre class="shiki shiki-themes github-light github-dark vp-code" tabindex="0"><code><span class="line"><span style="${ssrRenderStyle({ "--shiki-light": "#22863A", "--shiki-dark": "#85E89D" })}">modules</span><span style="${ssrRenderStyle({ "--shiki-light": "#24292E", "--shiki-dark": "#E1E4E8" })}">:</span></span>
<span class="line"><span style="${ssrRenderStyle({ "--shiki-light": "#22863A", "--shiki-dark": "#85E89D" })}">  mail</span><span style="${ssrRenderStyle({ "--shiki-light": "#24292E", "--shiki-dark": "#E1E4E8" })}">:</span></span>
<span class="line"><span style="${ssrRenderStyle({ "--shiki-light": "#22863A", "--shiki-dark": "#85E89D" })}">    enabled</span><span style="${ssrRenderStyle({ "--shiki-light": "#24292E", "--shiki-dark": "#E1E4E8" })}">: </span><span style="${ssrRenderStyle({ "--shiki-light": "#005CC5", "--shiki-dark": "#79B8FF" })}">true</span></span>
<span class="line"><span style="${ssrRenderStyle({ "--shiki-light": "#22863A", "--shiki-dark": "#85E89D" })}">    password</span><span style="${ssrRenderStyle({ "--shiki-light": "#24292E", "--shiki-dark": "#E1E4E8" })}">: </span><span style="${ssrRenderStyle({ "--shiki-light": "#032F62", "--shiki-dark": "#9ECBFF" })}">&quot;AXS-Mail@2026#Ready&quot;</span></span></code></pre></div><h2 id="命令" tabindex="-1">命令 <a class="header-anchor" href="#命令" aria-label="Permalink to &quot;命令&quot;">​</a></h2><p>管理：</p><div class="language- vp-adaptive-theme"><button title="Copy Code" class="copy"></button><span class="lang"></span><pre class="shiki shiki-themes github-light github-dark vp-code" tabindex="0"><code><span class="line"><span>/AXS mail status</span></span>
<span class="line"><span>/AXS mail reload</span></span>
<span class="line"><span>/AXS mail open &lt;player&gt;</span></span>
<span class="line"><span>/AXS mail preset send &lt;presetId&gt; &lt;player|all-online|all-registered&gt;</span></span>
<span class="line"><span>/AXS mail cdk create &lt;presetId&gt; &lt;code|auto&gt; &lt;maxClaims&gt; &lt;ttl&gt;</span></span>
<span class="line"><span>/AXS mail cdk info &lt;code&gt;</span></span>
<span class="line"><span>/AXS mail cdk list [page]</span></span>
<span class="line"><span>/AXS mail cdk delete &lt;code&gt;</span></span></code></pre></div><p>玩家：</p><div class="language- vp-adaptive-theme"><button title="Copy Code" class="copy"></button><span class="lang"></span><pre class="shiki shiki-themes github-light github-dark vp-code" tabindex="0"><code><span class="line"><span>/mail open</span></span>
<span class="line"><span>/mail compose</span></span>
<span class="line"><span>/mail claimall</span></span>
<span class="line"><span>/mail deleteall</span></span>
<span class="line"><span>/mail cdk &lt;code&gt;</span></span></code></pre></div><h2 id="papi" tabindex="-1">PAPI <a class="header-anchor" href="#papi" aria-label="Permalink to &quot;PAPI&quot;">​</a></h2><p>前缀：<code>%AXSmail_*%</code></p><div class="language- vp-adaptive-theme"><button title="Copy Code" class="copy"></button><span class="lang"></span><pre class="shiki shiki-themes github-light github-dark vp-code" tabindex="0"><code><span class="line"><span>%AXSmail_unread_count%</span></span>
<span class="line"><span>%AXSmail_claimable_count%</span></span>
<span class="line"><span>%AXSmail_total_count%</span></span></code></pre></div></div>`);
}
const _sfc_setup = _sfc_main.setup;
_sfc_main.setup = (props, ctx) => {
  const ssrContext = useSSRContext();
  (ssrContext.modules || (ssrContext.modules = /* @__PURE__ */ new Set())).add("modules/mail.md");
  return _sfc_setup ? _sfc_setup(props, ctx) : void 0;
};
const mail = /* @__PURE__ */ _export_sfc(_sfc_main, [["ssrRender", _sfc_ssrRender]]);
export {
  __pageData,
  mail as default
};
