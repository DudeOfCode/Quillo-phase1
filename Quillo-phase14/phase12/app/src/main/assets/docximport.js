/* Quillo native DOCX importer.
   Reads the file's own page size, margins, styles, numbering, tables, images and sections
   (instead of converting to "semantic" HTML) so layout stays as close to Word/WPS as possible. */
(function(){
const W='http://schemas.openxmlformats.org/wordprocessingml/2006/main',R='http://schemas.openxmlformats.org/officeDocument/2006/relationships';
const WPD='http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing',AD='http://schemas.openxmlformats.org/drawingml/2006/main';
const k1=(e,n)=>e?[...e.children].find(c=>c.localName==n)||null:null;
const ks=(e,n)=>e?[...e.children].filter(c=>c.localName==n):[];
const g=(e,n,ns=W)=>{if(!e)return;const v=e.getAttributeNS(ns,n);return v===null||v===''?undefined:v};
const on=e=>{const v=g(e,'val');return !(v==='0'||v==='false'||v==='off')};
const esc=s=>s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
const f2=n=>+n.toFixed(2);
let TH={minor:'Calibri',major:'Calibri Light'};
/* fonts: metric-compatible web fonts are bundled so line breaks match Word */
const FM={calibri:'Calibri,Carlito,sans-serif','calibri light':'Calibri,Carlito,sans-serif','times new roman':"'Times New Roman',Tinos,serif",arial:'Arial,Arimo,sans-serif',cambria:'Cambria,Caladea,serif','courier new':"'Courier New',Cousine,monospace",aptos:'Calibri,Carlito,sans-serif'};
const LHF={calibri:1.22,'calibri light':1.22,'times new roman':1.15,arial:1.15,cambria:1.17,'courier new':1.13,aptos:1.22};
const fam=f=>FM[f.toLowerCase()]||`'${f}',Calibri,Carlito,sans-serif`;
const lhf=f=>LHF[(f||'').toLowerCase()]||1.2;
const HL={yellow:'#ffff00',green:'#00ff00',cyan:'#00ffff',magenta:'#ff00ff',blue:'#0000ff',red:'#ff0000',darkBlue:'#000080',darkCyan:'#008080',darkGreen:'#008000',darkMagenta:'#800080',darkRed:'#800000',darkYellow:'#808000',darkGray:'#808080',lightGray:'#c0c0c0',black:'#000000'};

function rpr(e,o={}){if(!e)return o;
 for(const c of e.children){const n=c.localName,v=g(c,'val');
  if(n=='b')o.b=on(c);else if(n=='i')o.i=on(c);
  else if(n=='u')o.u=!!v&&v!='none';
  else if(n=='strike'||n=='dstrike')o.s=on(c);
  else if(n=='color')o.c=!v||v=='auto'?null:v;
  else if(n=='sz')o.sz=+v;
  else if(n=='rFonts'){const th=g(c,'asciiTheme')||g(c,'hAnsiTheme');o.f=g(c,'ascii')||g(c,'hAnsi')||(th?(/major/i.test(th)?TH.major:TH.minor):o.f)}
  else if(n=='highlight')o.hl=v=='none'?null:v;
  else if(n=='shd'){const fl=g(c,'fill');o.bg=!fl||fl=='auto'?null:fl}
  else if(n=='caps')o.caps=on(c);else if(n=='smallCaps')o.sc=on(c);
  else if(n=='vertAlign')o.va=v;else if(n=='vanish')o.hid=on(c)}
 return o}
function bdr(e){if(!e)return null;const o={};for(const c of e.children){const n={start:'left',end:'right'}[c.localName]||c.localName;o[n]={v:g(c,'val'),sz:+g(c,'sz')||4,c:g(c,'color')}}return o}
function ppr(e,o={}){if(!e)return o;
 for(const c of e.children){const n=c.localName,v=g(c,'val');
  if(n=='jc')o.jc=v;
  else if(n=='spacing'){const b=g(c,'before'),a=g(c,'after'),l=g(c,'line');if(b!=null)o.sb=+b;if(a!=null)o.sa=+a;if(l!=null){o.ln=+l;o.lr=g(c,'lineRule')||'auto'}}
  else if(n=='ind'){const l=g(c,'left')??g(c,'start'),r=g(c,'right')??g(c,'end'),f=g(c,'firstLine'),h=g(c,'hanging');if(l!=null)o.il=+l;if(r!=null)o.ir=+r;if(f!=null)o.fl=+f;if(h!=null)o.fl=-h}
  else if(n=='numPr'){o.num={id:g(k1(c,'numId'),'val'),lvl:+(g(k1(c,'ilvl'),'val')||0)}}
  else if(n=='pageBreakBefore')o.pbb=on(c);
  else if(n=='keepNext')o.kn=on(c);else if(n=='keepLines')o.kl=on(c);
  else if(n=='shd'){const fl=g(c,'fill');o.bg=!fl||fl=='auto'?null:fl}
  else if(n=='pBdr')o.bd=bdr(c)}
 return o}
function cmar(e){if(!e)return null;const o={};for(const c of e.children){const n={start:'left',end:'right'}[c.localName]||c.localName;o[n]=+g(c,'w')}return o}
function parseStyles(doc){const S={},dp={r:{},p:{}};if(!doc)return{S,dp,defP:null};
 const root=doc.documentElement,ds=k1(root,'docDefaults');
 if(ds){rpr(k1(k1(ds,'rPrDefault'),'rPr'),dp.r);ppr(k1(k1(ds,'pPrDefault'),'pPr'),dp.p)}
 let defP=null;
 for(const s of ks(root,'style')){const id=g(s,'styleId'),tp=k1(s,'tblPr');
  S[id]={t:g(s,'type'),name:g(k1(s,'name'),'val')||'',base:g(k1(s,'basedOn'),'val'),p:ppr(k1(s,'pPr')),r:rpr(k1(s,'rPr')),bd:tp?bdr(k1(tp,'tblBorders')):null,mar:tp?cmar(k1(tp,'tblCellMar')):null};
  if(g(s,'default')=='1'&&S[id].t=='paragraph')defP=id}
 return{S,dp,defP}}
const chain=(S,id)=>{const a=[];let n=0;while(id&&S[id]&&n++<25){a.unshift(S[id]);id=S[id].base}return a};
const resolve=(S,id,k)=>Object.assign({},...chain(S,id).map(s=>s[k]));
function parseNum(doc){const ab={},nums={};if(!doc)return{ab,nums};const root=doc.documentElement;
 for(const a of ks(root,'abstractNum')){const lv={};
  for(const l of ks(a,'lvl')){const pp=ppr(k1(l,'pPr'));lv[+g(l,'ilvl')]={fmt:g(k1(l,'numFmt'),'val')||'decimal',txt:g(k1(l,'lvlText'),'val')||'',st:+(g(k1(l,'start'),'val')||1),il:pp.il,fl:pp.fl,suff:g(k1(l,'suff'),'val')||'tab'}}
  ab[g(a,'abstractNumId')]=lv}
 for(const n of ks(root,'num')){const ov={};for(const o of ks(n,'lvlOverride')){const so=k1(o,'startOverride');if(so)ov[+g(o,'ilvl')]=+g(so,'val')}nums[g(n,'numId')]={ab:g(k1(n,'abstractNumId'),'val'),ov}}
 return{ab,nums}}
const romanN=n=>{let r='';[[1000,'M'],[900,'CM'],[500,'D'],[400,'CD'],[100,'C'],[90,'XC'],[50,'L'],[40,'XL'],[10,'X'],[9,'IX'],[5,'V'],[4,'IV'],[1,'I']].forEach(([v,s])=>{while(n>=v){r+=s;n-=v}});return r};
const letterN=n=>{let s='';while(n>0){n--;s=String.fromCharCode(97+n%26)+s;n=Math.floor(n/26)}return s};
function fmtNum(n,f){switch(f){case'lowerRoman':return romanN(n).toLowerCase();case'upperRoman':return romanN(n);case'lowerLetter':return letterN(n);case'upperLetter':return letterN(n).toUpperCase();case'decimalZero':return String(n).padStart(2,'0');case'none':return'';default:return String(n)}}
const bullet=t=>{if(!t)return'•';const c=t.charCodeAt(0);if(t=='o')return'◦';if(c==0xF0A7||c==0xF0A8)return'▪';if(c==0xF0D8)return'➢';if(c==0xF0FC)return'✓';if(c==0xF076)return'❖';if(c>=0xF000&&c<=0xF0FF)return'•';return t};
function bcss(b){if(!b||!b.v||b.v=='nil'||b.v=='none')return'none';const w=Math.max(1,Math.round(b.sz/6)),st=/double/.test(b.v)?'double':/dash/.test(b.v)?'dashed':/dot/.test(b.v)?'dotted':'solid';return`${st=='double'?Math.max(3,w):w}px ${st} ${b.c&&b.c!='auto'?'#'+b.c:'#000'}`}
function rcss(p,base){const d=[],df=(k,f)=>{if(p[k]!==base[k]){const s=f(p[k]);if(s)d.push(s)}};
 df('f',v=>v?`font-family:${fam(v)}`:'');df('sz',v=>v?`font-size:${f2(v*2/3)}px`:'');
 df('b',v=>`font-weight:${v?700:400}`);df('i',v=>`font-style:${v?'italic':'normal'}`);
 if(p.u!==base.u||p.s!==base.s){const t=[p.u&&'underline',p.s&&'line-through'].filter(Boolean).join(' ');d.push('text-decoration:'+(t||'none'))}
 df('c',v=>v?`color:#${v}`:'color:#000');df('hl',v=>v?`background-color:${HL[v]||v}`:'');df('bg',v=>v?`background-color:#${v}`:'');
 df('caps',v=>`text-transform:${v?'uppercase':'none'}`);df('sc',v=>`font-variant:${v?'small-caps':'normal'}`);
 df('va',v=>v=='superscript'?'vertical-align:super;font-size:smaller':v=='subscript'?'vertical-align:sub;font-size:smaller':'');
 return d.join(';')}

/* ---------- body conversion ---------- */
function runHtml(r,X,base){
 const rsid=g(k1(k1(r,'rPr'),'rStyle'),'val');
 const p=Object.assign({},base,rsid?resolve(X.S,rsid,'r'):{},rpr(k1(r,'rPr')));
 if(p.hid)return'';
 let out='';
 for(const c of r.children){const n=c.localName;
  if(n=='t')out+=esc(c.textContent);
  else if(n=='tab')out+='\t';
  else if(n=='br'){out+=g(c,'type')=='page'?'@@PB@@':'<br>'}
  else if(n=='cr')out+='<br>';
  else if(n=='noBreakHyphen')out+='\u2011';
  else if(n=='sym'){const ch=parseInt(g(c,'char')||'0',16);out+=bullet(String.fromCharCode(ch))}}
 const dr=r.getElementsByTagNameNS(W,'drawing')[0];
 if(dr){const ext=dr.getElementsByTagNameNS(WPD,'extent')[0],bl=dr.getElementsByTagNameNS(AD,'blip')[0];
  const src=bl&&X.img[bl.getAttributeNS(R,'embed')];
  if(src&&ext)out+=`<img src="${src}" width="${Math.round(+ext.getAttribute('cx')/9525)}" height="${Math.round(+ext.getAttribute('cy')/9525)}">`}
 if(!out)return'';
 const css=rcss(p,base);
 return css?`<span style="${css}">${out}</span>`:out}
function inline(node,X,base){let h='';
 for(const c of node.children){const n=c.localName;
  if(n=='r')h+=runHtml(c,X,base);
  else if(n=='hyperlink'){const rid=c.getAttributeNS(R,'id'),u=rid&&X.links[rid],inner=inline(c,X,base);h+=u?`<a href="${esc(u)}">${inner}</a>`:inner}
  else if(n=='sdt'){h+=inline(k1(c,'sdtContent')||c,X,base)}
  else if(/^(ins|fldSimple|smartTag|sdtContent|customXml)$/.test(n))h+=inline(c,X,base)}
 return h}
function para(p,X,top){
 const pp=k1(p,'pPr'),sid=g(k1(pp,'pStyle'),'val')||X.defP,sty=chain(X.S,sid),name=(sty.length?sty[sty.length-1].name:'').toLowerCase();
 const dir=ppr(pp),st=Object.assign({},X.dp.p,resolve(X.S,sid,'p'),dir);
 const base=Object.assign({},X.dp.r,resolve(X.S,sid,'r'));
 let inner=inline(p,X,base),label='';
 const lines=st.num&&st.num.id&&st.num.id!=='0'?st.num:null;let il=dir.il??st.il,fl=dir.fl??st.fl;
 if(lines){const num=X.num.nums[lines.id],lv=num&&X.num.ab[num.ab]&&X.num.ab[num.ab][lines.lvl];
  if(lv){const key=Object.keys(num.ov).length?'n'+lines.id:'a'+num.ab,c=X.cnt[key]||(X.cnt[key]=[]);
   c[lines.lvl]=c[lines.lvl]==null?(num.ov[lines.lvl]??lv.st):c[lines.lvl]+1;c.length=lines.lvl+1;
   let txt=lv.fmt=='bullet'?bullet(lv.txt):lv.txt.replace(/%(\d)/g,(m,d)=>{const L=X.num.ab[num.ab][d-1];return L?fmtNum(c[d-1]??L.st,L.fmt):''});
   il=dir.il??lv.il??st.il;fl=dir.fl??lv.fl??st.fl;
   const hang=Math.max(0,-(fl||0))/15;label=lv.fmt=='none'&&!txt?'':`<span style="display:inline-block;min-width:${f2(hang||24)}px;text-indent:0">${esc(txt)}${lv.suff=='space'?' ':''}</span>`}}
 const parts=inner.split('@@PB@@'),mark=rpr(k1(pp,'rPr'));
 const s=['margin:0',`padding:${f2((st.sb||0)/15)}px 0 ${f2((st.sa||0)/15)}px`];
 if(il)s.push(`margin-left:${f2(il/15)}px`);if(st.ir)s.push(`margin-right:${f2(st.ir/15)}px`);if(fl)s.push(`text-indent:${f2(fl/15)}px`);
 const j={center:'center',right:'right',end:'right',both:'justify',distribute:'justify'}[st.jc];s.push(`text-align:${j||'left'}`);
 const fsz=(base.sz||22)*2/3;
 if(st.ln){if(st.lr=='auto')s.push(`line-height:${f2(st.ln/240*lhf(base.f))}`);else{const px=st.ln/15;s.push(px>=fsz*1.2||st.lr=='exact'?`line-height:${f2(px)}px`:`line-height:${f2(lhf(base.f))}`)}}
 else s.push(`line-height:${lhf(base.f)}`);
 if(st.bg)s.push(`background-color:#${st.bg}`);
 if(st.bd)for(const k of['top','bottom','left','right'])if(st.bd[k]&&bcss(st.bd[k])!='none')s.push(`border-${k}:${bcss(st.bd[k])}`);
 const bc=rcss(base,{});if(bc)s.push(bc);
 const tag=/^title$/.test(name)?'h1':/^heading [1-3]$/.test(name)?'h'+name.slice(-1):'p';
 const mk=(h,first)=>{let css=s.join(';');if(!h&&mark.sz)css+=`;font-size:${f2(mark.sz*2/3)}px`;return`<${tag}${st.kn?' data-kn="1"':''}${st.kl?' data-kl="1"':''} style="${css}">${first?label:''}${h||'<br>'}</${tag}>`};
 let out='';
 if(st.pbb&&top&&X.started)out+='@@PBD@@';
 if(parts.length==1)out+=mk(parts[0],true);
 else parts.forEach((h,i)=>{if(h||(i==0&&false))out+=mk(h,i==0);if(i<parts.length-1)out+='@@PBD@@'});
 X.started=true;
 if(pp&&top){const sp=k1(pp,'sectPr');if(sp){X.secs.push(sp);out+='@@SB@@'}}
 return out}
function tstyle(S,sid){const a=chain(S,sid);return{bd:Object.assign({},...a.map(s=>s.bd||{})),mar:Object.assign({},...a.map(s=>s.mar||{}))}}
function table(t,X){
 const tp=k1(t,'tblPr'),ts=tstyle(X.S,g(k1(tp,'tblStyle'),'val')),bd=Object.assign({},ts.bd,bdr(k1(tp,'tblBorders'))),mar=Object.assign({left:108,right:108,top:0,bottom:0},ts.mar,cmar(k1(tp,'tblCellMar')));
 const grid=ks(k1(t,'tblGrid'),'gridCol').map(c=>+g(c,'w')/15);let total=grid.reduce((a,b)=>a+b,0);
 const tw=k1(tp,'tblW');if(tw&&g(tw,'type')=='dxa'&&+g(tw,'w')>0&&!grid.length)total=+g(tw,'w')/15;
 const rows=ks(t,'tr'),vm={},R_=[];let ncols=grid.length;
 rows.forEach((tr,ri)=>{let ci=+g(k1(k1(tr,'trPr'),'gridBefore'),'val')||0;const cells=[];
  for(const tc of ks(tr,'tc')){const tcp=k1(tc,'tcPr'),gs=+g(k1(tcp,'gridSpan'),'val')||1,vmn=k1(tcp,'vMerge'),vv=vmn?(g(vmn,'val')||'continue'):null;
   if(vv=='continue'){if(vm[ci])vm[ci].rs++;ci+=gs;continue}
   const cell={tc,tcp,ci,gs,ri,rs:1};if(vv=='restart')vm[ci]=cell;cells.push(cell);ci+=gs}
  ncols=Math.max(ncols,ci);R_.push({tr,cells})});
 let h='';
 R_.forEach(({tr,cells})=>{const th=k1(k1(tr,'trPr'),'trHeight'),hpx=th?+g(th,'val')/15:0;h+='<tr>';
  for(const c of cells){
   const w=f2(grid.slice(c.ci,c.ci+c.gs).reduce((a,b)=>a+b,0)||60),tb=bdr(k1(c.tcp,'tcBorders'))||{},last=c.ri+c.rs-1==R_.length-1;
   const side=(k,def)=>bcss(tb[k]!==undefined?tb[k]:def);
   const css=[`width:${w}px`,`border-top:${side('top',c.ri==0?bd.top:bd.insideH)}`,`border-bottom:${side('bottom',last?bd.bottom:bd.insideH)}`,`border-left:${side('left',c.ci==0?bd.left:bd.insideV)}`,`border-right:${side('right',c.ci+c.gs>=ncols?bd.right:bd.insideV)}`];
   const cm=Object.assign({},mar,cmar(k1(c.tcp,'tcMar')));css.push(`padding:${f2(cm.top/15)}px ${f2(cm.right/15)}px ${f2(cm.bottom/15)}px ${f2(cm.left/15)}px`);
   const sh=g(k1(c.tcp,'shd'),'fill');if(sh&&sh!='auto')css.push(`background-color:#${sh}`);
   const va=g(k1(c.tcp,'vAlign'),'val');if(va)css.push(`vertical-align:${va=='center'?'middle':va}`);
   if(hpx)css.push(`height:${f2(hpx)}px`);
   const inner=blocks(c.tc,X,false)||'<p style="margin:0"><br></p>';
   h+=`<td${c.gs>1?` colspan="${c.gs}"`:''}${c.rs>1?` rowspan="${c.rs}"`:''} style="${css.join(';')}">${inner}</td>`}
  h+='</tr>'});
 const jc=g(k1(tp,'jc'),'val'),ind=+g(k1(tp,'tblInd'),'w')||0;
 const ts2=[`border-collapse:collapse`,`table-layout:fixed`,`width:${f2(total)}px`];
 if(jc=='center')ts2.push('margin-left:auto;margin-right:auto');else if(ind)ts2.push(`margin-left:${f2(ind/15)}px`);
 return`<table style="${ts2.join(';')}">${h}</table>`}
function blocks(parent,X,top){let h='';
 for(const c of parent.children){const n=c.localName;
  if(n=='p')h+=para(c,X,top);
  else if(n=='tbl'){h+=table(c,X);X.started=true}
  else if(n=='sdt')h+=blocks(k1(c,'sdtContent')||c,X,top)}
 return h}

/* ---------- sections, footers, packaging ---------- */
async function importDocx(buf){
 const Z=await JSZip.loadAsync(buf),rd=async p=>{const f=Z.file(p);return f?f.async('string'):null},px=s=>s?new DOMParser().parseFromString(s,'application/xml'):null;
 const dX=px(await rd('word/document.xml'));if(!dX||!dX.documentElement||dX.getElementsByTagName('parsererror').length)throw Error('Not a valid .docx');
 const th=px(await rd('word/theme/theme1.xml'));
 if(th){const lt=t=>{const e=th.getElementsByTagNameNS(AD,t)[0],l=e&&e.getElementsByTagNameNS(AD,'latin')[0];return l&&l.getAttribute('typeface')};TH={minor:lt('minorFont')||'Calibri',major:lt('majorFont')||'Calibri'}}
 const sx=parseStyles(px(await rd('word/styles.xml'))),num=parseNum(px(await rd('word/numbering.xml')));
 const rels={},links={},img={},relX=px(await rd('word/_rels/document.xml.rels'));
 if(relX)for(const r of relX.documentElement.children){const id=r.getAttribute('Id'),tg=r.getAttribute('Target'),ty=(r.getAttribute('Type')||'').split('/').pop();rels[id]={tg,ty};if(ty=='hyperlink'&&r.getAttribute('TargetMode')=='External')links[id]=tg}
 for(const id in rels){if(rels[id].ty!='image')continue;const p='word/'+rels[id].tg.replace(/^\.?\//,''),f=Z.file(p);
  if(f){const ext=p.split('.').pop().toLowerCase(),mt={png:'image/png',jpg:'image/jpeg',jpeg:'image/jpeg',gif:'image/gif',svg:'image/svg+xml',bmp:'image/bmp'}[ext];
   if(mt)img[id]=`data:${mt};base64,`+await f.async('base64')}}
 const X={...sx,num,cnt:{},secs:[],links,img,started:false};
 const body=k1(dX.documentElement,'body');let html=blocks(body,X,true);
 const fin=k1(body,'sectPr');if(fin)X.secs.push(fin);
 /* footers -> page-number settings per section */
 const foot={};
 async function footerInfo(sp){const fr=ks(sp,'footerReference').find(e=>g(e,'type')=='default')||ks(sp,'footerReference')[0];if(!fr)return null;
  const rid=fr.getAttributeNS(R,'id');if(foot[rid])return foot[rid];const rel=rels[rid];const d=rel&&px(await rd('word/'+rel.tg.replace(/^\.?\//,'')));let info={show:false,align:'center'};
  if(d)for(const p of d.getElementsByTagNameNS(W,'p')){const txt=[...p.getElementsByTagNameNS(W,'instrText')].map(e=>e.textContent).join(' ')+[...p.getElementsByTagNameNS(W,'fldSimple')].map(e=>e.getAttributeNS(W,'instr')).join(' ');
   if(/\bPAGE\b/.test(txt)&&!/NUMPAGES|PAGEREF/.test(txt.replace(/\bPAGE\b/,''))||/\bPAGE\b/.test(txt)){
    let jc=g(k1(k1(p,'pPr'),'jc'),'val');
    if(!jc){const sid=g(k1(k1(p,'pPr'),'pStyle'),'val'),s=resolve(sx.S,sid,'p');jc=s.jc}
    if(!jc){let tabs=0;for(const e of p.getElementsByTagNameNS(W,'*')){if(e.localName=='tab'&&e.parentNode.localName=='r')tabs++;if(e.localName=='instrText'||e.localName=='fldSimple')break}jc=tabs>=2?'right':tabs==1?'center':'left'}
    info={show:true,align:{right:'right',end:'right',center:'center'}[jc]||'left'};break}}
  return foot[rid]=info}
 const cfgs=[];let prev={fmt:'dec',align:'center',show:false};
 for(let i=0;i<X.secs.length;i++){const sp=X.secs[i],pn=k1(sp,'pgNumType'),fi=await footerInfo(sp)||prev;
  const f=g(pn,'fmt'),fmt=f=='upperRoman'?'ru':f=='lowerRoman'?'rl':f?'dec':prev.fmt||'dec';
  cfgs.push({fmt:fi.show?fmt:'none',start:g(pn,'start')??(i==0?'1':''),align:fi.align,cont:g(k1(sp,'type'),'val')=='continuous'&&i>0,shown:fi.show,keep:fmt});
  prev={fmt,align:fi.align,show:fi.show}}
 if(!cfgs.length)cfgs.push({fmt:'none',start:'1',align:'center'});
 let mi=0;
 html=html.replace(/@@SB@@/g,()=>{const c=cfgs[++mi];return!c||c.cont?'':`<div class="sb" contenteditable="false" data-fmt="${c.fmt}" data-start="${c.start}" data-align="${c.align}"><span>Section break (next page)</span><i class="x" title="Remove break">✕</i></div>`});
 const nPB=(html.match(/@@PBD@@/g)||[]).length;
 html=html.replace(/@@PBD@@/g,'<div class="pb" contenteditable="false"><span>Page break</span><i class="x" title="Remove break">✕</i></div>');
 if(/<\/div>$/.test(html)||!html)html+='<p><br></p>';
 /* page setup from the first section */
 const sp0=X.secs[0],pg=k1(sp0,'pgSz'),pm=k1(sp0,'pgMar');let w=+g(pg,'w')||11906,h=+g(pg,'h')||16838;
 const land=g(pg,'orient')=='landscape'||w>h,m=n=>Math.round((+g(pm,n)||1440)/15);
 return{nPB,nSB:(html.match(/class="sb"/g)||[]).length,html,psz:[Math.round(Math.min(w,h)/15),Math.round(Math.max(w,h)/15)],land,mg:{t:m('top'),b:m('bottom'),l:m('left'),r:m('right')},cfg0:{fmt:cfgs[0].fmt,start:cfgs[0].start||'1',align:cfgs[0].align}}}
window.QuilloDocx={import:importDocx};
})();
