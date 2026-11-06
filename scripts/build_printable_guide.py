#!/usr/bin/env python3
"""Build the print-ready AUTV channel directory from the current checked-in TV_GUIDE.md.

No network fetch, no inventing stream status, and no changes to playback data.
"""
from pathlib import Path
import re
from collections import Counter
from xml.sax.saxutils import escape

from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER, TA_LEFT
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.units import mm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import BaseDocTemplate, Frame, PageTemplate, Paragraph, PageBreak, Table, TableStyle, KeepTogether, Spacer
from reportlab.platypus.flowables import HRFlowable

ROOT=Path(__file__).resolve().parents[1]
SOURCE=ROOT/"TV_GUIDE.md"
TARGET=ROOT/"docs"/"AUTV_Printable_TV_Guide.pdf"
DATE="6 November 2026"

CATS=("News","Sport","Movies","Game Shows","Crime","Comedy","Entertainment",
      "Reality & Lifestyle","Factual","Kids & Animation","Music")
ORDER=list(CATS)
FONT="/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
FONT_B="/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
if Path(FONT).exists():
    pdfmetrics.registerFont(TTFont("Guide",FONT))
    pdfmetrics.registerFont(TTFont("Guide-Bold",FONT_B))
    REG="Guide"; BOLD="Guide-Bold"
else:
    REG="Helvetica"; BOLD="Helvetica-Bold"

NAVY=colors.HexColor("#14283B")
TEAL=colors.HexColor("#087F87")
LIGHT=colors.HexColor("#EAF2F4")
MID=colors.HexColor("#566778")
INK=colors.HexColor("#253344")

def parse():
    lines=SOURCE.read_text(encoding="utf-8").splitlines()
    native=[]; fast=[]
    native_mode=False; fast_mode=False
    for line in lines:
        if line.startswith("## Australian TV"):
            native_mode=True;fast_mode=False;continue
        if line.startswith("## Full numbered FAST/IPTV"):
            native_mode=False;fast_mode=True;continue
        if line.startswith("## ") and (native_mode or fast_mode):
            native_mode=False;fast_mode=False
        if not line.startswith("|") or re.match(r"^\|\s*:?-{2,}",line):
            continue
        parts=[s.strip() for s in line.strip().strip("|").split("|")]
        if not parts or not parts[0].isdigit():
            continue
        if native_mode and len(parts)>=4:
            native.append((int(parts[0]),parts[1],parts[2],parts[3]))
        if fast_mode:
            # Names can include a literal/escaped '|' (e.g. two programme titles).
            # Identify the controlled category boundary instead of assuming
            # Markdown contains exactly five pipe-delimited cells.
            tail=line.strip().rstrip("|")
            category=r"(News|Sport|Movies|Game Shows|Crime|Comedy|Entertainment|Reality & Lifestyle|Factual|Kids & Animation|Music)"
            m=re.match(r"^\\|\\s*(\\d+)\\s*\\|\\s*(.*?)\\s*\\|\\s*"+category+r"\\s*\\|\\s*(.*?)\\s*\\|\\s*(.*?)\\s*\\|?$",tail)
            if not m:
                raise SystemExit(f"Cannot parse catalogue row: {line}")
            no,name,cat,source,epg=m.groups()
            fast.append((int(no),name.replace(r"\\|","|").strip(),cat,source.strip(),epg.strip()))
    if len(native)!=6 or len(fast)<600:
        raise SystemExit(f"Refusing incomplete printable guide: {len(native)} native / {len(fast)} FAST")
    numbers=[r[0] for r in fast]
    if len(numbers)!=len(set(numbers)):
        raise SystemExit("Duplicate FAST numbering")
    if any(r[2] not in CATS for r in fast):
        raise SystemExit("Unknown channel category")
    return sorted(native), sorted(fast)

STYLE={
    "title":ParagraphStyle("Title",fontName=BOLD,fontSize=27,leading=32,textColor=NAVY,spaceAfter=8),
    "subtitle":ParagraphStyle("Subtitle",fontName=BOLD,fontSize=12,leading=17,textColor=TEAL,spaceAfter=12),
    "body":ParagraphStyle("Body",fontName=REG,fontSize=9,leading=14,textColor=INK,spaceAfter=8),
    "small":ParagraphStyle("Small",fontName=REG,fontSize=7.3,leading=10,textColor=MID),
    "head":ParagraphStyle("Head",fontName=BOLD,fontSize=14,leading=18,textColor=NAVY,spaceBefore=12,spaceAfter=8),
    "row":ParagraphStyle("Row",fontName=REG,fontSize=8.1,leading=12,textColor=INK),
    "rowbold":ParagraphStyle("Rowbold",fontName=BOLD,fontSize=8.1,leading=12,textColor=NAVY),
    "tablehead":ParagraphStyle("TableHead",fontName=BOLD,fontSize=8,leading=11,textColor=colors.white),
}

def P(value,style="row"):
    return Paragraph(escape(str(value)), STYLE[style])

class GuideDoc(BaseDocTemplate):
    def __init__(self,*args,**kwargs):
        super().__init__(*args,**kwargs)
        f=Frame(self.leftMargin,self.bottomMargin,self.width,self.height,
                leftPadding=0,rightPadding=0,topPadding=0,bottomPadding=0)
        self.addPageTemplates(PageTemplate(id="guide",frames=f,onPage=self.headerfooter))
    def headerfooter(self,canvas,doc):
        canvas.saveState()
        width,height=A4
        canvas.setFillColor(NAVY)
        canvas.rect(0,height-13*mm,width,13*mm,fill=1,stroke=0)
        canvas.setFillColor(colors.white);canvas.setFont(BOLD,9)
        canvas.drawString(16*mm,height-8.7*mm,"AUTV  /  PRINTABLE CHANNEL GUIDE")
        canvas.setStrokeColor(colors.HexColor("#D9E5E8"))
        canvas.line(16*mm,15*mm,width-16*mm,15*mm)
        canvas.setFont(REG,7);canvas.setFillColor(MID)
        canvas.drawString(16*mm,10*mm,"6 November 2026  |  Numbered channel directory")
        canvas.drawRightString(width-16*mm,10*mm,f"Page {doc.page}")
        canvas.restoreState()

def table(rows,widths,header=True):
    matrix=[[P(v,"tablehead" if header and j==0 else "row") for v in row]
            for j,row in enumerate(rows)]
    t=Table(matrix,colWidths=widths,repeatRows=1 if header else 0,hAlign="LEFT",splitByRow=1)
    spec=[
        ("VALIGN",(0,0),(-1,-1),"MIDDLE"),
        ("TOPPADDING",(0,0),(-1,-1),5),
        ("BOTTOMPADDING",(0,0),(-1,-1),5),
        ("LEFTPADDING",(0,0),(-1,-1),8),
        ("RIGHTPADDING",(0,0),(-1,-1),6),
        ("LINEBELOW",(0,0),(-1,-1),.25,colors.HexColor("#D8E2E7")),
        ("ROWBACKGROUNDS",(0,1 if header else 0),(-1,-1),[colors.white,colors.HexColor("#F4F8F9")]),
    ]
    if header: spec.extend([("BACKGROUND",(0,0),(-1,0),NAVY),
                           ("TOPPADDING",(0,0),(-1,0),7),
                           ("BOTTOMPADDING",(0,0),(-1,0),7)])
    t.setStyle(TableStyle(spec));return t

def main():
    native,fast=parse()
    TARGET.parent.mkdir(parents=True,exist_ok=True)
    doc=GuideDoc(str(TARGET),pagesize=A4,
                 leftMargin=16*mm,rightMargin=16*mm,
                 topMargin=21*mm,bottomMargin=21*mm,
                 title="AUTV - Printable TV Guide - 6 November 2026",
                 author="AUTV Channel Operations",
                 subject="Current Australian TV and curated FAST/IPTV channel directory")
    usable=A4[0]-32*mm
    story=[Spacer(1,16*mm),
           Paragraph("Your channels, one guide.",STYLE["title"]),
           Paragraph("SPORT  /  MOVIES  /  COMEDY",STYLE["subtitle"]),
           HRFlowable(width="100%",thickness=2,color=TEAL),
           Spacer(1,10*mm),
           Paragraph("PRINT EDITION  |  6 NOVEMBER 2026",STYLE["subtitle"]),
           Paragraph(f"{len(native)} Australian TV channels + {len(fast)} curated FAST/IPTV channels",STYLE["head"]),
           Paragraph("This booklet is a numbered reference for the current AUTV catalogue. Channel listings are not a guarantee that a stream is currently available or that programme guide data is complete.",STYLE["body"]),
           Spacer(1,7*mm),
           Paragraph("Quick start",STYLE["head"]),
           Paragraph("Use the TV remote to select a channel and press OK to play. Long-press OK to add a Favourite. Set your preferred startup channel in Settings. After updates, choose Settings > Refresh channels and TV guide.",STYLE["body"]),
           Paragraph("Australian television",STYLE["head"]),
           Paragraph("The six ABC and Network 10 channels below were approved for no-login playback on the owner's Australian TV. Additional 7plus, 9Now and SBS services may require linking through Settings.",STYLE["body"]),
           table([("NO.","CHANNEL","PROVIDER")]+[(str(n),name,provider) for n,name,provider,_ in native],[18*mm,100*mm,usable-118*mm]),
           Spacer(1,7*mm),
           Paragraph("Channel categories",STYLE["head"])]
    ct=Counter(r[2] for r in fast)
    mid=(len(ORDER)+1)//2
    category_rows=[]
    for i in range(mid):
        left=ORDER[i];right=ORDER[i+mid] if i+mid<len(ORDER) else ""
        category_rows.append((left,str(ct[left]),right,str(ct[right]) if right else ""))
    story.append(table([("CATEGORY","COUNT","CATEGORY","COUNT")]+category_rows,
                       [69*mm,19*mm,69*mm,usable-157*mm]))
    story.append(PageBreak())
    story.append(Paragraph("Complete numbered channel directory",STYLE["head"]))
    story.append(Paragraph("The channel number is the AUTV guide number, not necessarily the source service's native channel number. 'Full' and 'Channel only' are source-guide classifications, not live EPG verification.",STYLE["body"]))
    for cat in ORDER:
        selected=[r for r in fast if r[2]==cat]
        if not selected:continue
        heading=Paragraph(f"{escape(cat)}  <font color='#087F87'>({len(selected)})</font>",STYLE["head"])
        entries=[("NO.","CHANNEL","SOURCE","GUIDE")]+[
            (str(number),name,source,epg) for number,name,_,source,epg in selected
        ]
        story.append(KeepTogether([heading,table(entries[:min(4,len(entries))],[17*mm,86*mm,42*mm,usable-145*mm])]) if False else heading)
        story.append(table(entries,[17*mm,86*mm,42*mm,usable-145*mm]))
        story.append(Spacer(1,5*mm))
    story.append(Spacer(1,5*mm))
    story.append(Paragraph("End of channel directory",STYLE["head"]))
    story.append(Paragraph("Source of truth: safetyblade/autv - TV_GUIDE.md (generated 6 November 2026). This is a printable channel directory, not a live streaming or EPG status report.",STYLE["small"]))
    doc.build(story)
    print(f"Generated {TARGET}: {len(native)} Australian channels and {len(fast)} FAST/IPTV channels.")

if __name__=="__main__":
    main()
