import { ssrRenderAttrs, ssrRenderStyle } from "vue/server-renderer";
import { useSSRContext } from "vue";
import { _ as _export_sfc } from "./plugin-vue_export-helper.1tPrXgE0.js";
const __pageData = JSON.parse('{"title":"Warehouse 仓库银行","description":"","frontmatter":{},"headers":[],"relativePath":"modules/warehouse.md","filePath":"modules/warehouse.md","lastUpdated":1778398530000}');
const _sfc_main = { name: "modules/warehouse.md" };
function _sfc_ssrRender(_ctx, _push, _parent, _attrs, $props, $setup, $data, $options) {
  _push(`<div${ssrRenderAttrs(_attrs)}><h1 id="warehouse-仓库银行" tabindex="-1">Warehouse 仓库银行 <a class="header-anchor" href="#warehouse-仓库银行" aria-label="Permalink to &quot;Warehouse 仓库银行&quot;">​</a></h1><div class="warning custom-block"><p class="custom-block-title">WIP</p><p>本模块仍在开发中，接口契约可能调整。</p></div><h2 id="功能定位" tabindex="-1">功能定位 <a class="header-anchor" href="#功能定位" aria-label="Permalink to &quot;功能定位&quot;">​</a></h2><p>个人仓库（物品存取）+ 多货币银行（活期/定期存款）。</p><h2 id="依赖" tabindex="-1">依赖 <a class="header-anchor" href="#依赖" aria-label="Permalink to &quot;依赖&quot;">​</a></h2><ul><li>必需：ArcartX</li><li>可选：Vault / PlayerPoints</li></ul><h2 id="启用步骤" tabindex="-1">启用步骤 <a class="header-anchor" href="#启用步骤" aria-label="Permalink to &quot;启用步骤&quot;">​</a></h2><div class="language-yaml vp-adaptive-theme"><button title="Copy Code" class="copy"></button><span class="lang">yaml</span><pre class="shiki shiki-themes github-light github-dark vp-code" tabindex="0"><code><span class="line"><span style="${ssrRenderStyle({ "--shiki-light": "#22863A", "--shiki-dark": "#85E89D" })}">modules</span><span style="${ssrRenderStyle({ "--shiki-light": "#24292E", "--shiki-dark": "#E1E4E8" })}">:</span></span>
<span class="line"><span style="${ssrRenderStyle({ "--shiki-light": "#22863A", "--shiki-dark": "#85E89D" })}">  warehouse</span><span style="${ssrRenderStyle({ "--shiki-light": "#24292E", "--shiki-dark": "#E1E4E8" })}">:</span></span>
<span class="line"><span style="${ssrRenderStyle({ "--shiki-light": "#22863A", "--shiki-dark": "#85E89D" })}">    enabled</span><span style="${ssrRenderStyle({ "--shiki-light": "#24292E", "--shiki-dark": "#E1E4E8" })}">: </span><span style="${ssrRenderStyle({ "--shiki-light": "#005CC5", "--shiki-dark": "#79B8FF" })}">true</span></span>
<span class="line"><span style="${ssrRenderStyle({ "--shiki-light": "#22863A", "--shiki-dark": "#85E89D" })}">    password</span><span style="${ssrRenderStyle({ "--shiki-light": "#24292E", "--shiki-dark": "#E1E4E8" })}">: </span><span style="${ssrRenderStyle({ "--shiki-light": "#032F62", "--shiki-dark": "#9ECBFF" })}">&quot;AXS-Warehouse@2026#Ready&quot;</span></span></code></pre></div><h2 id="命令" tabindex="-1">命令 <a class="header-anchor" href="#命令" aria-label="Permalink to &quot;命令&quot;">​</a></h2><p>管理：</p><div class="language- vp-adaptive-theme"><button title="Copy Code" class="copy"></button><span class="lang"></span><pre class="shiki shiki-themes github-light github-dark vp-code" tabindex="0"><code><span class="line"><span>/AXS warehouse status</span></span>
<span class="line"><span>/AXS warehouse reload</span></span>
<span class="line"><span>/AXS warehouse open &lt;player&gt;</span></span>
<span class="line"><span>/AXS warehouse info &lt;player&gt;</span></span>
<span class="line"><span>/AXS warehouse password &lt;player&gt; clear</span></span>
<span class="line"><span>/AXS warehouse bank &lt;player&gt; &lt;currency&gt; &lt;set|add|take&gt; &lt;amount&gt;</span></span></code></pre></div><p>玩家：</p><div class="language- vp-adaptive-theme"><button title="Copy Code" class="copy"></button><span class="lang"></span><pre class="shiki shiki-themes github-light github-dark vp-code" tabindex="0"><code><span class="line"><span>/warehouse</span></span>
<span class="line"><span>/wh</span></span></code></pre></div><h2 id="papi" tabindex="-1">PAPI <a class="header-anchor" href="#papi" aria-label="Permalink to &quot;PAPI&quot;">​</a></h2><p>前缀：<code>%AXSwarehouse_*%</code></p><div class="language- vp-adaptive-theme"><button title="Copy Code" class="copy"></button><span class="lang"></span><pre class="shiki shiki-themes github-light github-dark vp-code" tabindex="0"><code><span class="line"><span>%AXSwarehouse_total_items%</span></span>
<span class="line"><span>%AXSwarehouse_personal_used%</span></span>
<span class="line"><span>%AXSwarehouse_personal_capacity%</span></span>
<span class="line"><span>%AXSwarehouse_bank_balance_&lt;currency&gt;%</span></span></code></pre></div></div>`);
}
const _sfc_setup = _sfc_main.setup;
_sfc_main.setup = (props, ctx) => {
  const ssrContext = useSSRContext();
  (ssrContext.modules || (ssrContext.modules = /* @__PURE__ */ new Set())).add("modules/warehouse.md");
  return _sfc_setup ? _sfc_setup(props, ctx) : void 0;
};
const warehouse = /* @__PURE__ */ _export_sfc(_sfc_main, [["ssrRender", _sfc_ssrRender]]);
export {
  __pageData,
  warehouse as default
};
