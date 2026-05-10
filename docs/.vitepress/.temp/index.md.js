import { ssrRenderAttrs } from "vue/server-renderer";
import { useSSRContext } from "vue";
import { _ as _export_sfc } from "./plugin-vue_export-helper.1tPrXgE0.js";
const __pageData = JSON.parse('{"title":"","description":"","frontmatter":{"layout":"home","hero":{"name":"ArcartXSuite","text":"生态全面 自带UI 为每一个 ArcartX服务器 筑梦未来","image":{"src":"/logo.svg","alt":"ArcartXSuite"},"actions":[{"theme":"brand","text":"五分钟上手","link":"/guide/"},{"theme":"alt","text":"浏览模块","link":"/modules/"},{"theme":"alt","text":"GitHub","link":"https://github.com/xuanmomo233/ArcartXSuite"}]},"features":[{"icon":"📢","title":"Announcer 播报系统","details":"常驻/轮播 HUD 公告 + 打字机字幕动画，可点击执行命令","link":"/modules/announcer"},{"icon":"🐉","title":"EntityTracker 实体追踪","details":"Boss 血条、伤害排行、自动结算 + 攻击目标 HUD 实时显示","link":"/modules/entitytracker"},{"icon":"💬","title":"Chat 频道聊天","details":"多频道、私聊、@提及、SocialSpy、禁言、跨服 Redis 转发","link":"/modules/chat"},{"icon":"🗣️","title":"Conversation 对话桥","details":"Chemdah 对话 + Adyeshach NPC 联动，ArcartX UI 渲染","link":"/modules/conversation"},{"icon":"⚡","title":"EventPacket 事件引擎","details":"16 种触发器 × 6 种动作自由组合，事件驱动玩法编排","link":"/modules/eventpacket"},{"icon":"🎯","title":"CombatEffect 战斗特效","details":"击杀特效 + 伤害飘字，战斗视觉反馈一站式解决","link":"/modules/combateffect"},{"icon":"🔐","title":"LoginView 登录界面","details":"ArcartX UI 驱动的登录/注册面板，支持独立模式和 AuthMe 兼容","link":"/modules/loginview"},{"icon":"📬","title":"Mail 邮箱","details":"玩家写信、预设派发、CDK 兑换、物品附件、跨服广播","link":"/modules/mail"},{"icon":"🎁","title":"OnlineRewards 在线奖励","details":"在线时长奖励、每日签到、补签卡、四维排行榜","link":"/modules/onlinerewards"},{"icon":"✨","title":"Pickup 拾取提示","details":"物品拾取时 HUD 弹出提示动画","link":"/modules/pickup"},{"icon":"🗡️","title":"Prop 快捷道具","details":"道具快捷键绑定、客户端按键效果、临时属性加成","link":"/modules/prop"},{"icon":"🌈","title":"RGB 渐变文本","details":"PAPI 输出渐变/扫光效果，支持嵌套其他占位符","link":"/modules/rgb"},{"icon":"📋","title":"Tab 在线列表","details":"ArcartX TAB UI 驱动的自定义在线列表，支持排序和 PAPI","link":"/modules/tab"},{"icon":"🏅","title":"Title 称号","details":"分组称号、有效期/永久、属性加成、聊天/TAB 前缀","link":"/modules/title"},{"icon":"🗺️","title":"Map 世界地图","details":"锚点传送、玩家路径点、小地图 HUD、世界解锁","link":"/modules/map"},{"icon":"🧭","title":"QuestGPS 任务导航","details":"Chemdah 任务追踪、路径点 HUD 导航","link":"/modules/questgps"},{"icon":"🏦","title":"Warehouse 仓库银行","details":"个人仓库物品存取 + 多货币银行定期存款","link":"/modules/warehouse"}]},"headers":[],"relativePath":"index.md","filePath":"index.md","lastUpdated":1778398530000}');
const _sfc_main = { name: "index.md" };
function _sfc_ssrRender(_ctx, _push, _parent, _attrs, $props, $setup, $data, $options) {
  _push(`<div${ssrRenderAttrs(_attrs)}><div class="stats-bar"><div class="stat-item"><div class="stat-number">17</div><div class="stat-label">主模块</div></div><div class="stat-item"><div class="stat-number">7</div><div class="stat-label">PAPI 输出</div></div><div class="stat-item"><div class="stat-number">6</div><div class="stat-label">数据库模块</div></div><div class="stat-item"><div class="stat-number">8</div><div class="stat-label">反射桥</div></div></div></div>`);
}
const _sfc_setup = _sfc_main.setup;
_sfc_main.setup = (props, ctx) => {
  const ssrContext = useSSRContext();
  (ssrContext.modules || (ssrContext.modules = /* @__PURE__ */ new Set())).add("index.md");
  return _sfc_setup ? _sfc_setup(props, ctx) : void 0;
};
const index = /* @__PURE__ */ _export_sfc(_sfc_main, [["ssrRender", _sfc_ssrRender]]);
export {
  __pageData,
  index as default
};
