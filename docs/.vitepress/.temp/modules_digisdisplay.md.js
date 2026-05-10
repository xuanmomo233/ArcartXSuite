import { ssrRenderAttrs } from "vue/server-renderer";
import { useSSRContext } from "vue";
import { _ as _export_sfc } from "./plugin-vue_export-helper.1tPrXgE0.js";
const __pageData = JSON.parse('{"title":"","description":"","frontmatter":{"redirect":"/modules/combateffect"},"headers":[],"relativePath":"modules/digisdisplay.md","filePath":"modules/digisdisplay.md","lastUpdated":1778398530000}');
const _sfc_main = { name: "modules/digisdisplay.md" };
function _sfc_ssrRender(_ctx, _push, _parent, _attrs, $props, $setup, $data, $options) {
  _push(`<div${ssrRenderAttrs(_attrs)}><p>请参阅 <strong><a href="./combateffect.html">CombatEffect 战斗特效</a></strong>。</p></div>`);
}
const _sfc_setup = _sfc_main.setup;
_sfc_main.setup = (props, ctx) => {
  const ssrContext = useSSRContext();
  (ssrContext.modules || (ssrContext.modules = /* @__PURE__ */ new Set())).add("modules/digisdisplay.md");
  return _sfc_setup ? _sfc_setup(props, ctx) : void 0;
};
const digisdisplay = /* @__PURE__ */ _export_sfc(_sfc_main, [["ssrRender", _sfc_ssrRender]]);
export {
  __pageData,
  digisdisplay as default
};
