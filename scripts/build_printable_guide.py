#!/usr/bin/env python3
"""Render AUTV using the exact owner-approved, locked PDF master.

Only catalogue numbers, names and counts may change. All original art, typography,
brand icons, editorial copy, spacing and page imposition come from the PDF itself.
If a category overflows its signed-off pages, FAIL instead of silently redesigning.
"""
import csv
import hashlib
import re
from collections import defaultdict
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo

import fitz

ROOT=Path(__file__).resolve().parents[1]
MASTER=ROOT/"docs/templates/AUTV_TV_Booklet_Master.pdf"
PRINT=ROOT/"docs/AUTV_TV_Booklet_Print.pdf"
READER=ROOT/"docs/AUTV_TV_Booklet_Reader.pdf"
GUIDE=ROOT/"channel_guide.csv"
SHA256="96c309e947ee742e454126af9e5d6376745bc703eb1041855530c31db140c2ff"
CATS=["News","Sport","Movies","Game Shows","Crime","Comedy","Entertainment",
      "Reality & Lifestyle","Factual","Kids & Animation","Music"]
ORIGINAL_COUNTS=dict(zip(CATS,[24,96,36,20,45,44,149,91,55,50,29]))
W,H=fitz.paper_size("a5")

def catalog():
    with GUIDE.open(encoding="utf-8-sig",newline="") as f:
        rows=[r for r in csv.DictReader(f) if r["Status"].strip().casefold()=="active"]
    if len(rows)<600:raise ValueError("Incomplete approved channel catalogue")
    channels=defaultdict(list);seen=set()
    for r in rows:
        n=int(r["Channel number"])
        if n in seen:raise ValueError("Duplicate channel number "+str(n))
        if r["Genre"] not in CATS:raise ValueError("Unexpected category "+r["Genre"])
        seen.add(n);channels[r["Genre"]].append((n,r["Channel name"].strip()))
    for cat in CATS:channels[cat].sort()
    return channels,len(rows)

def imposition():
    mapping={}
    for k in range(9):
        mapping[36-2*k]=(2*k,0)
        mapping[1+2*k]=(2*k,1)
        mapping[2+2*k]=(2*k+1,0)
        mapping[35-2*k]=(2*k+1,1)
    assert len(mapping)==36
    return mapping

def spans_for(page,side):
    xmin=W*side
    return [s for block in page.get_text("dict")["blocks"]
              for line in block.get("lines",[]) for s in line["spans"]
              if xmin-0.5<=s["bbox"][0]<xmin+W-0.5]

def category_of(spans):
    for sp in spans:
        text=sp["text"].upper().strip()
        if text.startswith("AUTV  /  "):
            cat=text.split("/",1)[1].strip()
            for known in CATS:
                if known.upper()==cat:return known
    return None

def numbers_on(spans,side):
    xmin=W*side
    return sorted([s for s in spans
        if s["text"].strip().isdigit() and len(s["text"].strip())>=3
        and 8.5<=s["size"]<=10
        and xmin+20<s["origin"][0]<xmin+40],
        key=lambda s:s["origin"][1])

def old_name(spans,side,y):
    return next((s["text"] for s in spans
         if abs(s["origin"][1]-y)<1.0
         and W*side+68<s["origin"][0]<W*side+90
         and 8.5<=s["size"]<=10),None)

def replace_text(page,span,replacement,background=(1,1,1)):
    if replacement==span["text"]:return
    box=fitz.Rect(span["bbox"])
    width=fitz.get_text_length(replacement,fontname="helv",fontsize=span["size"])
    box.x1=max(box.x1,box.x0+width+2)
    page.add_redact_annot(box,fill=background)
    page.apply_redactions(images=0,graphics=0,text=0)
    bold="Bold" in span["font"]
    rgb=((span["color"]>>16)&255,(span["color"]>>8)&255,span["color"]&255)
    page.insert_text(span["origin"],replacement,fontname="hebo" if bold else "helv",
        fontsize=span["size"],color=tuple(v/255 for v in rgb),overlay=True)

def template_pages(pdf):
    result=defaultdict(list);mapping=imposition()
    for logical in range(5,36):
        face,side=mapping[logical]
        spans=spans_for(pdf[face],side)
        category=category_of(spans)
        if category:result[category].append((logical,face,side,spans))
    for cat in CATS:
        if not result[cat]:raise ValueError("Master missing category "+cat)
        result[cat].sort()
    return result

def master_catalogue(pdf):
    groups=defaultdict(list)
    for cat,pages in template_pages(pdf).items():
        for _,_,side,spans in pages:
            for sp in numbers_on(spans,side):
                groups[cat].append((int(sp["text"]),old_name(spans,side,sp["origin"][1])))
    return groups

def rewrite_entries(pdf,channels):
    for cat,pages in template_pages(pdf).items():
        selected=channels[cat]
        capacity=24*len(pages)
        if len(selected)>capacity:
            raise ValueError(f"{cat} has {len(selected)} channels; locked design capacity is {capacity}. "+
                             "Publishing blocked until editorial page expansion is approved.")
        for part,(_,face,side,spans) in enumerate(pages):
            nums=numbers_on(spans,side)
            if not nums:raise ValueError("Master page missing numbered slots")
            y0=nums[0]["origin"][1]
            requested=selected[part*24:(part+1)*24]
            for idx in range(max(len(nums),len(requested))):
                y=y0+17.5*idx
                original=next((s for s in nums if abs(s["origin"][1]-y)<1.0),None)
                old=(int(original["text"]),old_name(spans,side,y)) if original else None
                new=requested[idx] if idx<len(requested) else None
                if old==new:continue
                x=W*side+19
                # A painted replacement row preserves all original non-row elements.
                background=(0.965,0.962,0.951) if idx%2==0 else (1,1,1)
                pdf[face].add_redact_annot(fitz.Rect(x,y-10.8,x+W-38,y+4),fill=background)
                pdf[face].apply_redactions(images=0,graphics=0,text=0)
                if new:
                    num,name=new
                    pdf[face].insert_text((W*side+26,y),str(num),fontsize=9.3,
                                          fontname="hebo",color=(0,0,0),overlay=True)
                    size=9.3
                    while fitz.get_text_length(name,fontname="helv",fontsize=size)>W-100 and size>6.3:
                        size-=0.3
                    pdf[face].insert_text((W*side+74,y),name,fontsize=size,
                                          fontname="helv",color=(0,0,0),overlay=True)

def refresh_metadata(pdf,channels,count):
    today=datetime.now(ZoneInfo("Australia/Melbourne")).strftime("%d %B %Y")
    contents=sorted([s for s in spans_for(pdf[2],1)
                     if re.fullmatch(r"\d+ channels",s["text"].strip(),re.I)],
                    key=lambda s:s["origin"][1])
    if len(contents)!=len(CATS):
        raise ValueError("Approved contents layout changed unexpectedly")
    for cat,sp in zip(CATS,contents):
        replace_text(pdf[2],sp,f"{len(channels[cat])} channels")
    for idx in range(len(pdf)):
        page=pdf[idx]
        spans=[s for block in page.get_text("dict")["blocks"]
              for line in block.get("lines",[]) for s in line["spans"]]
        for sp in spans:
            before=sp["text"]
            after=re.sub(r"\b639 CHANNELS\b",f"{count} CHANNELS",before)
            after=after.replace("06 November 2026",today)
            after=after.replace("06 NOVEMBER 2026",today.upper())
            if after!=before:
                background=(0.025,0.030,0.043) if idx==0 else (1,1,1)
                replace_text(page,sp,after,background)

def make_reader(pdf):
    reader=fitz.open()
    mapping=imposition()
    for logical in range(1,37):
        face,side=mapping[logical]
        page=reader.new_page(width=W,height=H)
        xmin=W*side
        page.show_pdf_page(page.rect,pdf,face,
                           clip=fitz.Rect(xmin,0,xmin+W,H))
    reader.save(str(READER),garbage=4,deflate=True)
    reader.close()

def main():
    binary=MASTER.read_bytes()
    if hashlib.sha256(binary).hexdigest()!=SHA256:
        raise ValueError("The owner-approved master PDF has changed. Publishing refused.")
    channels,count=catalog()
    pdf=fitz.open(stream=binary,filetype="pdf")
    if len(pdf)!=18:raise ValueError("Expected 18-page A4 duplex master")
    same=(count==639 and master_catalogue(pdf)==channels)
    PRINT.parent.mkdir(parents=True,exist_ok=True)
    if same:
        PRINT.write_bytes(binary)  # exact, byte-for-byte original; no recreation
        print("Approved A4 print master preserved byte-for-byte.")
    else:
        rewrite_entries(pdf,channels)
        refresh_metadata(pdf,channels,count)
        pdf.save(str(PRINT),garbage=4,deflate=True)
        pdf.close()
        pdf=fitz.open(str(PRINT))
    make_reader(pdf)
    pdf.close()
    with fitz.open(str(READER)) as check:
        assert len(check)==36
    print(f"Published {count} approved FAST channels in original 36-page editorial design.")

if __name__=="__main__":
    main()
