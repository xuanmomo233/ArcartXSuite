import { ssrRenderAttrs } from "vue/server-renderer";
import { useSSRContext } from "vue";
import { _ as _export_sfc } from "./plugin-vue_export-helper.1tPrXgE0.js";
const __pageData = JSON.parse('{"title":"PlaceholderAPI 速查","description":"","frontmatter":{},"headers":[],"relativePath":"guide/placeholders.md","filePath":"guide/placeholders.md","lastUpdated":null}');
const _sfc_main = { name: "guide/placeholders.md" };
function _sfc_ssrRender(_ctx, _push, _parent, _attrs, $props, $setup, $data, $options) {
  _push(`<div${ssrRenderAttrs(_attrs)}><h1 id="placeholderapi-速查" tabindex="-1">PlaceholderAPI 速查 <a class="header-anchor" href="#placeholderapi-速查" aria-label="Permalink to &quot;PlaceholderAPI 速查&quot;">​</a></h1><p>AXS 中<strong>会对外输出 PAPI 的模块</strong>有 7 个。</p><table tabindex="0"><thead><tr><th>模块</th><th>前缀</th><th>必装 PAPI?</th><th>典型字段</th></tr></thead><tbody><tr><td><a href="/ArcartXSuite/modules/entitytracker.html">EntityTracker</a></td><td><code>%AXSentitytracker_*%</code></td><td>可选</td><td><code>current_*</code>, <code>slot_&lt;n&gt;_*</code>, <code>top_&lt;rank&gt;_*</code>, <code>last_*</code></td></tr><tr><td><a href="/ArcartXSuite/modules/title.html">Title</a></td><td><code>%AXStitle_*%</code></td><td>可选</td><td><code>chat_&lt;group&gt;_prefix</code>, <code>equipped_&lt;group&gt;_id</code></td></tr><tr><td><a href="/ArcartXSuite/modules/rgb.html">RGB</a></td><td><code>%arcartrgb_*%</code></td><td><strong>必装</strong></td><td><code>%arcartrgb_&lt;entryId&gt;%</code></td></tr><tr><td><a href="/ArcartXSuite/modules/onlinerewards.html">OnlineRewards</a></td><td><code>%AXSonlinerewards_*%</code></td><td>可选</td><td><code>daily_minutes</code>, <code>signin_streak</code></td></tr><tr><td><a href="/ArcartXSuite/modules/mail.html">Mail</a></td><td><code>%AXSmail_*%</code></td><td>可选</td><td><code>unread_count</code>, <code>claimable_count</code></td></tr><tr><td><a href="/ArcartXSuite/modules/chat.html">Chat</a></td><td><code>%AXSchat_*%</code></td><td>可选</td><td><code>current_channel</code>, <code>muted</code></td></tr><tr><td><a href="/ArcartXSuite/modules/warehouse.html">Warehouse</a></td><td><code>%AXSwarehouse_*%</code></td><td>可选</td><td><code>total_items</code>, <code>bank_balance_&lt;currency&gt;</code></td></tr></tbody></table><h2 id="entitytracker-占位符要点" tabindex="-1">EntityTracker 占位符要点 <a class="header-anchor" href="#entitytracker-占位符要点" aria-label="Permalink to &quot;EntityTracker 占位符要点&quot;">​</a></h2><div class="language- vp-adaptive-theme"><button title="Copy Code" class="copy"></button><span class="lang"></span><pre class="shiki shiki-themes github-light github-dark vp-code" tabindex="0"><code><span class="line"><span>%AXSentitytracker_current_display_name%</span></span>
<span class="line"><span>%AXSentitytracker_current_health_percent%</span></span>
<span class="line"><span>%AXSentitytracker_current_viewer_rank_text%</span></span>
<span class="line"><span>%AXSentitytracker_slot_2_top_1_name%</span></span>
<span class="line"><span>%AXSentitytracker_last_rank%</span></span></code></pre></div><h2 id="title-占位符要点" tabindex="-1">Title 占位符要点 <a class="header-anchor" href="#title-占位符要点" aria-label="Permalink to &quot;Title 占位符要点&quot;">​</a></h2><div class="language- vp-adaptive-theme"><button title="Copy Code" class="copy"></button><span class="lang"></span><pre class="shiki shiki-themes github-light github-dark vp-code" tabindex="0"><code><span class="line"><span>%AXStitle_chat_&lt;groupId&gt;_prefix%</span></span>
<span class="line"><span>%AXStitle_tab_&lt;groupId&gt;_prefix%</span></span>
<span class="line"><span>%AXStitle_equipped_&lt;groupId&gt;_id%</span></span>
<span class="line"><span>%AXStitle_owned_&lt;titleId&gt;%</span></span>
<span class="line"><span>%AXStitle_remaining_&lt;titleId&gt;%</span></span></code></pre></div><h2 id="反向消费-papi" tabindex="-1">反向消费 PAPI <a class="header-anchor" href="#反向消费-papi" aria-label="Permalink to &quot;反向消费 PAPI&quot;">​</a></h2><p>很多模块会<strong>消费 PAPI 字符串</strong>作为排序键、阈值条件等：</p><table tabindex="0"><thead><tr><th>模块</th><th>字段</th><th>用途</th></tr></thead><tbody><tr><td>Tab</td><td><code>tabs.&lt;id&gt;.pack</code> / <code>sort-papi-key</code></td><td>每个玩家渲染一行</td></tr><tr><td>EventPacket</td><td><code>rules.&lt;id&gt;.placeholder</code></td><td>触发器阈值监控</td></tr><tr><td>Mail</td><td><code>currencies.&lt;id&gt;.balance-placeholder</code></td><td>货币余额查询</td></tr><tr><td>EntityTracker</td><td><code>bosses.&lt;id&gt;.title-format</code></td><td>先替换内置变量，再走 PAPI</td></tr></tbody></table></div>`);
}
const _sfc_setup = _sfc_main.setup;
_sfc_main.setup = (props, ctx) => {
  const ssrContext = useSSRContext();
  (ssrContext.modules || (ssrContext.modules = /* @__PURE__ */ new Set())).add("guide/placeholders.md");
  return _sfc_setup ? _sfc_setup(props, ctx) : void 0;
};
const placeholders = /* @__PURE__ */ _export_sfc(_sfc_main, [["ssrRender", _sfc_ssrRender]]);
export {
  __pageData,
  placeholders as default
};
