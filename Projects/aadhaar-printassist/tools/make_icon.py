"""Reproducible PrintAssist icon: original printer/paper artwork, no official emblems."""
from pathlib import Path
from PIL import Image,ImageDraw

root=Path(__file__).resolve().parent.parent
im=Image.new('RGBA',(1024,1024),(0,0,0,0));d=ImageDraw.Draw(im)
d.rounded_rectangle((32,32,992,992),radius=215,fill='#15384B')
d.rounded_rectangle((278,165,746,495),radius=35,fill='#FFFFFF')
d.rounded_rectangle((180,365,844,755),radius=75,fill='#47B8B0')
d.rounded_rectangle((245,450,779,583),radius=28,fill='#15384B')
d.ellipse((725,405,755,435),fill='#FFFFFF')
d.rounded_rectangle((278,550,746,859),radius=28,fill='#FFFFFF')
for y,w in [(635,300),(704,300),(773,195)]:
    d.rounded_rectangle((355,y,355+w,y+22),radius=11,fill='#15384B')
assets=root/'assets';assets.mkdir(exist_ok=True)
im.save(assets/'printassist.png')
im.save(assets/'printassist.ico',sizes=[(16,16),(24,24),(32,32),(48,48),(64,64),(128,128),(256,256)])
print('Created PNG and multi-resolution Windows ICO.')
