#!/usr/bin/env python3
"""Build a print-friendly AUTV landscape A4 booklet-style Word channel directory.

A4 landscape spreads have two A5-sized panels. Print at actual size, double-sided
where supported. For folded booklet imposition, use the printer's Booklet setting;
the Word pages are sequential spreads rather than pre-imposed printer signatures.

Only approved catalogue data and published channel-number presence are used;
we do NOT claim that a published source has passed actual Australian TV playback.
"""
import csv
import re
import sys
from collections import Counter
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo

from docx import Document
from docx.enum.table import WD_CELL_VERTICAL_ALIGNMENT, WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.section import WD_ORIENT
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Cm, Pt, RGBColor

ROOT = Path(__file__).resolve().parents[1]
CATALOGUE = ROOT / "channel_guide.csv"
PLAYLIST = ROOT / "playlist.m3u"
AU_CSV = ROOT / "docs/australian_tv_channels.csv"
OUT = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "docs/AUTV_TV_Guide.docx"

INK = "070A10"
GLASS = "181C24"
SILVER = "CEC4B4"
WARM_WHITE = "F2EFE9"
MUTED = "595B62"
PAPER = "FFFFFF"
PALE = "F5F3EF"

def rgb(hexvalue):
    return RGBColor.from_string(hexvalue)

def shade(cell, fill):
    properties = cell._tc.get_or_add_tcPr()
    el = OxmlElement("w:shd")
    el.set(qn("w:fill"), fill)
    properties.append(el)

def margins(cell, top=80, start=110, bottom=80, end=110):
    tc = cell._tc
    props = tc.get_or_add_tcPr()
    mar = props.first_child_found_in("w:tcMar")
    if mar is None:
        mar = OxmlElement("w:tcMar")
        props.append(mar)
    for edge, size in [("top",top), ("start",start), ("bottom",bottom), ("end",end)]:
        node = OxmlElement("w:" + edge)
        node.set(qn("w:w"), str(size))
        node.set(qn("w:type"), "dxa")
        mar.append(node)

def write_line(cell, label, number=None, note=None, dark=False):
    cell.text = ""
    paragraph = cell.paragraphs[0]
    paragraph.paragraph_format.space_before = Pt(0)
    paragraph.paragraph_format.space_after = Pt(0)
    paragraph.paragraph_format.line_spacing = 1.0
    if number is not None:
        run = paragraph.add_run(str(number).rjust(4) + "   ")
        run.bold = True
        run.font.size = Pt(8)
        run.font.color.rgb = rgb(SILVER if dark else MUTED)
    title = paragraph.add_run(label)
    title.font.name = "Aptos"
    title.font.size = Pt(8.2)
    title.font.color.rgb = rgb(WARM_WHITE if dark else INK)
    title.bold = bool(dark)
    if note:
        status = paragraph.add_run("    " + note)
        status.font.size = Pt(7)
        status.font.color.rgb = rgb(SILVER if dark else MUTED)

def get_numbers():
    numbers = set()
    pattern = re.compile(r'(?:^|\s)tvg-chno="(\d+)"')
    for line in PLAYLIST.read_text(encoding="utf-8-sig").splitlines():
        if line.startswith("#EXTINF:"):
            match = pattern.search(line)
            if match:
                numbers.add(int(match.group(1)))
    return numbers

with CATALOGUE.open(encoding="utf-8-sig", newline="") as handle:
    records = [r for r in csv.DictReader(handle) if r["Status"].strip().lower() == "active"]
records.sort(key=lambda row: int(row["Channel number"]))
number_list = [int(r["Channel number"]) for r in records]
if not records or len(number_list) != len(set(number_list)):
    raise ValueError("Duplicate or empty active channel directory")
published = get_numbers()
counts = Counter(r["Genre"] for r in records)
genre_order = list(dict.fromkeys(r["Genre"] for r in records))
native = []
if AU_CSV.exists():
    with AU_CSV.open(encoding="utf-8-sig", newline="") as handle:
        native = [r for r in csv.DictReader(handle) if r.get("status", "").strip().lower() == "active"]
    native.sort(key=lambda row: int(row["number"]) if row.get("number","").isdigit() else 999999)

date = datetime.now(ZoneInfo("Australia/Melbourne")).strftime("%d %B %Y")
doc = Document()
section = doc.sections[0]
section.orientation = WD_ORIENT.LANDSCAPE
section.page_width = Cm(29.7)
section.page_height = Cm(21)
section.top_margin = Cm(1.15)
section.bottom_margin = Cm(1.1)
section.left_margin = Cm(1.05)
section.right_margin = Cm(1.05)
section.header_distance = Cm(0.45)
section.footer_distance = Cm(0.50)
normal = doc.styles["Normal"]
normal.font.name = "Aptos"
normal.font.size = Pt(8.5)
normal.font.color.rgb = rgb(INK)
normal.paragraph_format.space_after = Pt(0)

header = section.header.paragraphs[0]
header.text = "AUTV    /    CHANNEL DIRECTORY"
header.style = doc.styles["Normal"]
header.alignment = WD_ALIGN_PARAGRAPH.RIGHT
for run in header.runs:
    run.bold = True
    run.font.size = Pt(7)
    run.font.color.rgb = rgb(MUTED)
footer = section.footer.paragraphs[0]
footer.alignment = WD_ALIGN_PARAGRAPH.RIGHT
run = footer.add_run("AUTV  •  " + date + "     |     ")
run.font.size = Pt(7)
run.font.color.rgb = rgb(MUTED)
page = OxmlElement("w:fldSimple")
page.set(qn("w:instr"), "PAGE")
footer._p.append(page)

# The cover uses the app's near-black/silver identity; interior uses light stock to save ink.
cover = doc.add_table(rows=1, cols=1)
cover.autofit = False
cover.columns[0].width = Cm(27.5)
panel = cover.cell(0,0)
shade(panel, INK)
margins(panel, 550, 600, 600, 600)
for title, size, color in [
    ("AUTV", 40, WARM_WHITE),
    ("THE CHANNEL GUIDE", 24, SILVER),
    ("AUSTRALIAN TV   /   FAST CHANNELS   /   FAVOURITES", 10, WARM_WHITE),
    ("", 12, WARM_WHITE),
    (f"{len(records)} CONFIGURED CHANNELS", 17, WARM_WHITE),
    (f"{len(published & set(number_list))} CURRENTLY PUBLISHED STREAM ENTRIES", 11, SILVER),
    ("", 12, WARM_WHITE),
    ("LANDSCAPE PRINT EDITION    •    " + date.upper(), 10, WARM_WHITE),
]:
    p = panel.paragraphs[0] if len(panel.paragraphs)==1 and not panel.paragraphs[0].text else panel.add_paragraph()
    p.paragraph_format.space_after = Pt(9)
    run = p.add_run(title)
    run.bold = True
    run.font.size = Pt(size)
    run.font.color.rgb = rgb(color)
doc.add_paragraph("DIRECTORY  /  NOT A CERTIFICATION OF LIVE PLAYBACK").runs[0].font.color.rgb = rgb(MUTED)
doc.add_page_break()

def heading(title, subtitle):
    t = doc.add_table(rows=1, cols=1)
    t.autofit = False
    t.columns[0].width = Cm(27.5)
    cell = t.cell(0,0)
    shade(cell, INK)
    margins(cell, 160, 230, 160, 230)
    p = cell.paragraphs[0]
    a = p.add_run(title.upper())
    a.bold = True
    a.font.size = Pt(16)
    a.font.color.rgb = rgb(WARM_WHITE)
    if subtitle:
        p2 = cell.add_paragraph()
        sub = p2.add_run(subtitle)
        sub.font.size = Pt(8)
        sub.font.color.rgb = rgb(SILVER)
    doc.add_paragraph().paragraph_format.space_after = Pt(0)

def spread_rows(rows, status=True):
    # 44 entries per landscape A4 spread / two compact A5-like panels
    per_panel = 22
    left, right = rows[:per_panel], rows[per_panel:2*per_panel]
    table = doc.add_table(rows=per_panel, cols=2)
    table.autofit = False
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    for col in table.columns:
        col.width = Cm(13.45)
    for idx in range(per_panel):
        for column, data in [(0,left),(1,right)]:
            cell = table.cell(idx,column)
            cell.vertical_alignment = WD_CELL_VERTICAL_ALIGNMENT.CENTER
            shade(cell, PALE if idx%2==0 else PAPER)
            margins(cell, 65, 130, 65, 130)
            if idx < len(data):
                item = data[idx]
                no = int(item["Channel number"])
                note = "PUBLISHED" if no in published else "LISTED"
                if item["EPG"].strip().lower()=="channel only":
                    note += " · NO EPG"
                write_line(cell, item["Channel name"], no, note if status else None)
            else:
                cell.text = ""
    doc.add_paragraph().paragraph_format.space_after = Pt(0)

heading("Using AUTV", "YOUR TV REMOTE   /   THE CURRENT CHANNEL LINEUP")
p = doc.add_paragraph()
p.add_run("Settings  →  Refresh channels and TV guide").bold = True
doc.add_paragraph("Refresh the app’s channel list and programme information without restarting. Australian broadcasters may have their own account-specific refresh in Settings.")
doc.add_paragraph("PUBLISHED means a stream entry is in the generated AUTV playlist. LISTED means a configured channel currently lacks a published entry. Neither label guarantees that a channel plays in Australia.")
doc.add_paragraph("Print: A4 landscape at actual size. Duplex printing is supported for a landscape bound/stapled booklet. The paired A5-style panels are in reading order; folding pages into signatures requires separate page imposition, not the printer's automatic booklet option.")
heading("Contents", f"{len(records)} CHANNELS  •  {len(genre_order)} CATEGORIES")
summary = doc.add_table(rows=0, cols=2)
summary.autofit=False
for name in genre_order:
    cells=summary.add_row().cells
    shade(cells[0],PALE)
    shade(cells[1],PALE)
    write_line(cells[0],name,dark=False)
    write_line(cells[1],f"{counts[name]} channels",dark=False)
doc.add_paragraph("Australian broadcasters are discovered in the app, separately from this FAST/IPTV list. Their dynamic channel numbers are omitted until a verified export is supplied.")
doc.add_page_break()

for genre in genre_order:
    items = [r for r in records if r["Genre"]==genre]
    for start in range(0,len(items),44):
        piece = items[start:start+44]
        subtitle=f"{len(items)} CHANNELS    •    {start+1}–{start+len(piece)} OF {len(items)}"
        if start==0 and genre=="Sport": subtitle+="    •    MUTV #216"
        heading(genre,subtitle)
        spread_rows(piece)
        doc.add_page_break()

if native:
    heading("Australian TV",f"{len(native)} VERIFIED NATIVE CHANNEL ENTRIES")
    for row in native:
        doc.add_paragraph(f'{row.get("number","")}   {row.get("name","")}   /   {row.get("provider","")}')
else:
    heading("Australian TV", "NATIVE BROADCASTERS ARE MANAGED BY THE APPLICATION")
    doc.add_paragraph("ABC, 7plus, 9Now, Network 10 and SBS channels depend on linked sessions and the installed AUTV build. No unverified native channel numbers have been invented.")

doc.add_paragraph("Source: channel_guide.csv and playlist.m3u in safetyblade/autv. This directory is a snapshot. No channel listing certifies Australian rights, device playback, ad continuity or programme schedule.")
OUT.parent.mkdir(parents=True,exist_ok=True)
doc.save(OUT)
print(f"Generated landscape booklet-style {OUT} | {len(records)} active, {len(published & set(number_list))} published, {len(genre_order)} genres")
