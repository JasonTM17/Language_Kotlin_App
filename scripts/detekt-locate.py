import xml.etree.ElementTree as ET
import sys

tree = ET.parse(sys.argv[1])
# No rule argument -> summarise every rule, then list every finding.
rule = sys.argv[2] if len(sys.argv) > 2 else None
rows = []
for f in tree.iter("file"):
    path = f.get("name", "").replace("\\", "/")
    short = path.split("/server/")[-1]
    for e in f.iter("error"):
        r = e.get("source", "?").split(".")[-1]
        if rule is None or r == rule:
            rows.append((r, short, int(e.get("line")), e.get("message", "")))

if rule is None:
    counts = {}
    for r, _, _, _ in rows:
        counts[r] = counts.get(r, 0) + 1
    for r, n in sorted(counts.items(), key=lambda kv: -kv[1]):
        print("%4d  %s" % (n, r))
    print()

for r, p, l, m in sorted(rows):
    print("%-24s %s:%d  %s" % (r, p, l, m[:90]))
print()
print("total", len(rows))
