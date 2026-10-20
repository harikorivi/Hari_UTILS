"""Truthful, application-labelled print-copy status, outside document content."""
from PIL import Image, ImageDraw, ImageFont

STYLE = {
    'valid': ('#166534', 'SIGNATURE VERIFIED - original PDF'),
    'warning': ('#92400e', 'VERIFICATION INCOMPLETE'),
    'invalid': ('#b91c1c', 'VERIFICATION FAILED'),
    'unsigned': ('#475569', 'NO EMBEDDED DOCUMENT SIGNATURE'),
}


def draw_verified_tick(image, box):
    """Draw the verified viewer appearance on a print bitmap, without branding."""
    left, top, right, bottom = box
    width, height = right-left, bottom-top
    size = min(width*.55, height*.8)
    if size < 8:
        return
    x, y = left+(width-size)/2, top+(height-size)/2
    draw = ImageDraw.Draw(image)
    draw.line([(x,y+size*.5), (x+size*.34,y+size*.85), (x+size,y+size*.08)],
              fill='#008000', width=max(2,round(size*.1)))


def _font(size):
    for name in ('arial.ttf', '/System/Library/Fonts/Supplemental/Arial.ttf', 'DejaVuSans.ttf'):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            pass
    return ImageFont.load_default(size=size)


def add_verification_footer(image, report, dpi):
    """Return a new bitmap; never paint over source content or signature fields."""
    status = report.status if report.status in STYLE else 'warning'
    color, label = STYLE[status]
    size = max(8, min(round(10*dpi/72), image.width//32))
    font = _font(size)
    margin = max(8, round(8*dpi/72))
    icon_size = max(10, round(size*1.2))
    text_x = margin + icon_size + margin
    available = max(10, image.width-text_x-margin)
    measure = ImageDraw.Draw(image)
    lines = []
    for text in (
        'PrintAssist | '+label,
        ('Scope: original signed version; later changes excluded.' if report.scope == 'signed_revision'
         else 'Scope: uploaded original PDF, including revision checks.'),
        'Checked UTC: '+report.checked_at,
        'Original SHA-256: '+report.original_sha256,
        'Print copy only. This copy has no embedded digital signature.',
    ):
        line = ''
        for char in text:
            if line and measure.textlength(line+char, font=font)>available:
                lines.append(line)
                line = ''
            line += char
        lines.append(line)
    line_height = round(size*1.5)
    height = margin*2+line_height*len(lines)
    output = Image.new('RGB',(image.width,image.height+height),'white')
    output.paste(image,(0,0))
    draw = ImageDraw.Draw(output)
    top = image.height
    draw.line((margin,top,image.width-margin,top),fill=color,width=max(1,round(dpi/100)))
    x, y = margin, top+margin
    pen = max(2,round(size/5))
    if status == 'valid':
        draw.line([(x,y+icon_size*.5),(x+icon_size*.35,y+icon_size*.85),
                   (x+icon_size,y+icon_size*.1)],fill=color,width=pen)
    elif status == 'invalid':
        draw.line((x,y,x+icon_size,y+icon_size),fill=color,width=pen)
        draw.line((x+icon_size,y,x,y+icon_size),fill=color,width=pen)
    else:
        draw.ellipse((x,y,x+icon_size,y+icon_size),outline=color,width=pen)
        draw.text((x+icon_size*.35,y-icon_size*.1),'!',font=font,fill=color)
    for i,line in enumerate(lines):
        draw.text((text_x,top+margin+i*line_height),line,font=font,fill=color if i==0 else '#334155')
    return output
