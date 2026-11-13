"""Generate a coordinate mockup, not a Minecraft screenshot."""
from pathlib import Path
from html import escape
import math
import random
import base64

root = Path(__file__).resolve().parents[1]
parts = []
def rect(x,y,w,h,color,stroke=None):
    parts.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="{color}"'+(f' stroke="{stroke}" stroke-width=".7"' if stroke else '')+'/>')
def text(x,y,s,color='#243746',size=7):
    parts.append(f'<text x="{x}" y="{y+7}" fill="{color}" font-size="{size}">{escape(s)}</text>')
def slot(x,y):
    rect(x-1,y-1,18,18,'#909a9c','#ffffff'); rect(x,y,16,16,'#bcc6c7')
def button(x,y,w,s,enabled=True):
    rect(x,y,w,16,'#355d6c' if enabled else '#78868b'); text(x+4,y+4,s,'white')
def icon(name,x,y,size=16):
    p=root/f'src/main/resources/assets/techreborn/textures/item/spacecraft/{name}.png'
    uri='data:image/png;base64,'+base64.b64encode(p.read_bytes()).decode()
    parts.append(f'<image href="{uri}" x="{x}" y="{y}" width="{size}" height="{size}" style="image-rendering:pixelated"/>')
def screen(miner=False):
    rect(0,0,500,318,'#d3dbdc','#637682')
    text(190,5,'太空电梯采矿模块' if miner else '太空电梯 · 任务控制',size=8)
    text(132,19,'UI预览 · 示例数据 · 未接入任务结算','#9c6a24')
    rect(8,28,118,190,'#e2e8e8','#a4b5ba');rect(398,28,94,190,'#e2e8e8','#a4b5ba')
    text(14,34,'月球',size=9); text(14,48,'荒芜行星','#637682');text(14,60,'任务等级 T1','#167f92')
    text(14,72,'半径 1737.4 km','#637682');text(14,82,'温度 220 K','#637682')
    text(14,91,'任务输入');text(14,155,'任务输出')
    for i in range(4 if miner else 9): slot(18+i%(2 if miner else 3)*18,102+i//(2 if miner else 3)*18)
    for i in range(6 if miner else 9): slot(18+i%3*18,166+i//3*18)
    icon('space_drone_mk1' if miner else 'space_probe_mk1',18,102);icon('_powered',18,102)
    rect(132,43,260,130 if miner else 174,'#050a16')
    rng=random.Random(2)
    for i in range(100):rect(rng.randrange(133,391),rng.randrange(44,172 if miner else 216),.6,.6,'#99bddd')
    parts.append('<defs><clipPath id="map'+str(miner)+'"><rect x="132" y="43" width="260" height="'+str(130 if miner else 174)+'"/></clipPath></defs>')
    parts.append('<g clip-path="url(#map'+str(miner)+')">')
    cy=109 if miner else 125
    for r in [34,59,92,132,180]:parts.append(f'<circle cx="220" cy="{cy}" r="{r}" fill="none" stroke="#345268" stroke-width=".6"/>')
    rect(211,cy-9,18,18,'#e3b252');rect(281,cy-4,10,10,'#627d64');rect(305,cy+9,7,7,'#cfceaa');rect(331,cy-25,10,10,'#b86944')
    parts.append(f'<circle cx="309" cy="{cy+12}" r="15" fill="none" stroke="#68becd" stroke-width=".8"/>')
    text(318,cy+16,'月球','#d6f8ff');parts.append('</g>')
    button(132,28,22,'<');text(160,32,'太阳系');button(370,28,22,'>')
    if miner:
        text(132,178,'可选产出池');text(292,178,'已选 3/3','#167f92');rect(132,190,260,27,'#182d3a')
        for i,(s,c) in enumerate([('铁',20),('铜',15),('金',10),('红石',30),('青金',10)]):
            rx=138+i*31;rect(rx-1,193,26,24,'#248b95' if i<3 else '#3e5360');text(rx+2,198,s,'white');text(rx+17,207,str(c),'white',6)
        text(404,34,'任务状态');text(404,49,'任务系统尚未接通','#aa5736');text(404,66,'固定据点采矿','#167f92')
        text(404,80,'产量 ×1.0');text(404,94,'耗时 ×1.0');text(404,119,'预览采矿目标');text(404,132,'sol:moon','#167f92')
        button(398,161,94,'设为预览目标');button(10,223,116,'启用自动采矿',False);text(10,243,'原配方加工仍保留','#637682')
        slot(90,102);icon('space_drone_mk1',398,262);icon('_powered',398,262)
    else:
        text(404,34,'模块 3/8')
        for i,s in enumerate(['组装模块','采矿模块','采矿模块']):text(404,49+i*16,s,'#167f92');text(404,57+i*16,f'{24+i*8} 64 128','#637682')
        button(10,223,116,'修建永久据点',False);text(10,243,'任务系统尚未接通','#637682');icon('space_lander_mk1',398,259);icon('_powered',398,259);text(419,263,'512 EU/t')
    button(10,265,22,'<');button(104,265,22,'>');text(38,269,'切换天体');text(10,287,'拖动 / 缩放 / 点击跟随','#637682');text(169,225,'物品栏')
    button(398,223,94,'预览阶段：永久据点' if miner else '预览阶段：已登陆');button(398,243,94,'预览器材：T1')
    for i in range(27):slot(169+i%9*18,236+i//9*18)
    for i in range(9):slot(169+i*18,294)
    for i,name in enumerate(['space_drone_mk1','space_probe_mk1','space_lander_mk1','core_mk1']):icon(name,169+i*18,294)
    rect(398,296,94,7,'#354651');rect(399,297,64,5,'#167f92');text(398,306,'1 000 000 EU')

screens=[]
for miner in [False,True]:
    parts=[]
    screen(miner)
    screens.append('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 500 318" width="1000" height="636">'+''.join(parts)+'</svg>')
html='<!doctype html><meta charset="utf-8"><title>Space terminal coordinate mockup</title><style>body{margin:0;padding:24px;background:#101c28;font-family:"Microsoft YaHei",sans-serif;color:#cee0e7}h2{font-size:18px;margin:0 0 12px}p{font-size:14px}svg{display:block;margin-bottom:28px}</style><h2>主机 / 采矿模块 · 坐标布局稿</h2><p>用于检查实际500×318布局坐标。浏览器字体和底框近似；不是Minecraft实机截图。</p>'+''.join(screens)
(root/'docs/space-terminal-preview.html').write_text(html,encoding='utf-8')
print('Generated docs/space-terminal-preview.html')
