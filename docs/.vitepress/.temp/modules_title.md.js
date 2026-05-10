import { ssrRenderAttrs, ssrRenderStyle } from "vue/server-renderer";
import { useSSRContext } from "vue";
import { _ as _export_sfc } from "./plugin-vue_export-helper.1tPrXgE0.js";
const __pageData = JSON.parse('{"title":"Title 称号","description":"","frontmatter":{},"headers":[],"relativePath":"modules/title.md","filePath":"modules/title.md","lastUpdated":1778398530000}');
const _sfc_main = { name: "modules/title.md" };
function _sfc_ssrRender(_ctx, _push, _parent, _attrs, $props, $setup, $data, $options) {
  _push(`<div${ssrRenderAttrs(_attrs)}><h1 id="title-称号" tabindex="-1">Title 称号 <a class="header-anchor" href="#title-称号" aria-label="Permalink to &quot;Title 称号&quot;">​</a></h1><h2 id="功能定位" tabindex="-1">功能定位 <a class="header-anchor" href="#功能定位" aria-label="Permalink to &quot;功能定位&quot;">​</a></h2><p>分组称号系统：有效期/永久、属性加成、聊天/TAB 前缀、ArcartX UI 菜单、PAPI 全量输出。</p><h2 id="依赖" tabindex="-1">依赖 <a class="header-anchor" href="#依赖" aria-label="Permalink to &quot;依赖&quot;">​</a></h2><ul><li>必需：ArcartX</li><li>可选：PlaceholderAPI、AttributePlus / CraneAttribute / MythicLib</li></ul><h2 id="启用步骤" tabindex="-1">启用步骤 <a class="header-anchor" href="#启用步骤" aria-label="Permalink to &quot;启用步骤&quot;">​</a></h2><div class="language-yaml vp-adaptive-theme"><button title="Copy Code" class="copy"></button><span class="lang">yaml</span><pre class="shiki shiki-themes github-light github-dark vp-code" tabindex="0"><code><span class="line"><span style="${ssrRenderStyle({ "--shiki-light": "#22863A", "--shiki-dark": "#85E89D" })}">modules</span><span style="${ssrRenderStyle({ "--shiki-light": "#24292E", "--shiki-dark": "#E1E4E8" })}">:</span></span>
<span class="line"><span style="${ssrRenderStyle({ "--shiki-light": "#22863A", "--shiki-dark": "#85E89D" })}">  title</span><span style="${ssrRenderStyle({ "--shiki-light": "#24292E", "--shiki-dark": "#E1E4E8" })}">:</span></span>
<span class="line"><span style="${ssrRenderStyle({ "--shiki-light": "#22863A", "--shiki-dark": "#85E89D" })}">    enabled</span><span style="${ssrRenderStyle({ "--shiki-light": "#24292E", "--shiki-dark": "#E1E4E8" })}">: </span><span style="${ssrRenderStyle({ "--shiki-light": "#005CC5", "--shiki-dark": "#79B8FF" })}">true</span></span>
<span class="line"><span style="${ssrRenderStyle({ "--shiki-light": "#22863A", "--shiki-dark": "#85E89D" })}">    password</span><span style="${ssrRenderStyle({ "--shiki-light": "#24292E", "--shiki-dark": "#E1E4E8" })}">: </span><span style="${ssrRenderStyle({ "--shiki-light": "#032F62", "--shiki-dark": "#9ECBFF" })}">&quot;AXS-Title@2026#Ready&quot;</span></span></code></pre></div><h2 id="命令" tabindex="-1">命令 <a class="header-anchor" href="#命令" aria-label="Permalink to &quot;命令&quot;">​</a></h2><p>管理：</p><div class="language- vp-adaptive-theme"><button title="Copy Code" class="copy"></button><span class="lang"></span><pre class="shiki shiki-themes github-light github-dark vp-code" tabindex="0"><code><span class="line"><span>/AXS title status</span></span>
<span class="line"><span>/AXS title reload</span></span>
<span class="line"><span>/AXS title give &lt;player&gt; &lt;titleId&gt; &lt;duration&gt;</span></span>
<span class="line"><span>/AXS title revoke &lt;player&gt; &lt;titleId&gt;</span></span>
<span class="line"><span>/AXS title open &lt;player&gt;</span></span></code></pre></div><p>玩家：</p><div class="language- vp-adaptive-theme"><button title="Copy Code" class="copy"></button><span class="lang"></span><pre class="shiki shiki-themes github-light github-dark vp-code" tabindex="0"><code><span class="line"><span>/title open</span></span>
<span class="line"><span>/title equip &lt;id&gt;</span></span>
<span class="line"><span>/title unequip &lt;group|all&gt;</span></span>
<span class="line"><span>/title hide &lt;id&gt;</span></span>
<span class="line"><span>/title unhide &lt;id&gt;</span></span></code></pre></div><h2 id="papi" tabindex="-1">PAPI <a class="header-anchor" href="#papi" aria-label="Permalink to &quot;PAPI&quot;">​</a></h2><p>前缀：<code>%AXStitle_*%</code></p><div class="language- vp-adaptive-theme"><button title="Copy Code" class="copy"></button><span class="lang"></span><pre class="shiki shiki-themes github-light github-dark vp-code" tabindex="0"><code><span class="line"><span>%AXStitle_chat_&lt;groupId&gt;_prefix%</span></span>
<span class="line"><span>%AXStitle_tab_&lt;groupId&gt;_prefix%</span></span>
<span class="line"><span>%AXStitle_equipped_&lt;groupId&gt;_id%</span></span>
<span class="line"><span>%AXStitle_owned_&lt;titleId&gt;%</span></span>
<span class="line"><span>%AXStitle_remaining_&lt;titleId&gt;%</span></span></code></pre></div></div>`);
}
const _sfc_setup = _sfc_main.setup;
_sfc_main.setup = (props, ctx) => {
  const ssrContext = useSSRContext();
  (ssrContext.modules || (ssrContext.modules = /* @__PURE__ */ new Set())).add("modules/title.md");
  return _sfc_setup ? _sfc_setup(props, ctx) : void 0;
};
const title = /* @__PURE__ */ _export_sfc(_sfc_main, [["ssrRender", _sfc_ssrRender]]);
export {
  __pageData,
  title as default
};
