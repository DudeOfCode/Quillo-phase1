/* Quillo image tools: tap an image -> resize / rotate handles + floating menu (copy, cut, delete, edit, replace, rotate, flip, align) */
(function(){
let sel=null,drag=null,imgClip=null,last='';
const IC={copy:'<rect x="9" y="9" width="11" height="11" rx="2"/><path d="M5 15V6a2 2 0 0 1 2-2h8"/>',cut:'<circle cx="6" cy="6" r="3"/><circle cx="6" cy="18" r="3"/><path d="M20 4 8.1 15.9M14.5 14.5 20 20M8.1 8.1 12 12"/>',del:'<path d="M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3"/>',edit:'<rect x="3" y="4" width="14" height="14" rx="2"/><path d="m3 15 4-4 4 4"/><path d="m16 21 5-5-2-2-5 5v2z"/>',repl:'<path d="M4 8h13l-3-3M20 16H7l3 3"/>',rot:'<path d="M20 12a8 8 0 1 1-3-6.2M20 4v5h-5"/>',flip:'<path d="M12 3v18M8 7 3 12l5 5zM16 7l5 5-5 5z"/>',al:'<path d="M4 6h16M4 10h10M4 14h16M4 18h10"/>',ac:'<path d="M4 6h16M7 10h10M4 14h16M7 18h10"/>',ar:'<path d="M4 6h16M10 10h10M4 14h16M10 18h10"/>'};
const ITEMS=[['copy','Copy'],['cut','Cut'],['del','Delete'],['edit','Edit image'],['repl','Replace'],['rot','Rotate'],['flip','Flip'],['al','Left'],['ac','Centre'],['ar','Right']];
const st=document.createElement('style');st.textContent=`#isel{position:fixed;inset:0;pointer-events:none;z-index:40;display:none}#isel.on{display:block}
.ibox{position:absolute;border:2px solid #2f6fed;box-sizing:border-box}.irl{position:absolute;width:2px;background:#2f6fed;transform-origin:0 0}
.ih{position:absolute;width:22px;height:22px;margin:-11px 0 0 -11px;border-radius:50%;background:#5a5d63;border:2px solid #fff;box-shadow:0 1px 5px #0007;pointer-events:auto;touch-action:none;box-sizing:border-box}.irot{background:#2f6fed}
#imenu{position:absolute;pointer-events:auto;background:#2f3135;color:#fff;border-radius:18px;padding:6px 8px;box-shadow:0 10px 30px #0009;display:grid;grid-template-columns:repeat(5,64px);gap:2px 0}
#imenu button{background:none;border:0;color:#fff;display:flex;flex-direction:column;align-items:center;gap:4px;font:12px system-ui,sans-serif;padding:8px 2px;border-radius:10px;cursor:pointer}
#imenu button svg{width:22px;height:22px;stroke:#fff;fill:none;stroke-width:1.8;stroke-linecap:round;stroke-linejoin:round}#imenu button:active{background:#ffffff26}
#iedit{position:fixed;inset:0;background:#000a;z-index:60;display:none;align-items:center;justify-content:center;padding:12px}#iedit.on{display:flex}
#iedit .card{background:#fff;color:#111;border-radius:18px;padding:14px;width:min(100%,380px);max-height:96vh;overflow:auto;box-shadow:0 14px 40px #0008}
#iedit canvas{display:block;margin:0 auto 10px;background:repeating-conic-gradient(#ddd 0 25%,#fff 0 50%) 0 0/14px 14px;border-radius:8px;max-width:100%}
#iedit label{display:grid;grid-template-columns:86px 1fr 40px;align-items:center;gap:6px;font:12px system-ui;margin:3px 0}#iedit input[type=range]{width:100%}
#iedit h4{margin:8px 0 2px;font:700 11px system-ui;letter-spacing:.5px;text-transform:uppercase;color:#667}
#iedit .btns{display:flex;gap:8px;margin-top:12px}#iedit .btns button{flex:1;height:40px;border-radius:10px;border:1px solid #cdd3df;background:#f3f5fa;font:600 14px system-ui;color:#111}#iedit .btns .pri{background:#2f6fed;border-color:#2f6fed;color:#fff}`;
document.head.appendChild(st);
const root=document.createElement('div');root.id='isel';
root.innerHTML='<div class="ibox"></div><div class="irl"></div>'+['nw','n','ne','e','se','s','sw','w'].map(h=>`<div class="ih" data-h="${h}"></div>`).join('')+'<div class="ih irot" data-h="rot"></div><div id="imenu">'+ITEMS.map(([a,l])=>`<button data-a="${a}"><svg viewBox="0 0 24 24">${IC[a]}</svg>${l}</button>`).join('')+'</div>';
document.body.appendChild(root);
const box=root.querySelector('.ibox'),rl=root.querySelector('.irl'),menu=root.querySelector('#imenu'),hs={};root.querySelectorAll('.ih').forEach(h=>hs[h.dataset.h]=h);
const DIR={nw:[-1,-1],n:[0,-1],ne:[1,-1],e:[1,0],se:[1,1],s:[0,1],sw:[-1,1],w:[-1,0]};
const rad=()=>(+sel.dataset.rot||0)*Math.PI/180;
function geom(){const PR=paper.getBoundingClientRect(),k=PR.width/paper.offsetWidth,r=sel.getBoundingClientRect();return{k,w:sel.offsetWidth*k,h:sel.offsetHeight*k,cx:r.left+r.width/2,cy:r.top+r.height/2,th:rad()}}
function tick(){if(!sel)return;if(!sel.isConnected){deselect();return}
 const g=geom(),key=[g.cx,g.cy,g.w,g.h,g.th,innerHeight].map(v=>Math.round(v*2)).join();
 if(key!==last){last=key;const c=Math.cos(g.th),s=Math.sin(g.th),P=(x,y)=>[g.cx+x*c-y*s,g.cy+x*s+y*c];let minY=1e9,maxY=-1e9;
  for(const h in DIR){const[x,y]=P(DIR[h][0]*g.w/2,DIR[h][1]*g.h/2);hs[h].style.left=x+'px';hs[h].style.top=y+'px';minY=Math.min(minY,y);maxY=Math.max(maxY,y)}
  const[rx,ry]=P(0,-g.h/2-36);hs.rot.style.left=rx+'px';hs.rot.style.top=ry+'px';minY=Math.min(minY,ry);
  Object.assign(box.style,{left:g.cx-g.w/2+'px',top:g.cy-g.h/2+'px',width:g.w+'px',height:g.h+'px',transform:`rotate(${g.th}rad)`});
  Object.assign(rl.style,{left:rx-1+'px',top:ry+'px',height:'36px',transform:`rotate(${g.th}rad)`});
  const mw=menu.offsetWidth||336,mh=menu.offsetHeight||130;let top=maxY+22;if(top+mh>innerHeight-6)top=minY-mh-22;if(top<6)top=Math.max(6,Math.min(innerHeight-mh-6,innerHeight/2-mh/2));
  menu.style.left=Math.max(6,Math.min(innerWidth-mw-6,g.cx-mw/2))+'px';menu.style.top=top+'px'}
 requestAnimationFrame(tick)}
function select(img){sel=img;last='';root.classList.add('on');requestAnimationFrame(tick)}
function deselect(){sel=null;root.classList.remove('on')}
function applyT(){const r=+sel.dataset.rot||0,fh=sel.dataset.fh?-1:1,fv=sel.dataset.fv?-1:1;sel.style.transform=(r||fh<0||fv<0)?`rotate(${r}deg) scale(${fh},${fv})`:''}
function setSize(w,h){w=Math.max(16,Math.round(w));h=Math.max(16,Math.round(h));Object.assign(sel.style,{width:w+'px',height:h+'px'});sel.setAttribute('width',w);sel.setAttribute('height',h)}
ed.addEventListener('click',e=>{if(e.target.tagName=='IMG')select(e.target);else deselect()});
root.addEventListener('pointerdown',e=>{const h=e.target.dataset&&e.target.dataset.h;if(!h||!sel)return;e.preventDefault();e.target.setPointerCapture(e.pointerId);
 drag={h,g:geom(),x0:e.clientX,y0:e.clientY,w0:sel.offsetWidth,h0:sel.offsetHeight}});
root.addEventListener('pointermove',e=>{if(!drag||!sel)return;const d=drag,g=d.g;
 if(d.h=='rot'){let a=Math.atan2(e.clientY-g.cy,e.clientX-g.cx)*180/Math.PI+90;a=((a+540)%360)-180;const n=Math.round(a/15)*15;if(Math.abs(a-n)<4)a=n;sel.dataset.rot=Math.round(a);applyT();return}
 const dx=e.clientX-d.x0,dy=e.clientY-d.y0,c=Math.cos(g.th),s=Math.sin(g.th),lx=(dx*c+dy*s)/g.k,ly=(-dx*s+dy*c)/g.k,[sx,sy]=DIR[d.h];
 let w=d.w0+sx*lx,h=d.h0+sy*ly;
 if(sx&&sy){const sc=Math.max(w/d.w0,h/d.h0);w=d.w0*sc;h=d.h0*sc}else if(!sx)w=d.w0;else h=d.h0;
 setSize(w,h)});
const end=()=>{if(drag){drag=null;sync()}};root.addEventListener('pointerup',end);root.addEventListener('pointercancel',end);
async function copyImg(){imgClip=sel.cloneNode(true);try{const b=await(await fetch(sel.src)).blob();await navigator.clipboard.write([new ClipboardItem({[b.type||'image/png']:b})]);toast('Image copied')}catch(e){toast('Copied – use Insert ▸ Paste image')}}
function delImg(){const p=sel.parentElement;sel.remove();deselect();if(p&&p!==ed&&!p.textContent&&!p.querySelector('img'))p.innerHTML='<br>';sync()}
function align(v){let p=sel.parentElement;if(p===ed){const n=document.createElement('p');sel.before(n);n.appendChild(sel);p=n}p.style.textAlign=v;sync()}
const fi=document.createElement('input');fi.type='file';fi.accept='image/*';fi.hidden=true;document.body.appendChild(fi);
fi.onchange=()=>{const f=fi.files[0];if(!f||!sel)return;const r=new FileReader();r.onload=()=>{const w=sel.offsetWidth,im=new Image();im.onload=()=>{sel.src=r.result;setSize(w,w*im.naturalHeight/im.naturalWidth);sync()};im.src=r.result};r.readAsDataURL(f);fi.value=''};
menu.addEventListener('mousedown',e=>e.preventDefault());
menu.addEventListener('click',async e=>{const a=e.target.closest('button')?.dataset.a;if(!a||!sel)return;
 if(a=='copy')copyImg();else if(a=='cut'){await copyImg();delImg()}else if(a=='del')delImg();
 else if(a=='edit')openEdit();else if(a=='repl')fi.click();
 else if(a=='rot'){let r=((+sel.dataset.rot||0)+90+540)%360-180;sel.dataset.rot=r;applyT();sync()}
 else if(a=='flip'){if(sel.dataset.fh)delete sel.dataset.fh;else sel.dataset.fh=1;applyT();sync()}
 else align({al:'left',ac:'center',ar:'right'}[a])});
/* ---- crop + adjust editor ---- */
const ed2=document.createElement('div');ed2.id='iedit';
const SL=[['cl','Crop left',0,45,0,'%'],['ct','Crop top',0,45,0,'%'],['cr','Crop right',0,45,0,'%'],['cb','Crop bottom',0,45,0,'%'],['br','Brightness',40,160,100,'%'],['co','Contrast',40,160,100,'%'],['sa','Saturation',0,200,100,'%']];
ed2.innerHTML='<div class="card"><canvas id="iecv"></canvas>'+SL.map(([k,l,mn,mx,v,u],i)=>(i==0?'<h4>Crop</h4>':i==4?'<h4>Adjust</h4>':'')+`<label>${l}<input type="range" id="ie-${k}" min="${mn}" max="${mx}" value="${v}"><span id="ie-${k}v">${v}${u}</span></label>`).join('')+'<div class="btns"><button id="ie-x">Cancel</button><button id="ie-a">Auto</button><button id="ie-r">Reset</button><button id="ie-ok" class="pri">Apply</button></div></div>';
document.body.appendChild(ed2);
const $i=id=>document.getElementById(id);let im=null;
const val=k=>+$i('ie-'+k).value,filt=()=>`brightness(${val('br')}%) contrast(${val('co')}%) saturate(${val('sa')}%)`;
function crop(){let l=val('cl'),t=val('ct'),r=val('cr'),b=val('cb');if(l+r>90){r=90-l}if(t+b>90){b=90-t}return{l:l/100,t:t/100,r:r/100,b:b/100}}
function draw(){if(!im)return;const cv=$i('iecv'),x=cv.getContext('2d'),W=cv.width,H=cv.height;x.clearRect(0,0,W,H);x.filter=filt();x.drawImage(im,0,0,W,H);x.filter='none';
 const c=crop(),L=c.l*W,T=c.t*H,R=W-c.r*W,B=H-c.b*H;x.fillStyle='rgba(0,0,0,.55)';x.fillRect(0,0,W,T);x.fillRect(0,B,W,H-B);x.fillRect(0,T,L,B-T);x.fillRect(R,T,W-R,B-T);x.strokeStyle='#fff';x.lineWidth=2;x.strokeRect(L,T,R-L,B-T);
 SL.forEach(([k,,,,,u])=>$i('ie-'+k+'v').textContent=val(k)+u)}
function openEdit(){im=new Image();im.onload=()=>{const mw=Math.min(innerWidth-60,340),mh=innerHeight*.34,sc=Math.min(mw/im.naturalWidth,mh/im.naturalHeight),cv=$i('iecv');cv.width=Math.round(im.naturalWidth*sc);cv.height=Math.round(im.naturalHeight*sc);reset();ed2.classList.add('on')};im.src=sel.src}
function reset(){SL.forEach(([k,,,,v])=>$i('ie-'+k).value=v);draw()}
SL.forEach(([k])=>$i('ie-'+k).oninput=draw);
$i('ie-r').onclick=reset;$i('ie-x').onclick=()=>ed2.classList.remove('on');
$i('ie-a').onclick=()=>{$i('ie-br').value=106;$i('ie-co').value=118;$i('ie-sa').value=112;draw()};
$i('ie-ok').onclick=()=>{const c=crop(),nw=im.naturalWidth,nh=im.naturalHeight,sw=nw*(1-c.l-c.r),sh=nh*(1-c.t-c.b),k=Math.min(1,2400/Math.max(sw,sh)),cv=document.createElement('canvas');
 cv.width=Math.max(1,Math.round(sw*k));cv.height=Math.max(1,Math.round(sh*k));const x=cv.getContext('2d');x.filter=filt();x.drawImage(im,nw*c.l,nh*c.t,sw,sh,0,0,cv.width,cv.height);
 const W0=sel.offsetWidth,H0=sel.offsetHeight,jpeg=/^data:image\/jpe?g/.test(sel.src);sel.src=cv.toDataURL(jpeg?'image/jpeg':'image/png',.92);setSize(W0*(1-c.l-c.r),H0*(1-c.t-c.b));ed2.classList.remove('on');sync()};
/* ---- paste image ---- */
ed.addEventListener('paste',e=>{const f=[...(e.clipboardData&&e.clipboardData.files||[])].find(x=>x.type.startsWith('image/'));if(!f)return;e.preventDefault();const r=new FileReader();r.onload=()=>{document.execCommand('insertImage',false,r.result);setTimeout(sync,60)};r.readAsDataURL(f)});
const b=document.getElementById('iBlank');
if(b){const n=document.createElement('button');n.id='iPaste';n.title='Paste the image you copied in Quillo';n.innerHTML='<svg viewBox="0 0 24 24"><rect x="8" y="3" width="8" height="4" rx="1"/><path d="M8 5H6a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2h-2"/></svg>Paste image';
 b.after(n);n.onclick=()=>{if(!imgClip)return toast('Copy an image first');restore();document.execCommand('insertHTML',false,imgClip.outerHTML);setTimeout(sync,60)}}
})();
