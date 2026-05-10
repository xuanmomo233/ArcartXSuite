import { ssrRenderAttrs, ssrRenderStyle } from "vue/server-renderer";
import { useSSRContext } from "vue";
import { _ as _export_sfc } from "./plugin-vue_export-helper.1tPrXgE0.js";
const __pageData = JSON.parse('{"title":"OnlineRewards 在线奖励","description":"","frontmatter":{},"headers":[],"relativePath":"modules/onlinerewards.md","filePath":"modules/onlinerewards.md","lastUpdated":1778398530000}');
const _sfc_main = { name: "modules/onlinerewards.md" };
function _sfc_ssrRender(_ctx, _push, _parent, _attrs, $props, $setup, $data, $options) {
  _push(`<div${ssrRenderAttrs(_attrs)}><h1 id="onlinerewards-在线奖励" tabindex="-1">OnlineRewards 在线奖励 <a class="header-anchor" href="#onlinerewards-在线奖励" aria-label="Permalink to &quot;OnlineRewards 在线奖励&quot;">​</a></h1><h2 id="功能定位" tabindex="-1">功能定位 <a class="header-anchor" href="#功能定位" aria-label="Permalink to &quot;功能定位&quot;">​</a></h2><p>在线时长奖励、每日签到、补签卡、四维排行榜（日/周/月/总）。</p><h2 id="依赖" tabindex="-1">依赖 <a class="header-anchor" href="#依赖" aria-label="Permalink to &quot;依赖&quot;">​</a></h2><ul><li>必需：ArcartX</li><li>可选：PlaceholderAPI、Vault</li></ul><h2 id="启用步骤" tabindex="-1">启用步骤 <a class="header-anchor" href="#启用步骤" aria-label="Permalink to &quot;启用步骤&quot;">​</a></h2><div class="language-yaml vp-adaptive-theme"><button title="Copy Code" class="copy"></button><span class="lang">yaml</span><pre class="shiki shiki-themes github-light github-dark vp-code" tabindex="0"><code><span class="line"><span style="${ssrRenderStyle({ "--shiki-light": "#22863A", "--shiki-dark": "#85E89D" })}">modules</span><span style="${ssrRenderStyle({ "--shiki-light": "#24292E", "--shiki-dark": "#E1E4E8" })}">:</span></span>
<span class="line"><span style="${ssrRenderStyle({ "--shiki-light": "#22863A", "--shiki-dark": "#85E89D" })}">  onlinereward</span><span style="${ssrRenderStyle({ "--shiki-light": "#24292E", "--shiki-dark": "#E1E4E8" })}">:</span></span>
<span class="line"><span style="${ssrRenderStyle({ "--shiki-light": "#22863A", "--shiki-dark": "#85E89D" })}">    enabled</span><span style="${ssrRenderStyle({ "--shiki-light": "#24292E", "--shiki-dark": "#E1E4E8" })}">: </span><span style="${ssrRenderStyle({ "--shiki-light": "#005CC5", "--shiki-dark": "#79B8FF" })}">true</span></span>
<span class="line"><span style="${ssrRenderStyle({ "--shiki-light": "#22863A", "--shiki-dark": "#85E89D" })}">    password</span><span style="${ssrRenderStyle({ "--shiki-light": "#24292E", "--shiki-dark": "#E1E4E8" })}">: </span><span style="${ssrRenderStyle({ "--shiki-light": "#032F62", "--shiki-dark": "#9ECBFF" })}">&quot;AXS-OnlineRewards@2026#Ready&quot;</span></span></code></pre></div><div class="warning custom-block"><p class="custom-block-title">管理命令是 onlinereward（单数）</p><p>源码 <code>MODULE_IDS</code> 写的是 <code>onlinereward</code>；玩家命令则是 <code>/onlinerewards</code>（复数）。</p></div><h2 id="命令" tabindex="-1">命令 <a class="header-anchor" href="#命令" aria-label="Permalink to &quot;命令&quot;">​</a></h2><p>管理：</p><div class="language- vp-adaptive-theme"><button title="Copy Code" class="copy"></button><span class="lang"></span><pre class="shiki shiki-themes github-light github-dark vp-code" tabindex="0"><code><span class="line"><span>/AXS onlinereward status</span></span>
<span class="line"><span>/AXS onlinereward reload</span></span>
<span class="line"><span>/AXS onlinereward add|remove|set &lt;time&gt; &lt;player&gt;</span></span>
<span class="line"><span>/AXS onlinereward card &lt;add|remove|set&gt; &lt;amount&gt; &lt;player&gt;</span></span></code></pre></div><p>玩家：</p><div class="language- vp-adaptive-theme"><button title="Copy Code" class="copy"></button><span class="lang"></span><pre class="shiki shiki-themes github-light github-dark vp-code" tabindex="0"><code><span class="line"><span>/onlinerewards open</span></span>
<span class="line"><span>/onlinerewards status</span></span>
<span class="line"><span>/onlinerewards signin</span></span>
<span class="line"><span>/onlinerewards top &lt;daily|weekly|monthly|total&gt; [page]</span></span></code></pre></div><h2 id="papi" tabindex="-1">PAPI <a class="header-anchor" href="#papi" aria-label="Permalink to &quot;PAPI&quot;">​</a></h2><p>前缀：<code>%AXSonlinerewards_*%</code></p><div class="language- vp-adaptive-theme"><button title="Copy Code" class="copy"></button><span class="lang"></span><pre class="shiki shiki-themes github-light github-dark vp-code" tabindex="0"><code><span class="line"><span>%AXSonlinerewards_daily_minutes%</span></span>
<span class="line"><span>%AXSonlinerewards_signin_streak%</span></span>
<span class="line"><span>%AXSonlinerewards_top_daily_1_name%</span></span></code></pre></div></div>`);
}
const _sfc_setup = _sfc_main.setup;
_sfc_main.setup = (props, ctx) => {
  const ssrContext = useSSRContext();
  (ssrContext.modules || (ssrContext.modules = /* @__PURE__ */ new Set())).add("modules/onlinerewards.md");
  return _sfc_setup ? _sfc_setup(props, ctx) : void 0;
};
const onlinerewards = /* @__PURE__ */ _export_sfc(_sfc_main, [["ssrRender", _sfc_ssrRender]]);
export {
  __pageData,
  onlinerewards as default
};
