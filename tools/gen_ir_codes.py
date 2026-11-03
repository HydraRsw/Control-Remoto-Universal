#!/usr/bin/env python3
"""Genera app/src/main/assets/ir_codes.json a partir de una copia local de irdb.

Uso: python3 tools/gen_ir_codes.py /ruta/a/irdb
Datos: https://github.com/probonopd/irdb (ver NOTICE.md)
"""
import csv
import json
import pathlib
import sys

BRANDS = [
    ("lg", "LG", "LG/TV/4,-1.csv", {"NEC1"}),
    ("samsung", "Samsung", "Samsung/TV/7,7.csv", {"NECx2"}),
    ("sony", "Sony", "Sony/TV/1,-1.csv", {"Sony12"}),
    ("panasonic", "Panasonic", "Panasonic/TV/128,0.csv", {"Panasonic"}),
    ("philips", "Philips", "Philips/TV/0,-1.csv", {"RC5"}),
    ("tcl", "TCL", "TCL/TV/15,-1.csv", {"RCA-38"}),
    ("rca", "RCA", "RCA/TV/15,-1.csv", {"RCA-38"}),
    ("toshiba", "Toshiba", "Toshiba/TV/64,-1.csv", {"NEC1"}),
    ("sanyo", "Sanyo", "Sanyo/TV/56,-1.csv", {"NEC1"}),
    ("hitachi", "Hitachi", "Hitachi/TV/80,-1.csv", {"NEC1"}),
    ("insignia", "Insignia", "Insignia/TV/134,5.csv", {"NEC1"}),
]

ALIASES = {
    "POWER": ["POWER", "POWER TOGGLE", "POWER ON/OFF", "POWER ON / OFF"],
    "VOL_UP": ["VOLUME +", "VOLUME+", "VOLUME UP", "VOL+", "VOL +"],
    "VOL_DOWN": ["VOLUME -", "VOLUME DOWN", "VOL-", "VOL -"],
    "MUTE": ["MUTE", "VOLUME MUTE", "VOLUME MUTE TOGGLE"],
    "CH_UP": ["CHANNEL +", "CHANNEL+", "CHANNEL UP", "CH+", "CH +"],
    "CH_DOWN": ["CHANNEL -", "CHANNEL DOWN", "CH-", "CH -"],
    "UP": ["CURSOR UP", "ARROW UP", "UP"],
    "DOWN": ["CURSOR DOWN", "ARROW DOWN", "DOWN"],
    "LEFT": ["CURSOR LEFT", "ARROW LEFT", "LEFT"],
    "RIGHT": ["CURSOR RIGHT", "ARROW RIGHT", "RIGHT"],
    "OK": ["OK", "CURSOR ENTER/SELECT", "CURSOR ENTER", "CURSOR SELECT", "SELECT", "CONFIRM", "OK/ENTER", "ENTER"],
    "BACK": ["RETURN", "BACK", "GO BACK", "EXIT"],
    "HOME": ["SMART HUB", "HOME", "SMART", "HOME/MENU"],
    "MENU": ["MENU"],
    "INPUT": ["INPUT SOURCE", "INPUT SCROLL", "INPUT", "INPUT SELECT/SCROLL", "TV/AV", "TV/VIDEO"],
    "PLAY_PAUSE": ["PLAY PAUSE", "PLAY", "PLAY/PAUSE"],
    "STOP": ["STOP"],
    "REWIND": ["SEEK BACK", "REWIND", "REW"],
    "FORWARD": ["SEEK FORWARD", "FAST FORWARD", "FF", "FWD"],
}
for n in range(10):
    ALIASES[f"NUM_{n}"] = [str(n), f"KEY {n}", f"DIGIT {n}", f"DIGIT {n}/10"]


def build(root: pathlib.Path):
    brands = []
    for brand_id, name, rel, protocols in BRANDS:
        rows = [r for r in csv.DictReader((root / "codes" / rel).open(encoding="utf-8"))
                if r["protocol"] in protocols]
        by_name = {}
        for row in rows:
            by_name.setdefault(row["functionname"].strip().upper(), row)
        keys = {}
        for key, names in ALIASES.items():
            row = next((by_name[n] for n in names if n in by_name), None)
            if row:
                keys[key] = int(row["function"])
        first = rows[0]
        brands.append({
            "id": brand_id,
            "name": name,
            "protocol": first["protocol"],
            "device": int(first["device"]),
            "subdevice": int(first["subdevice"]),
            "keys": keys,
        })
        missing = sorted(set(ALIASES) - set(keys))
        print(f"{name}: {len(keys)}/{len(ALIASES)} teclas; faltan: {missing}", file=sys.stderr)
    return {"source": "irdb", "brands": brands}


if __name__ == "__main__":
    data = build(pathlib.Path(sys.argv[1]))
    out = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/assets/ir_codes.json"
    out.write_text(json.dumps(data, ensure_ascii=False, indent=1, sort_keys=True) + "\n", encoding="utf-8")
