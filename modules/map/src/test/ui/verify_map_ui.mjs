import fs from 'node:fs';
import vm from 'node:vm';
import assert from 'node:assert/strict';

const document = JSON.parse(fs.readFileSync(0, 'utf8'));
const doc = document.ui;
const controls = new Map();
function collect(node, name='ui') {
  controls.set(name, node);
  for (const [name, child] of Object.entries(node.children || {})) collect(child, name);
}
for (const [name, node] of Object.entries(document.controls)) collect(node, name);
function rewrite(code) {
  const quoted = /("(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|\/\/[^\n]*)/g;
  const convert = s => s.replace(/\bvar\./g, 'state.').replace(/\bval\./g, 'refs.')
    .replace(/\bnone\b/g, 'null').replace(/\belif\b/g, 'else if').replace(/\b(\d+)\.round\(/g, '($1).round(');
  let out = '', start = 0;
  for (const match of code.matchAll(quoted)) {
    out += convert(code.slice(start, match.index)) + match[0]; start = match.index + match[0].length;
  }
  return (out + convert(code.slice(start))).replace(/for\((\w+) in (.+)\)\s*\{/g, 'for($1 of $2){');
}
const packets = [];
let closed = 0;
const state = {mapRenderer: null, actionBusy: false, pendingRevision: -1, searchText:'', searchSent:'', actionBusySeconds:0};
const refs = {world_entry:{'地图项目':null}};
for (const [id, template] of Object.entries({anchor:'浏览锚点项',waypoint:'浏览路径点项',pin:'浏览标记点项',external:'浏览其它项'})) {
  const pool = [];
  const row = {copy(){const copy={}; pool.push(copy); return copy;}};
  pool.push(row);
  refs['browse_'+id] = {[template]:row,childrenCount:()=>pool.length,get:i=>({remove:()=>pool.splice(i,1)})};
}
refs.地图搜索框 = {getContent:()=>state.searchText,setContent:()=>{},setFocus:()=>{}};
const sandbox = {state,refs,packet:{},self:{currentKeyPress:()=> 'ESCAPE'},
  Packet:{send:(...args)=>packets.push(args)},Screen:{close:()=>closed++},call_syncWorldDims:()=>{},
  range:(a,b)=>b>=a ? Array.from({length:b-a+1},(_,i)=>a+i) : []};
const context = vm.createContext(sandbox);
vm.runInContext("Object.defineProperty(Number.prototype,'round',{value:function(){return Math.round(this)}})", context);
function run(code) { vm.runInContext(rewrite(code), context, {timeout:500}); }
function poll(now) {
  state.mapRenderer = {getUiTimeMillis:()=>now};
  run(doc.action.tick.split('// interaction-status-end')[0]);
  state.mapRenderer = null;
}
function snapshot(revision, busy, count=0) {
  sandbox.packet = {action_token:'token',actionRevision:revision,actionBusy:busy,dataState:'ready',maxWorldCount:0,
    browseAnchorCount:count,browseWaypointCount:count,browsePinCount:count,browseExternalCount:count,
    anchorRows:{},waypointRows:{},pinRows:{},externalTargetRows:{}};
  run(doc.packetHandler.update);
}
state.actionBusy=true;state.pendingRevision=4;
snapshot(4,false);assert.equal(state.actionBusy,true,'unrelated snapshot released pending write');
snapshot(5,true);assert.equal(state.actionBusy,true,'live transaction released');
snapshot(5,false);assert.equal(state.actionBusy,false,'settlement did not release UI');
console.log('PASS revision acknowledgement and active transaction ownership');
state.actionBusy=true;state.actionBusySeconds=0;packets.length=0;
state.busyStartedAt=null;
for(let i=0;i<=6;i++) poll(i*1000);
assert.equal(state.actionBusy,true);assert.equal(packets.filter(p=>p[1]==='action_status').length,1);
assert(!packets.some(p=>p[1]?.includes('delete')||p[1]?.includes('unlock')));
console.log('PASS slow-operation status checks never replay writes');
state.deletePromptVisible=true;state.editPanelVisible=true;
run(doc.action.keyPress);assert.equal(state.deletePromptVisible,false);assert.equal(state.editPanelVisible,true);assert.equal(closed,0);
run(doc.action.keyPress);assert.equal(state.editPanelVisible,false);assert.equal(closed,0);
state.detailSelectedType='none';state.browsePanelOpen=false;state.worldListOpen=false;
run(doc.action.keyPress);assert.equal(closed,1);
console.log('PASS layered escape closes the innermost layer');
state.actionBusy=false;state.detailCanDeleteWaypoint=true;state.detailSelectedId='original';state.detailTitle='测试';
packets.length=0;run(controls.get('路径点删除按钮').action.clickLeft);
assert.equal(packets.length,0);assert.equal(state.deletePromptVisible,true);
state.detailSelectedId='other';run(controls.get('确认删除').action.clickLeft);
assert.equal(packets.at(-1)[2],'original');assert.equal(state.actionBusy,true);
console.log('PASS delete requires confirmation and keeps the captured target');
state.actionBusy=false;state.searchText='北门';state.searchSent='';state.observedQuery='';packets.length=0;
poll(10000);assert.equal(packets.length,0);poll(10250);poll(10500);
assert.equal(packets.filter(p=>p[1]==='browse_options').length,1);
assert.equal(packets[0][2],'北门');
poll(12000);assert.equal(packets.filter(p=>p[1]==='browse_options').length,2);
state.serverQuery='北门';poll(20000);assert.equal(packets.filter(p=>p[1]==='browse_options').length,2);
console.log('PASS search debounces input and retries only an unacknowledged read');
for(const count of [0,1,4,0,1,5]) {
  snapshot(6,false,count);
  for(const id of ['anchor','waypoint','pin','external']) assert.equal(refs['browse_'+id].childrenCount(),Math.max(1,count));
}
console.log('PASS pooled lists grow and shrink through zero/one/many results');
