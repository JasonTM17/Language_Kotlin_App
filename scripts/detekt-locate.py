import xml.etree.ElementTree as ET
import sys

tree = ET.parse(sys.argv[1])
rule = sys.argv[2] if len(sys.argv) > 2 else "MaxLineLength"
rows = []
for f in tree.iter("file"):
    path = f.get("name", "").replace("\\", "/")
    short = path.split("/server/")[-1]
    for e in f.iter("error"):
        if e.get("source", "?").split(".")[-1] == rule:
            rows.append((short, int(e.get("line"))))
for p, l in sorted(rows):
    print("%s:%d" % (p, l))
print()
print("total", len(rows))
