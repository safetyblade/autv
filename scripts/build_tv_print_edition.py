#!/usr/bin/env python3
"""AUTV classic TV guide — genuine A5 reading pages and A4 duplex-imposed PDF.

A4 landscape, print both sides flip SHORT edge, actual size, no further n-up or
'Booklet' driver option. A5 reading PDF and editable A5 Word copy are also built.
Official bundled provider logos and exact app icon geometry are reused.
"""
from __future__ import annotations
import csv
from collections import Counter
from datetime import datetime
from math import ceil
from pathlib import Path
from zoneinfo import ZoneInfo

from docx import Document
from docx.enum.section import WD_ORIENT
from docx.shared import Cm, Pt, RGBColor
from pypdf import PdfReader, PdfWriter, Transformation
from reportlab.lib.colors import HexColor, Color
from reportlab.lib.pagesizes import A4, A5, landscape
from reportlab.pdfbase.pdfmetrics import stringWidth
from reportlab.pdfgen import canvas

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "docs"
INK = HexColor("#070A10")
GLASS = HexColor("#181C24")
SILVER = HexColor("#CEC4B4")
WARM = HexColor("#F2EFE9")
MUTED = HexColor("#686B72")
LIGHT = HexColor("#F7F5F0")
LINE = HexColor("#DDD8D0")
ISSUE_DATE = datetime.now(ZoneInfo("Australia/Melbourne")).strftime("%d %B %Y")
SOURCES = {
    "9Now": ROOT / "android/app/src/main/res/drawable-nodpi/provider_nine.png",
    "7plus": ROOT / "android/app/src/main/res/drawable-nodpi/provider_seven.png",
    "10": ROOT / "android/app/src/main/res/drawable-nodpi/provider_ten.png",
    "SBS": ROOT / "android/app/src/main/res/drawable-nodpi/provider_sbs.png",
}
MAX_PER_PAGE = 24
PAGE_W, PAGE_H = landscape(A4)[0]/2, landscape(A4)[1]  # exact A4 half-width: 148.5 x 210 mm

def text_fit(value: str, max_w: float, font="Helvetica", size=8.2) -> str:
    value = value.replace("\n", " ").strip()
    if stringWidth(value, font, size) <= max_w:
        return value
    while value and stringWidth(value + "…", font, size) > max_w:
        value = value[:-1]
    return value.rstrip() + "…"

def read_catalogue():
    with (ROOT / "channel_guide.csv").open(encoding="utf-8-sig", newline="") as handle:
        rows = [r for r in csv.DictReader(handle) if r["Status"].casefold().strip() == "active"]
    rows.sort(key=lambda r: int(r["Channel number"]))
    nums = [int(x["Channel number"]) for x in rows]
    assert len(nums) == len(set(nums)), "Duplicate channel numbers"
    published = set()
    import re
    rx = re.compile(r'(?:^|\s)tvg-chno="(\d+)"')
    for line in (ROOT / "playlist.m3u").read_text(encoding="utf-8-sig").splitlines():
        if line.startswith("#EXTINF:"):
            m = rx.search(line)
            if m: published.add(int(m.group(1)))
    genres = list(dict.fromkeys(row["Genre"] for row in rows))
    grouped = [(g, [r for r in rows if r["Genre"] == g]) for g in genres]
    return rows, published, grouped

def page_plan(rows, published, grouped):
    pages = [
        {"kind":"cover", "label":"AUTV"},
        {"kind":"intro", "label":"Getting started"},
        {"kind":"contents", "label":"Contents"},
        {"kind":"providers", "label":"Australian TV"},
    ]
    for genre, entries in grouped:
        for start in range(0, len(entries), MAX_PER_PAGE):
            pages.append({"kind":"directory", "label":genre,
                          "rows": entries[start:start+MAX_PER_PAGE],
                          "part": 1+start//MAX_PER_PAGE,
                          "parts":ceil(len(entries)/MAX_PER_PAGE)})
    # Always close with a branded back cover and keep a four-page signature.
    while (len(pages)+1)%4:
        pages.append({"kind":"notes", "label":"Notes"})
    pages.append({"kind":"back", "label":"Back cover"})
    assert len(pages)%4==0
    flat = [int(x["Channel number"]) for p in pages if p["kind"]=="directory" for x in p["rows"]]
    assert flat == [int(r["Channel number"]) for r in rows], "Directory order mismatch or omission"
    return pages

def rect(c, x, y, w, h, color, radius=0):
    c.setFillColor(color)
    if radius: c.roundRect(x,y,w,h,radius,fill=1,stroke=0)
    else: c.rect(x,y,w,h,fill=1,stroke=0)

def label(c, s, x, y, size=10, font="Helvetica", color=INK):
    c.setFillColor(color);c.setFont(font,size);c.drawString(x,y,s)

def icon(c, x, y, size):
    """Scaled geometry from drawable/app_icon.xml (screen + play glyph)."""
    rect(c,x,y,size,size,INK,8)
    k=size/108
    rect(c,x+20*k,y+30*k,68*k,48*k,SILVER)
    rect(c,x+28*k,y+38*k,52*k,32*k,INK)
    c.setFillColor(SILVER)
    p=c.beginPath()
    p.moveTo(x+46*k,y+42*k);p.lineTo(x+65*k,y+54*k);p.lineTo(x+46*k,y+66*k);p.close()
    c.drawPath(p,fill=1,stroke=0)

def provider_row(c, x, y, w=74, h=59):
    rect(c,x,y,w,h,GLASS,7)
    # Real icon files carried by AUTV; ABC is a plain text panel until an approved asset exists.
    slots=[("9Now",0),("7plus",1),("10",2),("SBS",3),("ABC",4)]
    imgw=35
    for name, idx in slots:
        xx=x+9+idx*(w-18)/5
        file=SOURCES.get(name)
        if file and file.is_file():
            c.drawImage(str(file),xx,y+20,width=28,height=28,mask="auto",preserveAspectRatio=True,anchor="c")
        else:
            label(c,"ABC",xx+1,y+27,8.4,"Helvetica-Bold",WARM)
    label(c,"AUSTRALIAN TV",x+8,y+7,7,"Helvetica-Bold",SILVER)

def top_header(c, pno, title, kind):
    rect(c,0,PAGE_H-36,PAGE_W,36,INK)
    icon(c,13,PAGE_H-31,24)
    label(c,"AUTV  /  "+title.upper(),46,PAGE_H-23,10,"Helvetica-Bold",WARM)
    c.setStrokeColor(LINE);c.setLineWidth(.5);c.line(17,26,PAGE_W-17,26)
    label(c,"AUTV  •  "+ISSUE_DATE,17,15,6.4,"Helvetica",MUTED)
    c.setFillColor(MUTED);c.setFont("Helvetica",7);c.drawRightString(PAGE_W-17,15,str(pno))

def dark_page(c):
    rect(c,0,0,PAGE_W,PAGE_H,INK)

def cover(c, row_count, published_count):
    dark_page(c)
    rect(c,0,PAGE_H-11,PAGE_W,11,SILVER)
    icon(c,28,PAGE_H-144,92)
    label(c,"AUTV",141,PAGE_H-83,39,"Helvetica-Bold",WARM)
    label(c,"THE TV GUIDE",30,PAGE_H-192,28,"Helvetica-Bold",SILVER)
    label(c,"YOUR COMPLETE CHANNEL DIRECTORY",31,PAGE_H-217,9,"Helvetica-Bold",WARM)
    c.setStrokeColor(SILVER);c.setLineWidth(1);c.line(30,PAGE_H-238,PAGE_W-30,PAGE_H-238)
    lines=["THE WORLD OF TV,","ALL IN ONE PLACE"]
    for k,line in enumerate(lines):
        label(c,line,30,PAGE_H-280-27*k,19.7,"Helvetica-Bold",WARM)
    label(c,"Live sport, classic television,",30,PAGE_H-353,11,"Helvetica",SILVER)
    label(c,"movies, comedy and more.",30,PAGE_H-371,11,"Helvetica",SILVER)
    provider_row(c,30,PAGE_H-458,PAGE_W-60,65)
    label(c,f"{row_count} CHANNELS  •  {published_count} STREAM ENTRIES",31,66,8.2,"Helvetica-Bold",WARM)
    label(c,"PRINT EDITION  /  "+ISSUE_DATE.upper(),31,46,8,"Helvetica",SILVER)

def intro(c, pno, count):
    top_header(c,pno,"Start here","intro")
    label(c,"WELCOME TO AUTV",22,PAGE_H-76,22,"Helvetica-Bold",INK)
    label(c,"A familiar TV guide for your Android TV.",22,PAGE_H-97,10,"Helvetica",MUTED)
    items=[
      ("01","OPEN THE GUIDE","Use the remote arrows. Press OK to tune."),
      ("02","SAVE A FAVOURITE","Long-press OK on a channel to save it."),
      ("03","REFRESH CHANNELS","Settings → Refresh channels and TV guide."),
      ("04","AUSTRALIAN TV","Link supported providers in Settings."),
      ("05","CHOOSE A STARTING CHANNEL","Set your preferred startup channel in Settings."),
    ]
    y=PAGE_H-143
    for n,head,desc in items:
        rect(c,22,y-49,PAGE_W-44,59,LIGHT,6)
        label(c,n,32,y-14,14,"Helvetica-Bold",MUTED)
        label(c,head,75,y-9,10.5,"Helvetica-Bold",INK)
        label(c,text_fit(desc,PAGE_W-100,size=9),75,y-28,9,"Helvetica",MUTED)
        y-=72
    label(c,f"THIS EDITION: {count} CONFIGURED CHANNELS",22,68,9,"Helvetica-Bold",INK)
    label(c,"Channel availability may change between editions.",22,51,8,"Helvetica",MUTED)

def contents(c,pno,grouped,pages):
    top_header(c,pno,"Contents","contents")
    label(c,"WHAT'S INSIDE",22,PAGE_H-80,22,"Helvetica-Bold",INK)
    y=PAGE_H-118
    for name,rows in grouped:
        pos=next(i+1 for i,p in enumerate(pages) if p["kind"]=="directory" and p["label"]==name)
        rect(c,22,y-11,PAGE_W-44,31,LIGHT if int((PAGE_H-y)/35)%2==0 else Color(1,1,1),0)
        label(c,name,31,y+2,11,"Helvetica-Bold",INK)
        label(c,f"{len(rows)} channels",31,y-10,7.4,"Helvetica",MUTED)
        c.setFont("Helvetica-Bold",10);c.setFillColor(INK);c.drawRightString(PAGE_W-32,y,str(pos))
        y-=35
    label(c,"PAGE NUMBERS REFER TO THE READING EDITION.",22,49,7.6,"Helvetica",MUTED)

def providers(c,pno):
    top_header(c,pno,"Australian TV","providers")
    label(c,"YOUR NETWORKS",22,PAGE_H-82,22,"Helvetica-Bold",INK)
    label(c,"Free-to-air services within the AUTV app",22,PAGE_H-104,9.7,"Helvetica",MUTED)
    items=[("9Now","Nine Network"),("7plus","Seven Network"),("10","Network 10"),("SBS","SBS On Demand"),("ABC","ABC iview")]
    y=PAGE_H-155
    for name,desc in items:
        rect(c,22,y-56,PAGE_W-44,69,LIGHT,5)
        im=SOURCES.get(name)
        if im and im.is_file():c.drawImage(str(im),32,y-43,width=46,height=46,preserveAspectRatio=True,mask="auto")
        else:
            rect(c,32,y-43,46,46,INK,4)
            label(c,"ABC",38,y-18,12,"Helvetica-Bold",WARM)
        label(c,name,94,y-6,14,"Helvetica-Bold",INK)
        label(c,desc,94,y-25,9,"Helvetica",MUTED)
        y-=83
    label(c,"Availability depends on the installed app and account access.",22,45,7.9,"Helvetica",MUTED)

def directory(c,pno,page,published):
    genre=page["label"];top_header(c,pno,genre,"directory")
    label(c,genre.upper(),20,PAGE_H-72,18,"Helvetica-Bold",INK)
    label(c,f"CHANNEL DIRECTORY  •  {page['part']} OF {page['parts']}",20,PAGE_H-88,8,"Helvetica",MUTED)
    rows=page["rows"]
    y=PAGE_H-111
    step=19.0
    for i,row in enumerate(rows):
        number=int(row["Channel number"])
        rect(c,20,y-12,PAGE_W-40,step,LIGHT if i%2==0 else Color(1,1,1),0)
        label(c,str(number),26,y-4,9.3,"Helvetica-Bold",INK)
        name=text_fit(row["Channel name"],PAGE_W-125,size=9.3)
        label(c,name,74,y-4,9.3,"Helvetica",INK)
        if number not in published:
            c.setFillColor(MUTED);c.setFont("Helvetica-Bold",6.1)
            c.drawRightString(PAGE_W-25,y-3,"LISTED")
        y-=step
    label(c,"Numbers follow AUTV's channel directory.",20,41,7.8,"Helvetica",MUTED)
    label(c,"Listed does not guarantee live playback in Australia.",20,31,7.3,"Helvetica",MUTED)

def notes(c,pno):
    top_header(c,pno,"Notes","notes")
    label(c,"YOUR TV NOTES",22,PAGE_H-83,21,"Helvetica-Bold",INK)
    label(c,"Favourites, programmes and channels to revisit",22,PAGE_H-104,9,"Helvetica",MUTED)
    c.setStrokeColor(LINE)
    for i in range(17):
        y=PAGE_H-142-i*25
        c.line(26,y,PAGE_W-26,y)

def back(c,count):
    dark_page(c)
    rect(c,0,0,PAGE_W,12,SILVER)
    icon(c,30,PAGE_H-166,102)
    label(c,"AUTV",155,PAGE_H-100,39,"Helvetica-Bold",WARM)
    label(c,"YOUR TV. YOUR GUIDE.",30,PAGE_H-215,20,"Helvetica-Bold",SILVER)
    label(c,"Refresh your channels in Settings.",30,PAGE_H-248,10.5,"Helvetica",WARM)
    c.setStrokeColor(SILVER);c.line(30,90,PAGE_W-30,90)
    label(c,f"{count} CHANNELS  /  {ISSUE_DATE.upper()}",30,67,9,"Helvetica-Bold",WARM)
    label(c,"AUTV  •  ANDROID TV",30,47,8.5,"Helvetica",SILVER)

def build_pdf(pages,rows,published,grouped):
    OUT.mkdir(exist_ok=True,parents=True)
    path=OUT/"AUTV_TV_Booklet_Reader.pdf"
    c=canvas.Canvas(str(path),pagesize=A5,pageCompression=1)
    pub=len({int(r["Channel number"]) for r in rows}&published)
    for i,page in enumerate(pages,1):
        kind=page["kind"]
        if kind=="cover":cover(c,len(rows),pub)
        elif kind=="intro":intro(c,i,len(rows))
        elif kind=="contents":contents(c,i,grouped,pages)
        elif kind=="providers":providers(c,i)
        elif kind=="directory":directory(c,i,page,published)
        elif kind=="notes":notes(c,i)
        else:back(c,len(rows))
        c.showPage()
    c.save()
    return path

def impose_pdf(reader_path,pages):
    src=PdfReader(str(reader_path))
    n=len(src.pages)
    assert n==len(pages) and n%4==0
    w,h=landscape(A4)
    assert abs(2*PAGE_W-w)<1 and abs(PAGE_H-h)<1
    out=PdfWriter()
    order=[]
    for sheet in range(n//4):
        for pair in [(n-2*sheet,1+2*sheet),(2+2*sheet,n-1-2*sheet)]:
            blank=out.add_blank_page(width=w,height=h)
            for x, page_number in enumerate(pair):
                blank.merge_transformed_page(src.pages[page_number-1],Transformation().translate(tx=x*(w/2),ty=0))
            order.append(pair)
    flattened=[i for p in order for i in p]
    assert sorted(flattened)==list(range(1,n+1))
    target=OUT/"AUTV_TV_Booklet_Print.pdf"
    with target.open("wb") as f:out.write(f)
    return target,order

def write_word(pages):
    """Editable A5 reading order; the imposed PDF is the final print master."""
    d=Document()
    sec=d.sections[0]
    sec.orientation=WD_ORIENT.PORTRAIT
    sec.page_width=Cm(14.85);sec.page_height=Cm(21)
    sec.top_margin=Cm(1.1);sec.bottom_margin=Cm(1.1)
    sec.left_margin=Cm(1.05);sec.right_margin=Cm(1.05)
    normal=d.styles["Normal"];normal.font.name="Aptos";normal.font.size=Pt(9)
    for i,p in enumerate(pages):
        if i:d.add_page_break()
        if p["kind"]=="cover":
            d.add_heading("AUTV",0);d.add_heading("THE TV GUIDE",1)
            d.add_heading("YOUR COMPLETE CHANNEL DIRECTORY",2)
            d.add_heading("THE WORLD OF TV, ALL IN ONE PLACE",1)
            d.add_paragraph("Live sport, classic television, movies, comedy and more.")
            for name,path in SOURCES.items():
                if path.is_file():
                    d.add_picture(str(path),width=Cm(1.1))
            d.add_paragraph("ABC iview")
            d.add_paragraph(ISSUE_DATE)
        elif p["kind"]=="directory":
            d.add_heading(p["label"],1)
            for row in p["rows"]:
                x=d.add_paragraph(style="Normal")
                x.paragraph_format.space_after=Pt(3)
                x.add_run(row["Channel number"]+"   ").bold=True
                x.add_run(row["Channel name"])
        elif p["kind"]=="intro":
            d.add_heading("Using AUTV",1)
            d.add_paragraph("Settings → Refresh channels and TV guide")
            d.add_paragraph("Use remote arrows to browse, OK to tune, long-press OK to favourite.")
        elif p["kind"]=="contents":
            d.add_heading("Contents",1)
            for pp in pages:
                if pp["kind"]=="directory" and pp["part"]==1:
                    d.add_paragraph(pp["label"])
        elif p["kind"]=="providers":
            d.add_heading("Australian TV",1)
            for name,path in SOURCES.items():
                if path.is_file():d.add_picture(str(path),width=Cm(1.1))
                d.add_paragraph(name)
            d.add_paragraph("ABC iview")
        elif p["kind"]=="notes": d.add_heading("Notes",1)
        else:d.add_heading("AUTV — Your TV. Your guide.",1)
    path=OUT/"AUTV_TV_Booklet.docx"
    d.save(path)
    return path

def main():
    rows,published,grouped=read_catalogue()
    pages=page_plan(rows,published,grouped)
    reader=build_pdf(pages,rows,published,grouped)
    imposed,order=impose_pdf(reader,pages)
    word=write_word(pages)
    audit=OUT/"AUTV_TV_Booklet_Print_Proof.txt"
    audit.write_text(
        f"AUTV print edition / {ISSUE_DATE}\nA5 pages: {len(pages)}\n"
        f"A4 landscape sheets: {len(order)//2}\n"
        "Print A4 landscape / two-sided / FLIP SHORT EDGE / actual size.\n"
        "Do NOT enable printer Booklet or 2 pages per sheet: PDF is already imposed.\n"
        "Fold all sheets together at their centre and staple through the spine.\n"
        + "\n".join(f"Sheet {i//2+1} {'front' if i%2==0 else 'back'}: {a} | {b}" for i,(a,b) in enumerate(order))+"\n",
        encoding="utf-8"
    )
    print(f"PASS — {len(rows)} channels, {len(pages)} A5 pages, {len(order)//2} A4 sheets; outputs {reader.name}, {imposed.name}, {word.name}")

if __name__=="__main__":
    main()
