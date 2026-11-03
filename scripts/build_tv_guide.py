#!/usr/bin/env python3
"""Build a current AUTV .docx from the authoritative FAST/IPTV CSV.

Usage: python scripts/build_tv_guide.py [output.docx]
Requires: python-docx

Australian TV channels are app-native: this exporter deliberately does not invent
them. Supply an approved export at docs/australian_tv_channels.csv with columns
number,name,provider,guide,status to include those listings. No source URLs.
"""
import csv
from collections import Counter
from datetime import date
from pathlib import Path
import sys

from docx import Document
from docx.shared import Cm, Pt
from docx.oxml import OxmlElement
from docx.oxml.ns import qn

ROOT = Path(__file__).resolve().parents[1]
CSV = ROOT / "channel_guide.csv"
AUS = ROOT / "docs" / "australian_tv_channels.csv"
OUT = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "AUTV_TV_Guide.docx"

def cell(row, key):
    return str(row.get(key, "")).strip()

with CSV.open(encoding="utf-8-sig", newline="") as file:
    records = [r for r in csv.DictReader(file) if cell(r, "Status").casefold() == "active"]
records.sort(key=lambda r: int(cell(r, "Channel number")))
numbers = [int(cell(r, "Channel number")) for r in records]
if len(numbers) != len(set(numbers)):
    raise ValueError("Duplicate FAST/IPTV channel numbers; refuse to publish")

australian = []
if AUS.exists():
    with AUS.open(encoding="utf-8-sig", newline="") as file:
        australian = list(csv.DictReader(file))
    australian = [r for r in australian if cell(r,"status").casefold() == "active"]
    australian.sort(key=lambda r: int(cell(r,"number")) if cell(r,"number").isdigit() else 99999)

d = Document()
section = d.sections[0]
section.top_margin = Cm(1.8)
section.bottom_margin = Cm(1.8)
section.left_margin = Cm(1.8)
section.right_margin = Cm(1.8)
normal = d.styles["Normal"]
normal.font.name = "Aptos"
normal.font.size = Pt(9)

d.add_heading("AUTV — Home TV Guide", 0)
d.add_paragraph(f"Generated {date.today().isoformat()} | {len(records)} active FAST/IPTV channels")
d.add_paragraph("Current catalogue source: channel_guide.csv. Listed channels are configured, not guaranteed to be playable at every moment.")
d.add_heading("Install and start", 1)
for step in [
    "Obtain the current owner-approved AUTV APK and install it on Android TV / Google TV.",
    "Open AUTV and allow the current channel guide and playlist to load.",
    "Use the remote direction buttons to navigate, OK to tune and Back to return.",
    "Browse categories, Australian TV and Favourites. Long-press OK on a supported channel to toggle Favourites.",
    "In Settings, select a starting channel if supported and connect broadcaster accounts using the on-screen instructions or QR activation where available.",
    "After a playlist update, refresh the channel guide in AUTV.",
]:
    d.add_paragraph(step, style="List Number")
d.add_heading("Australian TV", 1)
d.add_paragraph(
    "Australian broadcasters use separate app integrations rather than the FAST/IPTV CSV. "
    "Provider login and playback depend on the installed build and provider authorisation. "
    "ABC iview and Network 10 may require configuration; check Settings for current status."
)
if australian:
    t=d.add_table(rows=1, cols=4);t.style="Light Shading Accent 1"
    for i,h in enumerate(["Number","Channel","Provider","Guide"]): t.rows[0].cells[i].text=h
    for r in australian:
        cells=t.add_row().cells
        for i,k in enumerate(["number","name","provider","guide"]):cells[i].text=cell(r,k)
else:
    d.add_paragraph(
        "Current app-native broadcaster channel export has not been provided or verified. "
        "The Australian TV directory is pending release validation; no broadcaster channels have been invented."
    )
d.add_heading("Troubleshooting", 1)
for item in [
    "No live stream: refresh once and retry. A catalogue listing does not prove the stream is currently available.",
    "No programme information: some channels intentionally have channel-only guide coverage.",
    "Configuration required: review that broadcaster's status in Settings. Unsupported provisioning cannot be fixed by repeatedly signing in.",
    "Outdated channels: refresh the playlist/guide and verify the installed APK version.",
]:
    d.add_paragraph(item, style="List Bullet")
d.add_heading("FAST/IPTV category totals", 1)
summary = d.add_table(rows=1, cols=2)
summary.style = "Light Shading Accent 1"
summary.rows[0].cells[0].text = "Category"
summary.rows[0].cells[1].text = "Channels"
for name, n in Counter(cell(r,"Genre") for r in records).items():
    cells = summary.add_row().cells
    cells[0].text = name
    cells[1].text = str(n)
d.add_heading("Full numbered FAST/IPTV channel directory",1)
d.add_paragraph("Full = schedule expected, not always available. Channel only = reliable schedule not expected.")
t=d.add_table(rows=1,cols=5);t.style="Light Shading Accent 1"
for i,h in enumerate(["No.","Channel","Category","Provider","Guide"]):t.rows[0].cells[i].text=h
header_properties = t.rows[0]._tr.get_or_add_trPr()
repeat = OxmlElement("w:tblHeader")
repeat.set(qn("w:val"), "true")
header_properties.append(repeat)
for r in records:
    cells=t.add_row().cells
    for i,key in enumerate(["Channel number","Channel name","Genre","Source","EPG"]): cells[i].text=cell(r,key)
    if len(t.rows) % 50 == 0:
        print(f"Added {len(t.rows)-1} channels")
footer=section.footer.paragraphs[0]
footer.text="AUTV | Generated from channel_guide.csv | Australian TV requires verification | Page "
field = OxmlElement("w:fldSimple")
field.set(qn("w:instr"), "PAGE")
footer._p.append(field)
OUT.parent.mkdir(parents=True,exist_ok=True)
d.save(OUT)
print(f"Saved {OUT} ({len(records)} FAST/IPTV, {len(australian)} Australian TV)")
