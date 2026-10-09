import gzip
import os
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET

import scripts.build_epg as build_epg


class TestBuildEpg(unittest.TestCase):
    def setUp(self):
        self.tmp_dir = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp_dir.cleanup)
        self.root = Path(self.tmp_dir.name)

        self.playlist_path = self.root / "playlist.m3u"
        self.guide_path = self.root / "channel_guide.csv"
        self.output_path = self.root / "epg.xml.gz"
        self.source_epg_path = self.root / "epg_source.xml.gz"

        # Patch paths in build_epg module
        self.orig_playlist = build_epg.PLAYLIST
        self.orig_guide = build_epg.GUIDE
        self.orig_output = build_epg.OUTPUT
        self.orig_sources = build_epg.EPG_SOURCES

        build_epg.PLAYLIST = self.playlist_path
        build_epg.GUIDE = self.guide_path
        build_epg.OUTPUT = self.output_path
        build_epg.EPG_SOURCES = [self.source_epg_path]

        self.addCleanup(self.restore_paths)

    def restore_paths(self):
        build_epg.PLAYLIST = self.orig_playlist
        build_epg.GUIDE = self.orig_guide
        build_epg.OUTPUT = self.orig_output
        build_epg.EPG_SOURCES = self.orig_sources

    def test_epg_building_clones_elements_correctly(self):
        # Create dummy playlist
        self.playlist_path.write_text(
            '#EXTM3U\n'
            '#EXTINF:-1 tvg-id="ch1" tvg-name="Test Channel 1",Test Channel 1\n'
            'http://example.com/ch1.m3u8\n',
            encoding="utf-8"
        )

        # Create dummy guide
        self.guide_path.write_text(
            'Channel number,Channel name,Genre,Subgenre,EPG,Source,Alternate source,Status,Description\n'
            '101,Test Channel 1,News,Local,Full,Curated,,Active,Test channel description\n',
            encoding="utf-8-sig"
        )

        # Create dummy EPG source
        tv_elem = ET.Element("tv")
        ch_elem = ET.SubElement(tv_elem, "channel", {"id": "ch1"})
        dn_elem = ET.SubElement(ch_elem, "display-name")
        dn_elem.text = "Test Channel 1"

        prog_elem = ET.SubElement(
            tv_elem, "programme",
            {"start": "20250101000000 +0000", "stop": "20250101010000 +0000", "channel": "ch1"}
        )
        t_elem = ET.SubElement(prog_elem, "title")
        t_elem.text = "Breaking News"

        xml_data = ET.tostring(tv_elem, encoding="utf-8", xml_declaration=True)
        with gzip.open(self.source_epg_path, "wb") as f:
            f.write(xml_data)

        # Run the build logic (reload module or execute script logic)
        # We re-evaluate the module's script body logic with updated globals
        playlist = build_epg.playlist_channels()
        guide_genre_by_name = build_epg.guide_genres()

        source_channels = {}
        source_names = {}
        programmes_by_id = {}

        for source in build_epg.EPG_SOURCES:
            if not source.exists():
                continue
            opener = gzip.open if source.suffix == ".gz" else open
            with opener(source, "rb") as handle:
                for event, elem in ET.iterparse(handle, events=("end",)):
                    if elem.tag == "channel":
                        cid = elem.attrib.get("id", "")
                        if cid and cid not in source_channels:
                            cloned = build_epg.copy.deepcopy(elem)
                            source_channels[cid] = cloned
                            names = [
                                (node.text or "").strip()
                                for node in cloned.findall("display-name")
                                if (node.text or "").strip()
                            ]
                            for name in names:
                                key = build_epg.canonical(name)
                                if key:
                                    source_names.setdefault(key, set()).add(cid)
                        elem.clear()
                    elif elem.tag == "programme":
                        cid = elem.attrib.get("channel", "")
                        if cid:
                            programmes_by_id.setdefault(cid, []).append(
                                build_epg.copy.deepcopy(elem)
                            )
                        elem.clear()

        root = ET.Element("tv", {"generator-info-name": "autv"})
        for target_id, target_name in playlist:
            guide_genre = guide_genre_by_name.get(build_epg.canonical(target_name), "")
            android_category = build_epg.ANDROID_GENRE.get(guide_genre, "Entertainment")
            source_id = target_id if target_id in source_channels else None

            if source_id:
                channel = build_epg.copy.deepcopy(source_channels[source_id])
                channel.set("id", target_id)
                root.append(channel)

                for programme in programmes_by_id.get(source_id, []):
                    cloned = build_epg.copy.deepcopy(programme)
                    cloned.set("channel", target_id)
                    build_epg.add_android_category(cloned, android_category)
                    root.append(cloned)

        xml_bytes = ET.tostring(root, encoding="utf-8", xml_declaration=True)
        with gzip.open(self.output_path, "wb", compresslevel=9) as handle:
            handle.write(xml_bytes)

        self.assertTrue(self.output_path.exists())
        with gzip.open(self.output_path, "rb") as handle:
            parsed_root = ET.parse(handle).getroot()

        parsed_channels = parsed_root.findall("channel")
        parsed_programmes = parsed_root.findall("programme")

        self.assertEqual(len(parsed_channels), 1)
        self.assertEqual(parsed_channels[0].attrib["id"], "ch1")
        self.assertEqual(len(parsed_programmes), 1)
        self.assertEqual(parsed_programmes[0].find("title").text, "Breaking News")
        self.assertEqual(parsed_programmes[0].find("category").text, "News")


if __name__ == "__main__":
    unittest.main()
