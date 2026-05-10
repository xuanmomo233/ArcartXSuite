import { ssrRenderAttrs } from "vue/server-renderer";
import { useSSRContext } from "vue";
import { _ as _export_sfc } from "./plugin-vue_export-helper.1tPrXgE0.js";
const __pageData = JSON.parse('{"title":"","description":"","frontmatter":{"redirect":"/modules/eventpacket"},"headers":[],"relativePath":"modules/packetcommand.md","filePath":"modules/packetcommand.md","lastUpdated":1778398530000}');
const _sfc_main = { name: "modules/packetcommand.md" };
function _sfc_ssrRender(_ctx, _push, _parent, _attrs, $props, $setup, $data, $options) {
  _push(`<div${ssrRenderAttrs(_attrs)}><p>请参阅 <strong><a href="./eventpacket.html">EventPacket 事件引擎</a></strong>。</p></div>`);
}
const _sfc_setup = _sfc_main.setup;
_sfc_main.setup = (props, ctx) => {
  const ssrContext = useSSRContext();
  (ssrContext.modules || (ssrContext.modules = /* @__PURE__ */ new Set())).add("modules/packetcommand.md");
  return _sfc_setup ? _sfc_setup(props, ctx) : void 0;
};
const packetcommand = /* @__PURE__ */ _export_sfc(_sfc_main, [["ssrRender", _sfc_ssrRender]]);
export {
  __pageData,
  packetcommand as default
};
