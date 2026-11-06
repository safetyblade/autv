#!/usr/bin/env python3
"""AUTV approved-layout booklet generator. Data changes; visual template does not.

Outputs a 36-page A5 reader (for the current catalogue) and correctly imposed
A4-landscape duplex print sheets. No streaming URLs are read or embedded.
"""
import csv
import re
from collections import Counter
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo

from reportlab.lib import colors
from reportlab.lib.pagesizes import A4, A5, landscape
from reportlab.lib.units import mm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.pdfgen import canvas
from pypdf import PdfReader, PdfWriter

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/"docs"
CSV=ROOT/"channel_guide.csv"
NATIVE=ROOT/"android/app/src/main/java/dev/prestwich/autv/provider/AustralianLiveChannels.kt"
READER=OUT/"AUTV_TV_Booklet_Reader.pdf"
PRINT=OUT/"AUTV_TV_Booklet_Print.pdf"
DATE=datetime.now(ZoneInfo("Australia/Melbourne")).strftime("%d %B %Y").upper()
CATEGORIES=["News","Sport","Movies","Game Shows","Crime","Comedy","Entertainment",
            "Reality & Lifestyle","Factual","Kids & Animation","Music"]
TAGLINES={
 "News":"Stay informed.",
 "Sport":"Every match. Every moment.",
 "Movies":"Something worth watching.",
 "Game Shows":"Play along.",
 "Crime":"Stories that keep you guessing.",
 "Comedy":"A little more laughter.",
 "Entertainment":"Old favourites. New discoveries.",
 "Reality & Lifestyle":"Find your next obsession.",
 "Factual":"Explore something new.",
 "Kids & Animation":"For the young and young at heart.",
 "Music":"Turn it up.",
}
NAVY=colors.HexColor("#0B1828")
DARK=colors.HexColor("#102338")
SEA=colors.HexColor("#277A89")
PAPER=colors.HexColor("#F6F2EB")
WARM=colors.HexColor("#D5CAB8")
MUTED=colors.HexColor("#728390")
WHITE=colors.white
A5W,A5H=A5
FONT="/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
BOLD_FONT="/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
if Path(FONT).exists() and Path(BOLD_FONT).exists():
    pdfmetrics.registerFont(TTFont("AUTV",FONT))
    pdfmetrics.registerFont(TTFont("AUTVBold",BOLD_FONT))
    REG,BOLD="AUTV","AUTVBold"
else:
    REG,BOLD="Helvetica","Helvetica-Bold"

def sources():
    with CSV.open(encoding="utf-8-sig",newline="") as f:
        records=[r for r in csv.DictReader(f) if r["Status"].strip().casefold()=="active"]
    nums=[int(r["Channel number"]) for r in records]
    if len(nums)!=len(set(nums)) or len(records)<600:
        raise ValueError("Invalid or incomplete approved channel catalogue")
    if set(r["Genre"] for r in records)-set(CATEGORIES):
        raise ValueError("Unknown catalogue genre")
    rows={genre:sorted([(int(r["Channel number"]),r["Channel name"]) for r in records if r["Genre"]==genre])
          for genre in CATEGORIES}
    native=re.findall(r'LiveChannel\(\s*(\d+)\s*,\s*"([^"]+)"\s*,\s*"[^"]+"\s*,\s*"([^"]+)"',NATIVE.read_text(encoding="utf-8"),re.S)
    native=sorted([(int(n),name,provider) for n,name,provider in native])
    if len(native)<6 or len({x[0] for x in native})!=len(native):
        raise ValueError("Missing native Free-To-Air TV catalogue")
    return rows,native,len(records)

def text(c,x,y,string,size=9,color=NAVY,bold=False):
    c.setFillColor(color);c.setFont(BOLD if bold else REG,size)
    c.drawString(x,y,string)

def right(c,x,y,string,size=9,color=NAVY,bold=False):
    c.setFillColor(color);c.setFont(BOLD if bold else REG,size)
    c.drawRightString(x,y,string)

def start(c,section,page,total,dark=False):
    c.setFillColor(NAVY if dark else PAPER)
    c.rect(0,0,A5W,A5H,stroke=0,fill=1)
    c.setStrokeColor(SEA if dark else WARM);c.setLineWidth(.7)
    c.line(17*mm,A5H-20*mm,A5W-17*mm,A5H-20*mm)
    text(c,17*mm,A5H-16*mm,"AUTV / "+section.upper(),7,WHITE if dark else MUTED,True)
    c.line(17*mm,18*mm,A5W-17*mm,18*mm)
    text(c,17*mm,12.5*mm,"AUTV  •  "+DATE,6.3,WHITE if dark else MUTED)
    right(c,A5W-17*mm,12.5*mm,str(page),7,WHITE if dark else MUTED,True)

def end(c):c.showPage()

def wrap_name(value,maxwidth,size=9):
    # Never clip longer channel names; shrink rather than changing row height.
    while pdfmetrics.stringWidth(value,REG,size)>maxwidth and size>6.2:
        size-=.3
    return size

def category_page(c,genre,entries,part,parts,page,total):
    start(c,genre,page,total)
    text(c,17*mm,A5H-34*mm,genre.upper(),17 if len(genre)<15 else 14,NAVY,True)
    text(c,17*mm,A5H-42*mm,TAGLINES[genre],7.8,SEA)
    right(c,A5W-17*mm,A5H-49*mm,f"CHANNEL DIRECTORY  •  {part} OF {parts}",6.3,MUTED)
    top=A5H-57*mm
    for i,(number,name) in enumerate(entries):
        y=top-i*5.35*mm
        if i%2==0:
            c.setFillColor(colors.HexColor("#EBE8E1"))
            c.rect(16*mm,y-2.0*mm,A5W-32*mm,5.35*mm,stroke=0,fill=1)
        text(c,18*mm,y,str(number),8,NAVY,True)
        text(c,35*mm,y,name,wrap_name(name,A5W-54*mm,8.2),NAVY)
    text(c,17*mm,24*mm,"Find it by number. Make it a favourite.",6.9,SEA,True)
    end(c)

def cover(c,count):
    start(c,"YOUR CHANNELS, ONE GUIDE",1,0,True)
    text(c,17*mm,A5H-62*mm,"THE WORLD OF TV,",20,WHITE,True)
    text(c,17*mm,A5H-75*mm,"ALL IN ONE PLACE.",20,WHITE,True)
    c.setStrokeColor(SEA);c.setLineWidth(4);c.line(17*mm,A5H-85*mm,A5W-17*mm,A5H-85*mm)
    text(c,17*mm,A5H-112*mm,"SPORT  •  MOVIES  •  COMEDY",9,WARM,True)
    text(c,17*mm,A5H-120*mm,"Your channels, one guide.",9,WHITE)
    text(c,17*mm,43*mm,f"{count} CHANNELS",16,WHITE,True)
    text(c,17*mm,33*mm,"ANDROID TV  /  NUMBERED CHANNEL DIRECTORY",7,WARM)
    end(c)

def make_reader(rows,native,count):
    pages_by_genre={genre:[rows[genre][i:i+24] for i in range(0,len(rows[genre]),24)]
                    for genre in CATEGORIES}
    # Cover, welcome, contents, Free-To-Air TV, category pages, end card.
    num_pages=5+sum(len(x) for x in pages_by_genre.values())
    # Fill up to 4-page signature boundary without distorting category pages.
    padded=((num_pages+3)//4)*4
    c=canvas.Canvas(str(READER),pagesize=A5,pageCompression=1)
    c.setTitle("AUTV — The World of TV, All in One Place")
    c.setAuthor("AUTV Channel Operations")
    cover(c,count)
    start(c,"WELCOME",2,padded)
    text(c,17*mm,A5H-44*mm,"WELCOME TO AUTV",19,NAVY,True)
    text(c,17*mm,A5H-56*mm,"Your television. Your way.",11,SEA,True)
    for i,line in enumerate([
        "Browse the world of free live television",
        "with one simple, numbered channel guide.",
        "Find a favourite and settle in.",
        "",
        "OK  —  Watch the selected channel",
        "LONG-PRESS OK  —  Add or remove a favourite",
        "SETTINGS  —  Choose a startup channel",
        "REFRESH  —  Update channels and TV guide",
    ]):
        text(c,17*mm,A5H-(75+i*10)*mm,line,8.5,NAVY)
    end(c)
    start(c,"CONTENTS",3,padded)
    text(c,17*mm,A5H-35*mm,"CONTENTS",18,NAVY,True)
    sections=[("Free-To-Air TV",4)]
    page=5
    for genre in CATEGORIES:
        sections.append((genre,page))
        page+=len(pages_by_genre[genre])
    for i,(name,p) in enumerate(sections):
        y=A5H-(49+i*9)*mm
        text(c,18*mm,y,name,8.4,NAVY,i==0)
        right(c,A5W-18*mm,y,str(p),8.4,SEA,True)
        c.setStrokeColor(WARM);c.line(18*mm,y-2*mm,A5W-18*mm,y-2*mm)
    end(c)
    start(c,"FREE-TO-AIR TV",4,padded)
    text(c,17*mm,A5H-35*mm,"FREE-TO-AIR TV",17,NAVY,True)
    text(c,17*mm,A5H-44*mm,"Your local channels, all in one place.",7.8,SEA)
    for i,(n,name,provider) in enumerate(native):
        y=A5H-(60+i*15)*mm
        c.setFillColor(colors.HexColor("#EAE6DE"))
        c.roundRect(17*mm,y-5*mm,A5W-34*mm,13*mm,2*mm,stroke=0,fill=1)
        text(c,21*mm,y,str(n),12,NAVY,True)
        text(c,38*mm,y+2*mm,name,8.6,NAVY,True)
        text(c,38*mm,y-3.3*mm,provider,6.5,MUTED)
    text(c,17*mm,48*mm,"7plus, 9Now and SBS are linked separately",7.4,NAVY)
    text(c,17*mm,41*mm,"through supported account settings.",7.4,NAVY)
    end(c)
    page=5
    for genre in CATEGORIES:
        for i,entries in enumerate(pages_by_genre[genre]):
            category_page(c,genre,entries,i+1,len(pages_by_genre[genre]),page,padded)
            page+=1
    start(c,"SEE YOU AFTER THE BREAK",page,padded,True)
    text(c,17*mm,A5H-64*mm,"SEE YOU AFTER",19,WHITE,True)
    text(c,17*mm,A5H-78*mm,"THE BREAK.",19,WHITE,True)
    text(c,17*mm,A5H-94*mm,"There is always something worth watching.",8,WARM)
    text(c,17*mm,42*mm,f"{count} CHANNELS  /  "+DATE,8,WHITE,True)
    end(c);page+=1
    while page<=padded:
        start(c,"AUTV",page,padded,True)
        text(c,17*mm,A5H-76*mm,"YOUR CHANNELS, ONE GUIDE.",12,WHITE,True)
        end(c);page+=1
    c.save()
    return padded

def impose(pages):
    if pages%4:raise ValueError("Booklet page count must be a multiple of four")
    source=PdfReader(str(READER))
    writer=PdfWriter()
    w,h=landscape(A4)
    for sheet in range(pages//4):
        layouts=[(pages-2*sheet,1+2*sheet),
                 (2+2*sheet,pages-1-2*sheet)]
        for left,rightpage in layouts:
            page=writer.add_blank_page(width=w,height=h)
            page.merge_translated_page(source.pages[left-1],0,0,expand=False)
            page.merge_translated_page(source.pages[rightpage-1],A5W,0,expand=False)
    with PRINT.open("wb") as f:writer.write(f)

def main():
    rows,native,count=sources()
    OUT.mkdir(exist_ok=True)
    pages=make_reader(rows,native,count)
    impose(pages)
    assert READER.stat().st_size>30000 and PRINT.stat().st_size>30000
    print(f"Approved AUTV booklet: {count} FAST + {len(native)} Free-To-Air; "
          f"{pages} A5 pages / {pages//2} A4 duplex print faces.")
    print(f"Reader: {READER}")
    print(f"Print: {PRINT}")

if __name__=="__main__":main()
