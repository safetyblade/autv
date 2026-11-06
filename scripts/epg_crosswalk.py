"""Controlled, reviewed XMLTV channel-ID overrides for AUTV.

Only explicit provider/region-specific mappings are permitted. This file contains no
heuristic matching and never changes a channel number, URL or provider account.
"""
import csv
from dataclasses import dataclass
from pathlib import Path

REQUIRED = ("target_tvg_id", "source_tvg_id", "source_file", "approved_by", "reason")

@dataclass(frozen=True)
class EpgCrosswalk:
    source_id: str
    source_file: str
    approved_by: str
    reason: str

def load_crosswalk(path: Path, targets: set[str], sources: list[Path]) -> dict[str, EpgCrosswalk]:
    allowed_sources = {p.name for p in sources}
    holds_path = path.parent / "epg_crosswalk_holds.csv"
    holds = {}
    if holds_path.exists():
        with holds_path.open(newline="", encoding="utf-8-sig") as stream:
            reader = csv.DictReader(stream)
            if tuple(reader.fieldnames or ()) != ("target_tvg_id", "reason", "reference"):
                raise ValueError("Malformed EPG hold registry header")
            for row in reader:
                key = (row.get("target_tvg_id") or "").strip()
                reason = (row.get("reason") or "").strip()
                reference = (row.get("reference") or "").strip()
                if not key or not reason or not reference or key in holds:
                    raise ValueError("Invalid or duplicate held EPG channel identity")
                holds[key] = reason
    output: dict[str, EpgCrosswalk] = {}
    if not path.exists():
        raise ValueError(f"EPG crosswalk missing: {path}")
    with path.open(newline="", encoding="utf-8-sig") as handle:
        reader = csv.DictReader(handle)
        if tuple(reader.fieldnames or ()) != REQUIRED:
            raise ValueError(f"EPG crosswalk header must be {REQUIRED}")
        for line_number, row in enumerate(reader, 2):
            if None in row or any(value is None for value in row.values()):
                raise ValueError(f"Malformed EPG mapping at line {line_number}")
            values = {name: row[name].strip() for name in REQUIRED}
            if not all(values.values()):
                raise ValueError(f"Incomplete EPG mapping at line {line_number}")
            target = values["target_tvg_id"]
            if target in holds:
                raise ValueError(f"EPG mapping blocked by unresolved identity hold: {target}: {holds[target]}")
            if target not in targets:
                raise ValueError(f"Unpublished/unrecognised EPG target: {target}")
            if values["source_file"] not in allowed_sources:
                raise ValueError(f"Unrecognised EPG source: {values['source_file']}")
            if target in output:
                raise ValueError(f"Duplicate EPG mapping for {target}")
            if len(values["reason"]) < 12:
                raise ValueError(f"EPG mapping needs a meaningful approval reason: {target}")
            output[target] = EpgCrosswalk(values["source_tvg_id"], values["source_file"],
                                          values["approved_by"], values["reason"])
    return output
