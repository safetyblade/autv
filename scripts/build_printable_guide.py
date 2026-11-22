#!/usr/bin/env python3
"""Render the AUTV booklet from the live channel guide — no locked counts.

The owner-approved master PDF supplies the design only: cover, start-here,
contents and free-to-air pages, one catalogue template page per category,
and the back cover. Catalogue pages are generated from channel_guide.csv at
whatever count exists — 24 rows per page, as many pages per category as the
data needs — and the booklet is padded to a printable multiple of four.
Page counts, contents and totals all come from the data, never from the
master's original 639-channel layout.
"""
import csv
import math
import re
from collections import defaultdict
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo

import fitz

ROOT = Path(__file__).resolve().parents[1]
MASTER = ROOT / "docs/templates/AUTV_TV_Booklet_Master.pdf"
PRINT = ROOT / "docs/AUTV_TV_Booklet_Print.pdf"
READER = ROOT / "docs/AUTV_TV_Booklet_Reader.pdf"
GUIDE = ROOT / "channel_guide.csv"

CATS = ["News", "Sport", "Movies", "Game Shows", "Crime", "Comedy",
        "Entertainment", "Reality & Lifestyle", "Factual",
        "Kids & Animation", "Music"]
ROWS_PER_PAGE = 24
ROW_Y0 = 115.0          # baseline of the first catalogue row
ROW_PITCH = 17.5
ROW_BAND = (104.0, 522.0)   # vertical extent of the row area
INK = (7 / 255, 10 / 255, 96 / 255)

def printable_name(name):
    """Base-14 fonts cannot render some Unicode punctuation; map it to the
    closest print equivalent (the master booklet did the same)."""
    return (name.replace("\u2026", "...").replace("\u2018", "'")
            .replace("\u2019", "'").replace("\u201c", '"')
            .replace("\u201d", '"').replace("\u2013", "-")
            .replace("\u2014", "-").replace("\u00b7", "-"))          # master row text colour
ROW_SHADE = ((0.965, 0.962, 0.951), (1, 1, 1))
OLD_DATE_LONG = "06 November 2026"
OLD_DATE_UPPER = "06 NOVEMBER 2026"
OLD_TOTAL = "639 CHANNELS"


def catalog():
    with GUIDE.open(encoding="utf-8-sig", newline="") as f:
        rows = [r for r in csv.DictReader(f)
                if r["Status"].strip().casefold() == "active"]
    if len(rows) < 600:
        raise ValueError("Incomplete approved channel catalogue")
    channels = defaultdict(list)
    seen = set()
    for r in rows:
        n = int(r["Channel number"])
        if n in seen:
            raise ValueError("Duplicate channel number " + str(n))
        if r["Genre"] not in CATS:
            raise ValueError("Unexpected category " + r["Genre"])
        seen.add(n)
        channels[r["Genre"]].append((n, r["Channel name"].strip()))
    for cat in CATS:
        channels[cat].sort()
    return channels, len(rows)


def imposition(n):
    """logical page -> (face, side) for an n-page booklet (n % 4 == 0)."""
    mapping = {}
    for k in range(n // 4):
        mapping[n - 2 * k] = (2 * k, 0)
        mapping[1 + 2 * k] = (2 * k, 1)
        mapping[2 + 2 * k] = (2 * k + 1, 0)
        mapping[n - 1 - 2 * k] = (2 * k + 1, 1)
    return mapping


class Master:
    """Extract logical A5 pages from the locked 36-page master."""

    def __init__(self, path):
        self.pdf = fitz.open(path)
        if len(self.pdf) != 18:
            raise ValueError("Expected 18-page A4 duplex master")
        self.w = self.pdf[0].rect.width / 2
        self.h = self.pdf[0].rect.height
        self.imp = imposition(36)

    def logical_page(self, doc, logical):
        """Append master logical page `logical` to doc; return the new page."""
        face, side = self.imp[logical]
        page = doc.new_page(width=self.w, height=self.h)
        clip = fitz.Rect(side * self.w, 0, side * self.w + self.w, self.h)
        page.show_pdf_page(page.rect, self.pdf, face, clip=clip)
        return page

    def spans(self, logical):
        face, side = self.imp[logical]
        xmin = side * self.w
        out = []
        for block in self.pdf[face].get_text("dict")["blocks"]:
            for line in block.get("lines", []):
                for s in line["spans"]:
                    if xmin - 0.5 <= s["bbox"][0] < xmin + self.w - 0.5:
                        s = dict(s)
                        s["bbox"] = [s["bbox"][0] - xmin, s["bbox"][1],
                                     s["bbox"][2] - xmin, s["bbox"][3]]
                        s["origin"] = (s["origin"][0] - xmin, s["origin"][1])
                        out.append(s)
        return out

    def category_templates(self):
        """First master logical page for each category (design template)."""
        found = {}
        for logical in range(5, 36):
            for s in self.spans(logical):
                t = s["text"].upper().strip()
                if t.startswith("AUTV  /  "):
                    cat = t.split("/", 1)[1].strip()
                    for known in CATS:
                        if known.upper() == cat and known not in found:
                            found[known] = logical
        missing = [c for c in CATS if c not in found]
        if missing:
            raise ValueError("Master missing category templates: " + str(missing))
        return found


def rewrite_spans(page, replacements):
    """Replace span texts on a copied page: redact the old text away
    (including text inside the copied Form XObject), then insert the new.

    replacements: list of (span, new_text_or_None, fill)."""
    todo = [(s, t, f) for (s, t, f) in replacements if t != s["text"]]
    if not todo:
        return
    for span, _, fill in todo:
        page.add_redact_annot(fitz.Rect(span["bbox"]), fill=fill)
    page.apply_redactions()
    for span, new_text, _ in todo:
        if new_text is None:
            continue
        rgb = tuple(((span["color"] >> shift) & 255) / 255
                    for shift in (16, 8, 0))
        page.insert_text(span["origin"], new_text,
                         fontname="hebo" if "Bold" in span["font"] else "helv",
                         fontsize=span["size"], color=rgb, overlay=True)


def build_catalogue_page(doc, master, template_logical, entries, part, parts,
                         page_number, today_long):
    page = master.logical_page(doc, template_logical)
    spans = master.spans(template_logical)
    w = master.w
    # Redact the old row band and the changing furniture (subtitle, page
    # number, footer date) so no ghost text survives underneath.
    furniture = []
    for span in spans:
        t = span["text"]
        if t.startswith("CHANNEL DIRECTORY"):
            furniture.append((span,
                              f"CHANNEL DIRECTORY  \u2022  {part} OF {parts}",
                              (1, 1, 1)))
        elif span["origin"][1] > 570 and t.strip().isdigit():
            furniture.append((span, str(page_number), (1, 1, 1)))
        elif OLD_DATE_LONG in t:
            furniture.append((span, t.replace(OLD_DATE_LONG, today_long),
                              (1, 1, 1)))
    page.add_redact_annot(fitz.Rect(19, ROW_BAND[0], w - 19, ROW_BAND[1]),
                          fill=(1, 1, 1))
    for span, new_text, fill in furniture:
        if new_text != span["text"]:
            page.add_redact_annot(fitz.Rect(span["bbox"]), fill=fill)
    page.apply_redactions()
    for span, new_text, _ in furniture:
        if new_text != span["text"]:
            rgb = tuple(((span["color"] >> shift) & 255) / 255
                        for shift in (16, 8, 0))
            page.insert_text(span["origin"], new_text,
                             fontname="hebo" if "Bold" in span["font"]
                             else "helv",
                             fontsize=span["size"], color=rgb, overlay=True)
    # Repaint the alternating row shading, then the rows.
    shape = page.new_shape()
    for idx in range(ROWS_PER_PAGE):
        y = ROW_Y0 + ROW_PITCH * idx
        shape.draw_rect(fitz.Rect(19, y - 10.8, w - 19, y + 4))
        shape.finish(color=None, fill=ROW_SHADE[idx % 2])
    shape.commit(overlay=True)
    for idx, (num, name) in enumerate(entries):
        y = ROW_Y0 + ROW_PITCH * idx
        page.insert_text((26.9, y), str(num), fontname="hebo",
                         fontsize=9.3, color=INK, overlay=True)
        name = printable_name(name)
        size = 9.3
        while fitz.get_text_length(name, fontname="helv",
                                   fontsize=size) > w - 100 and size > 6.3:
            size -= 0.3
        page.insert_text((74.9, y), name, fontname="helv",
                         fontsize=size, color=INK, overlay=True)
    return page


def build_reader(channels, count, today_long, today_upper):
    master = Master(MASTER)
    templates = master.category_templates()
    pages_per_cat = {c: max(1, math.ceil(len(channels[c]) / ROWS_PER_PAGE))
                     for c in CATS}
    total_logical = 5 + sum(pages_per_cat.values())
    pad = (-total_logical) % 4
    total_logical += pad

    doc = fitz.open()
    notes = []
    # Front matter: cover, start here, contents, free-to-air (logical 1-4).
    for logical in (1, 2, 3, 4):
        master.logical_page(doc, logical)
        notes.append(master.spans(logical))
    # Catalogue.
    starts = {}
    logical = 5
    for cat in CATS:
        starts[cat] = logical
        parts = pages_per_cat[cat]
        for part in range(1, parts + 1):
            entries = channels[cat][(part - 1) * ROWS_PER_PAGE:
                                    part * ROWS_PER_PAGE]
            build_catalogue_page(doc, master, templates[cat], entries,
                                  part, parts, logical, today_long)
            notes.append(None)
            logical += 1
    # Padding blanks (footer only) so the booklet imposes cleanly.
    for _ in range(pad):
        page = doc.new_page(width=master.w, height=master.h)
        page.insert_text((17.9, 580.3), f"AUTV  •  {today_long}",
                         fontname="helv", fontsize=6.4, color=(0, 0, 0),
                         overlay=True)
        notes.append(None)
    # Back cover (master logical 36).
    master.logical_page(doc, 36)
    notes.append(master.spans(36))
    # Contents counts/start pages plus dates and totals on front/back
    # matter — one redaction pass per page (cover and back cover are dark).
    last = len(doc) - 1
    for idx, spans in enumerate(notes):
        if not spans:
            continue
        page = doc[idx]
        fill = (0.025, 0.030, 0.043) if idx in (0, last) else (1, 1, 1)
        replacements = []
        for span in spans:
            text = span["text"]
            new_text = text.replace(OLD_TOTAL, f"{count} CHANNELS")
            new_text = new_text.replace(OLD_DATE_LONG, today_long)
            new_text = new_text.replace(OLD_DATE_UPPER, today_upper)
            if new_text != text:
                replacements.append((span, new_text, fill))
        if idx == 2:
            count_spans = sorted(
                (s for s in spans
                 if re.fullmatch(r"\d+ channels", s["text"].strip())),
                key=lambda s: s["origin"][1])
            page_spans = sorted(
                (s for s in spans if s["text"].strip().isdigit()
                 and s["origin"][0] > 300 and s["origin"][1] < 500),
                key=lambda s: s["origin"][1])
            if len(count_spans) != len(CATS) or len(page_spans) != len(CATS):
                raise ValueError("Approved contents layout changed "
                                 "unexpectedly")
            for cat, cspan, pspan in zip(CATS, count_spans, page_spans):
                replacements.append(
                    (cspan, f"{len(channels[cat])} channels", fill))
                replacements.append((pspan, str(starts[cat]), fill))
        rewrite_spans(page, replacements)
    return doc, total_logical


def build_print(reader, total_logical):
    imp = imposition(total_logical)
    w = reader[0].rect.width
    h = reader[0].rect.height
    doc = fitz.open()
    for face in range(total_logical // 2):
        doc.new_page(width=2 * w, height=h)
    for logical, (face, side) in imp.items():
        target = fitz.Rect(side * w, 0, side * w + w, h)
        doc[face].show_pdf_page(target, reader, logical - 1)
    return doc


def main():
    channels, count = catalog()
    now = datetime.now(ZoneInfo("Australia/Melbourne"))
    today_long = now.strftime("%d %B %Y")
    reader, total = build_reader(channels, count, today_long,
                                 today_long.upper())
    PRINT.parent.mkdir(parents=True, exist_ok=True)
    reader.save(str(READER), garbage=4, deflate=True)
    print_doc = build_print(reader, total)
    print_doc.save(str(PRINT), garbage=4, deflate=True)
    print(f"Published {count} channels in a {total}-page booklet "
          f"({total // 2} print faces / {total} reading pages), "
          "generated from the live guide.")


if __name__ == "__main__":
    main()
