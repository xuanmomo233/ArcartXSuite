// Executes the subset of Shimmer used by the bundled Abyss UIs against a deferred control tree.
// This checks contracts and event ordering; it is not an ArcartX renderer.
import vm from 'node:vm';
import fs from 'node:fs';
import assert from 'node:assert/strict';

const {documents,routes} = JSON.parse(fs.readFileSync(0, 'utf8'));
Object.defineProperty(Object.prototype, 'keys', {value(){return Object.keys(this)}, configurable:true});
Object.defineProperty(Number.prototype, 'round', {value(places=0){const p=10**places; return Math.round(this*p)/p}, configurable:true});
function rewrite(code) {
  const quoted= /("(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|\/\/[^\n]*)/g;
  const transform=s=>s.replace(/\bvar\./g,'state.').replace(/\bval\./g,'refs.')
    .replace(/\bnone\b/g,'null').replace(/\belif\b/g,'else if')
    .replace(/\b(\d+)\.round\(/g,'($1).round(');
  let out='',start=0;
  for(const m of code.matchAll(quoted)){out+=transform(code.slice(start,m.index))+m[0];start=m.index+m[0].length;}
  out+=transform(code.slice(start));
  return out.replace(/for\(([\p{L}\w]+) in (.+)\)\{/gu,'for($1 of $2){');
}
const AIR='{"id":"minecraft:air","Count":1b}';
function row(i,version) {return {id:'id_'+version+'_'+i,key:'object_'+i,name:'LongPlayerName16',label:'标题'+i,
 title:'本局条目'+i,detail:'剩余2次或待发奖励',target:'00000000-0000-0000-0000-000000000001',
 icon:'◆',kind:'interact',mark:'point',kindLabel:'交互点',kindColor:'186,140,232',summary:'摘要',
 iconJson:AIR,desc0:'第一行说明',desc1:'第二行说明',desc2:'第三行说明',difficulty:'普通',minPlayers:1,maxPlayers:8,
 partyRequired:false,staminaCost:1,moneyCost:5,cooldown:10,cooldownLeft:0,stamina:100,running:0,timeLimit:600,
 partyId:'party_'+i,partyName:'队伍'+i,leader:i===0,size:2,max:8,dungeonId:'dungeon',dungeonName:'副本',
 joinMode:i%2?'APPLY':'OPEN',full:false,from:'Leader',uuid:'00000000-0000-0000-0000-00000000000'+i,
 online:true,ready:true,sender:'玩家',text:'消息'+version+'-'+i,display:'&6玩家 &7: &f消息'+version+'-'+i,depth:0,count:1,dirty:false,
 rank:i+1,time:'10:20',completions:123456,kills:0,damage:123456789,taken:20,heal:30,killsText:'0',
 damageText:'1.2亿',takenText:'20',healText:'30',completionsText:'12.3万',rarity:'rare',rarityColor:'80,150,235,255',
 price:i+1,priceText:''+(i+1),selected:false,accepted:false};}
function fixture(n,version=0) {
 const rows=Object.fromEntries(Array.from({length:n},(_,i)=>[''+i,row(i,version)]));
 const p={...row(0,version),packetId:'abyss',action_token:'token',view_nonce:'nonce',title:'深渊回廊',dungeonName:'测试副本',dungeonId:'test',
 hasParty:true,hasChest:true,hasRewardGame:true,isLeader:true,partyName:'编队',partyText:'四人小队',myUuid:'me',maxMembers:8,
 offer:'offer',mode:'flip',remaining:2,revision:0,
 hasDungeon:true,dungeon:row(0,version),allowInvite:true,keyword:'',dungeonFilter:'',listed:true,joinMode:'OPEN',allReady:true,
 myReady:true,feedRevision:''+version,puzzleId:'puzzle',hint:'请阅读线索后输入答案',length:24,pattern:'',masked:n%2?'1':'0',
 attemptsText:'剩余3次',attemptsLeft:3,poolId:'buff',poolName:'祝福',timeout:20,timeoutText:'超时自动选择',
 picksLeft:1,coins:30,coinsText:'30',shopId:'shop',shopTitle:'商店',chestTitle:'奖励',itemCount:n,
 dirty:1,worldDirty:true,saving:false,worldSaveLabel:'配置+建筑',worldSaveHint:'建筑保存完成后再退出',
 selNode:n?'object_0':'',nodeKey:'misc:objects',objKey:'interact:test',objId:'test',kindLabel:'交互点',
 kindIcon:'◆',summary:'对象摘要',markable:true,status:'ACTIVE',statusLabel:'进行中',
 groupTotal:4,groupDone:1,activeGroup:'wave_1',aliveMobs:3,alivePlayers:4,totalPlayers:4,timeText:'10:20',timeRatio:0.5,guideText:'前往下一房间',progressLabel:'波次'};
 for(const [data,count]of Object.entries({dungeonRows:'dungeonCount',partyRows:'partyCount',inviteRows:'inviteCount',
 nodeRows:'nodeCount',objRows:'objCount',kindRows:'kindCount',memberRows:'memberCount',feedRows:'feedCount',
 applicantRows:'applicantCount',allDungeonRows:'allDungeonCount',onlineRows:'onlineCount',cards:'count',
 goods:'goodsCount',statRows:'statCount',boardRows:'boardCount',runRows:'runCount'})){p[data]=rows;p[count]=n}
 p.runMode='rescue';p.runHint='保持原位完成救援';p.hasPrevious=false;p.hasNext=n>8;
 p.fieldCount=n;p.fieldRows=Object.fromEntries(Array.from({length:n},(_,i)=>[''+i,
 {name:'field'+i,editor:['TEXT','TOGGLE','CYCLE','COORD'][i%4],label:'属性',value:'true',valueZh:'是',options:'a,b'}]));
 p.groupCount=n?Math.min(3,n):0;p.groupRows=Object.fromEntries(Array.from({length:p.groupCount},(_,g)=>[''+g,{
  field:'field'+g,label:'脚本',lineCount:Math.min(12,n),lineTotal:n+12*g,lineOffset:12*g,linePage:g,linePages:Math.max(1,Math.ceil((n+12*g)/12)),
  lines:Object.fromEntries(Array.from({length:Math.min(12,n)},(_,i)=>[''+i,'message @all hello'+version+'-'+g+'-'+i]))}]));
 for(let i=0;i<9;i++){p['itemJson'+i]=AIR;p['itemAmount'+i]=i<n?64:0}
 return p;
}
class Engine {
 constructor(document,width=1920,height=1080) {
  const scale=Math.min(width/1920,height/1080);this.viewWidth=width/scale;this.viewHeight=height/scale;
  this.document=document;this.nodes=[];this.pending=[];this.delayed=[];this.packets=[];this.state={};this.refs={};this.compiled=new Map();
  this.context=vm.createContext({state:this.state,refs:this.refs,packet:{},console,Math,
   Screen:{getWidth:()=>width,getHeight:()=>height,close:()=>this.run(this.document.ui.action?.close||'',this.root)},
   Sound:{self(){}},Lerp:()=>({get:()=>0}),Smooth:()=>({get:()=>0,reset(){}}),
   Packet:{send:(...args)=>{assert(args.every(a=>a!==null&&a!==undefined),'undefined packet field: '+JSON.stringify(args));this.packets.push(args)}},
   range:(a,b)=>{assert(Number.isInteger(a)&&Number.isInteger(b),'range needs integer bounds');assert(b>=a,'descending range '+a+','+b);assert(b-a<5000,'unbounded list');return Array.from({length:b-a+1},(_,i)=>a+i)},
  });
  vm.runInContext("Object.defineProperty(Object.prototype,'keys',{value:function(){return Object.keys(this)}});Number.prototype.round=function(n=0){const p=10**n;return Math.round(this*p)/p}",this.context);
  this.root=this.make('$screen',{children:document.controls},null,true);
  this.run(document.ui.action?.load||'',this.root);
  this.run(document.ui.action?.open||'',this.root);
  this.flush();
 }
 run(code,self=this.root) {
  const previous=this.context.self;this.context.self=self?.proxy??self;
  try{
   if(!this.compiled.has(code))this.compiled.set(code,new vm.Script(rewrite(code)));
   return this.compiled.get(code).runInContext(this.context);
  }
  catch(error){throw new Error((self?.path||'$')+': '+error.message+'\n'+code,{cause:error})}
  finally{this.context.self=previous}
 }
 expression(value,node) {
  if(Array.isArray(value))return value.map(v=>this.expression(v,node));
  if(typeof value!=='string')return value;
  if(value.startsWith('~'))return value.slice(1);
  return this.run('('+value+')',node);
 }
 invoke(control,action){
  const node=this.nodes.find(n=>n.proxy===control);
  assert(node,'missing action target');this.run(node.definition.action[action],node);
 }
 make(name,definition,parent,mounted) {
  const e=this,node={name,definition,parent,mounted,removed:false,overrides:{},children:[],path:(parent?.path||'')+'/'+name};
  node.proxy=new Proxy(node,{
   get(_,key) {
    if(key==='parent')return parent?.proxy??null;
    if(key==='actions')return new Proxy({}, {get:(_,action)=>(...args)=>e.run(e.document.ui.action?.[action]||'',e.root)});
    if(key==='get')return index=>{assert(node.mounted,'unmounted get');return node.children.filter(c=>!c.removed)[index]?.proxy??null};
    if(key==='childrenCount')return ()=>node.children.filter(c=>!c.removed).length;
    if(key==='copy')return copyName=>{
      const clone=e.make(copyName,JSON.parse(JSON.stringify(node.definition)),parent,false);
      clone.overrides={...node.overrides};
      // Real copies include currently generated descendants; tests expose contamination of parent templates.
      for(const child of node.children.slice(Object.keys(node.definition.children||{}).length))
        clone.children.push(e.make(child.name,JSON.parse(JSON.stringify(child.definition)),clone,false));
      e.pending.push(()=>{if(!clone.removed){parent.children.push(clone);e.mount(clone)}});return clone.proxy;
    };
    if(key==='remove')return ()=>e.pending.push(()=>{node.removed=true;if(parent)parent.children=parent.children.filter(c=>c!==node)});
    if(key==='delayAction')return (ms,action)=>e.delayed.push({frames:Math.max(1,Math.ceil(ms/50)),action});
    if(key==='setItemIcon')return json=>{assert(json!==null&&json!==undefined,'missing icon');assert.equal(node.proxy.slotType,'Icon','setItemIcon requires an Icon slot: '+node.path);node.icon=json};
    if(key==='setContent')return text=>{assert.equal(typeof text,'string','TextBox content must be a string');node.content=text.slice(0,node.proxy.length??99999)};
    if(key==='getContent')return ()=>node.content??'input';
    if(key==='setFocus')return value=>{node.focused=value};
    if(key==='isHovered')return ()=>false;
    if(key==='getDragXRatio'||key==='getDragYRatio')return ()=>node.ratio??0;
    if(key==='setDragXRatio'||key==='setDragYRatio')return ratio=>{assert(Number.isFinite(ratio));node.ratio=Math.max(0,Math.min(1,ratio))};
    if(key==='moveX'||key==='moveY')return node.ratio??0;
    if(key==='wheelValue')return 1;
    if(key in node.overrides)return node.overrides[key];
    if(key in (definition.attribute||{})){
      const value=definition.attribute[key];
      if(['seticon'].includes(key))return ()=>e.run(value,node);
      return e.expression(value,node);
    }
    if(key==='entry')return null;
    if(key==='entryKey')return 0;
    const child=node.children.find(c=>c.name===key&&!c.removed);
    if(child){assert(node.mounted&&child.mounted,'copied descendant accessed before mount: '+node.path+'/'+String(key));return child.proxy}
    if(['width','height','x','y'].includes(key))return 0;
    return undefined;
   },
   set(_,key,value){node.overrides[key]=value;return true}
  });
  this.nodes.push(node);
  if(definition.val&&mounted)this.refs[definition.val]=node.proxy;
  for(const [child,def]of Object.entries(definition.children||{}))node.children.push(this.make(child,def,node,mounted));
  return node;
 }
 mount(node){node.mounted=true;for(const child of node.children)this.mount(child)}
 flush(){
  for(const work of this.pending.splice(0))work();
  for(const node of this.nodes)if(['hstack','vstack'].includes((node.definition.type||'').toLowerCase())&&!node.removed){
   const max=node.proxy.maxSize??64;
   while(node.children.length>max){const removed=node.children.shift();removed.removed=true}
  }
 }
 tick() {
  this.run(this.document.ui.action?.tick||'',this.root);this.flush();
  for(const task of [...this.delayed])if(--task.frames<=0){
    this.delayed=this.delayed.filter(t=>t!==task);this.run(this.document.ui.action?.[task.action]||'',this.root);this.flush();
  }
 }
 receive(payload,handler='init'){this.context.packet=payload;this.run(this.document.ui.packetHandler?.[handler]||'',this.root);this.flush()}
 settle(){for(let i=0;i<9;i++)this.tick()}
 render(node=this.root) {
  if(node.removed)return;
  if(node!==this.root&&node.proxy.visible===false)return;
  const attrs=node.definition.attribute||{};
  for(const [key,value]of Object.entries(attrs)){
   if(['seticon','entry','visible'].includes(key))continue;
   if(typeof value==='string'&&(/\b(var|val|self)\b/.test(value)||value.includes('?'))){
    const result=node.proxy[key];
    if(['width','height','fontSize','maxDragX','maxDragY'].includes(key))assert(Number.isFinite(result)&&result>=0,'invalid geometry '+node.path+' '+key+'='+result);
    if(key==='texts')assert(!String(result).includes('undefined')&&!String(result).includes('NaN'),'missing text data: '+node.path);
    if(['normal','hover'].includes(key))assert(typeof result==='string','color expression must return string: '+node.path);
   }
  }
  for(const child of node.children)this.render(child);
 }
 visible(node) {
  if(node.removed || (node.parent && !this.visible(node.parent)))return false;
  return node.proxy.visible !== false;
 }
 // Conservative geometry model. This reports layout defects, not native rendered pixels.
 geometry() {
  const boxes=[];
  const textWidth=(text,font)=>[...String(text).replace(/(?:&|§)[0-9a-fk-or]/gi,'')]
    .reduce((n,c)=>n+(/[\u0020-\u007e]/.test(c)?(c===' '?0.32:0.6):1)*font/2,0);
  const measure=node=>{
   const p=node.proxy,a=node.definition.attribute||{},type=(node.definition.type||'').toLowerCase();
   const children=node.children.filter(c=>this.visible(c));
   const sizes=children.map(measure);
   let w=Object.hasOwn(a,'width')?p.width:0,h=Object.hasOwn(a,'height')?p.height:0;
   let lines=[],glyphWidth=0;
   if(type==='text'){
    const raw=p.texts;lines=(Array.isArray(raw)?raw:[raw??'']).flatMap(s=>String(s).split('\n'));
    lines=lines.map(s=>[...s.replace(/(?:&|§)[0-9a-fk-or]/gi,'')].slice(0,p.limit??999999).join(''));
    const natural=Math.max(0,...lines.map(s=>textWidth(s,p.fontSize||24)));
    glyphWidth=natural;
    w=w||natural;h=h||lines.length*(p.fontSize||24)/2+Math.max(0,lines.length-1)*(p.lineSpace??16);
   }
   if(type==='hstack'){w=w||sizes.reduce((n,s)=>n+s.w,0)+Math.max(0,sizes.length-1)*(p.spaceBetween||0);h=h||Math.max(0,...sizes.map(s=>s.h))}
   if(type==='vstack'){h=h||sizes.reduce((n,s)=>n+s.h,0)+Math.max(0,sizes.length-1)*(p.spaceBetween||0);w=w||Math.max(0,...sizes.map(s=>s.w))}
   if(type==='hgrid'){
    const cols=p.column||1,sx=p.spaceBetweenX||p.spaceBetween||0,sy=p.spaceBetweenY||p.spaceBetween||0;
    w=w||Math.min(cols,sizes.length)*Math.max(0,...sizes.map(s=>s.w))+Math.max(0,Math.min(cols,sizes.length)-1)*sx;
    h=h||Math.ceil(sizes.length/cols)*Math.max(0,...sizes.map(s=>s.h))+Math.max(0,Math.ceil(sizes.length/cols)-1)*sy;
   }
   if(node===this.root){w=this.viewWidth;h=this.viewHeight}
   return node.size={w,h,type,lines,glyphWidth};
  };
  const place=(node,parentBox)=>{
   if(!this.visible(node))return;
   const {w:mw,h:mh,type,lines,glyphWidth}=node.size,p=node.proxy,point=p.point||'top_left';
   let w=mw,h=mh,x=Number(p.x)||0,y=Number(p.y)||0;
   if(point==='stretch_all'){w=parentBox.w;h=parentBox.h}
   if(point.includes('right'))x+=parentBox.w-w;else if(point.includes('center'))x+=(parentBox.w-w)/2;
   if(point.startsWith('bottom'))y+=parentBox.h-h;else if(point.startsWith('middle'))y+=(parentBox.h-h)/2;
   const siblings=node.parent?.children.filter(c=>this.visible(c))||[],index=siblings.indexOf(node);
   const pt=node.parent?.size.type;
   if(pt==='hstack')x=siblings.slice(0,index).reduce((n,c)=>n+c.size.w+(node.parent.proxy.spaceBetween||0),0);
   if(pt==='vstack')y=siblings.slice(0,index).reduce((n,c)=>n+c.size.h+(node.parent.proxy.spaceBetween||0),0);
   if(pt==='hgrid'){
    const q=node.parent.proxy,cols=q.column||1;
    x=(index%cols)*(w+(q.spaceBetweenX||q.spaceBetween||0));y=Math.floor(index/cols)*(h+(q.spaceBetweenY||q.spaceBetween||0));
   }
   const box={path:node.path,x:parentBox.x+x,y:parentBox.y+y,w,h,type,lines,
     glyphWidth:p.limitControl?Math.min(w,glyphWidth):glyphWidth,
     clickable:!!node.definition.action?.clickLeft,parent:node.parent?.path};
   node.box=box;boxes.push(box);
   for(const child of node.children)place(child,box);
  };
  measure(this.root);place(this.root,{x:0,y:0,w:this.viewWidth,h:this.viewHeight});
  return boxes;
 }
 checkGeometry(){
  const boxes=this.geometry(),byPath=new Map(boxes.map(b=>[b.path,b]));
  for(const b of boxes){
   let parent=byPath.get(b.parent);
   // Full-screen children compensate for Adaptive's centered 1920-wide design frame.
   if(parent?.type==='adaptive')parent={...parent,x:0,y:0,w:this.viewWidth,h:this.viewHeight};
   if(b.type!=='text'||!parent||['hstack','scroll'].includes(parent.type))continue;
   assert(b.x>=parent.x-2&&b.x+b.glyphWidth<=parent.x+parent.w+2
     &&b.y>=parent.y-2&&b.y+b.h<=parent.y+parent.h+2,
    'text exceeds its container: '+b.path+' '+JSON.stringify({text:b.lines,width:b.glyphWidth,parentWidth:parent.w}));
  }
  for(let i=0;i<boxes.length;i++){
   const b=boxes[i];if(b.type!=='text'||b.glyphWidth===0)continue;
   for(const c of boxes.slice(i+1)){
    if(c.parent!==b.parent||(c.type!=='text'&&!c.clickable))continue;
    const overlapX=Math.min(b.x+b.glyphWidth,c.x+(c.type==='text'?c.glyphWidth:c.w))-Math.max(b.x,c.x);
    const overlapY=Math.min(b.y+b.h,c.y+c.h)-Math.max(b.y,c.y);
    assert(overlapX<=4||overlapY<=4,'text overlaps adjacent text/action: '+b.path+' / '+c.path);
   }
  }
 }
 clickAt(x,y){
  const boxes=this.geometry();
  for(const box of boxes.toReversed()){
   const node=this.nodes.find(n=>n.path===box.path&&!n.removed);
   if(!node||node.proxy.through===true||x<box.x||x>box.x+box.w||y<box.y||y>box.y+box.h)continue;
   let enabled=true;
   for(let p=node;p;p=p.parent)if(p.proxy.enable===false)enabled=false;
   if(enabled&&node.definition.action?.clickLeft)this.run(node.definition.action.clickLeft,node);
   return node;
  }
 }
 checkLists(node=this.root) {
  if(!this.visible(node))return;
  const templates=node.children.filter(c=>Object.hasOwn(c.definition.attribute||{},'entry'));
  if(templates.length) {
    const visible=templates.filter(c=>c.proxy.entry!=null);
    const keys=visible.map(c=>c.proxy.entryKey);
    assert.equal(new Set(keys).size,keys.length,'duplicate generated keys in '+node.path);
    const expression=templates[0].definition.attribute.entry;
    const match=expression.match(/^var\.([\p{L}\w]+)\[self.entryKey\]$/u);
    if(match && this.state[match[1]])assert.equal(templates.length,Math.max(1,Object.keys(this.state[match[1]]).length),'stale generated controls in '+node.path);
  }
  for(const child of node.children)this.checkLists(child);
 }
 interactions() {
  for(const node of [...this.nodes]) {
   if(!this.visible(node))continue;
   for(const action of ['clickLeft','enter','wheel']) {
    const script=node.definition.action?.[action];if(script)this.run(script,node);
   }
  }
  for(const args of this.packets){
   assert.equal(args[0],'abyss','wrong client channel');
   assert(Object.hasOwn(routes,args[1]),'missing server route '+args[1]);
   const token=routes[args[1]];
   if(token!=null)assert.equal(args[token+1],'token','wrong token position for '+args[1]);
  }
 }
}
let scenarios=0;
const layouts={};
for(const [name,doc]of Object.entries(documents)) {
 const engine=new Engine(doc);
 engine.settle();engine.render();engine.interactions();
 assert(engine.packets.every(p=>p[1]==='view_ready'),'uninitialized screen must only request mount readiness: '+name);
 scenarios++;
 for(const [version,n]of [0,1,4,12,65,70,1,0,3,3].entries()) {
  engine.receive(fixture(n,version),name==='hud'?'update':'init');engine.settle();engine.render();engine.checkLists();scenarios++;
 }
 // Rapid data replacement before a frame: old queued mutations must not leak descendants.
 for(const n of [4,1,3,0,2])engine.receive(fixture(n,10+n),name==='hud'?'update':'init');
 engine.settle();engine.render();engine.checkLists();scenarios++;
 engine.state.invitePanel=true;engine.state.dungeonPanel=false;engine.render();
 engine.state.invitePanel=false;engine.state.dungeonPanel=true;engine.render();
 engine.state.invitePanel=false;engine.state.dungeonPanel=false;
 layouts[name]=engine.geometry();
 if(name==='design_edit'){
  assert.equal(engine.refs.rename_input.getContent(),'test','rename must contain the current id');
  assert.equal(engine.refs.字段容器.get(0)['输入壳']['输入框'].getContent(),'true','field must be editable, not a placeholder');
  const second=engine.state.组引用[1],firstLine=second['行容器'].get(0);
  assert.equal(firstLine['行号'].texts,'&613','page 2 must show absolute line numbers');
  engine.invoke(firstLine['改钮'],'clickLeft');
  assert.equal(engine.state.editI,12,'editing must use the absolute index');
  assert(second['追加框']['框']['输入框'].getContent().includes('hello'),'editing must load the complete original line');
  const firstGroup=engine.refs.组容器.get(0);
  engine.invoke(firstGroup['行容器'].get(0)['改钮'],'clickLeft');
  assert.equal(second['追加框']['框']['输入框'].getContent(),'','switching edit groups must clear the previous loaded edit');
  engine.invoke(firstGroup['追加框']['取消编辑'],'clickLeft');
  assert.equal(engine.state.editI,-1);assert.equal(firstGroup['追加框']['框']['输入框'].getContent(),'');
 }
 if(name==='input'){
  const plain={...fixture(1),masked:'0',pattern:'',length:8};engine.receive(plain);
  assert.equal(engine.refs.输入框.passwordChar,'');assert.equal(engine.refs.输入框.inputPattern,'');
  assert.equal(engine.refs.输入框.length,8);assert.equal(engine.refs.输入框.getContent(),'');
 }
 if(name==='shop'){
  engine.receive({coins:0,coinsText:'0'},'update');
  const good=engine.refs.goods_rows.get(0),before=engine.packets.filter(p=>p[1]==='shop_buy').length;
  engine.invoke(good,'clickLeft');
  assert.equal(engine.packets.filter(p=>p[1]==='shop_buy').length,before,'unaffordable goods must not submit a purchase');
  assert.equal(engine.state.coinsText,'0','balance text must update with the numeric balance');
 }
 if(name==='run'){
  engine.receive(fixture(2));engine.render();
  const row=engine.nodes.find(node=>node.name==='行0');
  engine.run(row.definition.action.clickLeft,row);
  assert.deepEqual(engine.packets.at(-1),['abyss','run_rescue','00000000-0000-0000-0000-000000000001','token']);
  engine.receive({...fixture(2),runMode:'loot'});engine.render();
  const before=engine.packets.length;
  engine.run(row.definition.action.clickLeft,row);
  assert.equal(engine.packets.length,before,'read-only loot rows cannot claim rewards');
 }
 if(name==='reward_game'){
  engine.receive({...fixture(1),mode:'wheel',remaining:1});engine.render();
  const wheel=engine.nodes.find(n=>n.name==='牌0');
  const before=engine.packets.length;
  engine.run(wheel.definition.action.clickLeft,wheel);
  engine.run(wheel.definition.action.clickLeft,wheel);
  assert.equal(engine.packets.length,before+1,'wheel must ignore clicks during spin');
  assert.deepEqual(engine.packets.at(-1),['abyss','reward_game_pick','offer',0,0,'token']);
  const revealed=fixture(1);revealed.cards['0'].selected=true;revealed.cards['0'].accepted=true;
  engine.receive({...revealed,mode:'wheel',remaining:0,revision:1});
  for(let i=0;i<20;i++)engine.tick();
  assert.equal(engine.state.spin,0);
  engine.checkGeometry();
  engine.receive({...fixture(3),mode:'choose-three',remaining:1});
  engine.render();engine.checkGeometry();
 }
 if(name==='party'){
  engine.state.chatMsg='hello';engine.refs.party_chat_input.setContent('hello');
  const send=engine.nodes.find(n=>n.name==='队聊发送');engine.run(send.definition.action.clickLeft,send);
  assert.equal(engine.refs.party_chat_input.getContent(),'','sent chat must clear its actual input');
  engine.state.isLeader=false;engine.state.invitePanel=true;engine.state.dungeonPanel=false;
  const prepare=engine.geometry().find(b=>b.path.endsWith('/准备按钮')),before=engine.packets.length;
  engine.clickAt(prepare.x+prepare.w/2,prepare.y+prepare.h/2);
  assert.equal(engine.packets.length,before,'invite modal mask must block the background button');
 }
 engine.interactions();
 const long=fixture(4);
 const makeLong=value=>{
  if(value&&typeof value==='object')for(const key of Object.keys(value)){
   if(['name','label','partyName','dungeonName','title','shopTitle','chestTitle','summary','desc0','desc1','desc2','guideText','activeGroup'].includes(key))value[key]='深渊远征测试名称与完整说明'.repeat(4);
   else makeLong(value[key]);
  }
 };
 makeLong(long);
 if(name==='design_edit')long.fieldRows['0'].value='字段值'.repeat(150);
 const longEngine=new Engine(doc);longEngine.receive(long,name==='hud'?'update':'init');longEngine.settle();longEngine.render();
 if(name==='design_edit')assert.equal(longEngine.refs.字段容器.get(0)['输入壳']['输入框'].getContent(),long.fieldRows['0'].value,'prefill must not truncate an existing scalar');
 longEngine.checkGeometry();
 layouts[name]=longEngine.geometry();scenarios++;
 for(const viewport of [[1280,720],[1280,1024],[2560,1080]]) {
  const other=new Engine(doc,...viewport);other.receive(fixture(4),name==='hud'?'update':'init');
  other.settle();other.render();other.checkGeometry();scenarios++;
 }
 console.log(name+': lifecycle, empty/single/grow/shrink/rapid-update, controls, packets and three aspect ratios passed');
}
console.log('PASS '+Object.keys(documents).length+' UIs; '+scenarios+' state/viewport scenarios');
if(process.env.ABYSS_LAYOUT_REPORT)fs.writeFileSync(process.env.ABYSS_LAYOUT_REPORT,JSON.stringify(layouts,null,2),'utf8');
