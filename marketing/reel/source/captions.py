"""Word-timed caption chunks: script words (exact spelling) timed by the Whisper word timestamps."""
import os
E = os.path.join(os.environ.get('REEL_DIR', os.path.expanduser('~/.cache/donutessentials-smoke/reel')), "edit")
import json, re
T = json.load(open(E + "/timeline.json"))
W = json.load(open(E + "/words.json"))
CUTS = [x["start"] for x in json.load(open(E + "/edl.json"))["shots"][1:]]
SKIP = {10, 11}      # shown as the platform pills and the title card instead
MAXW = {}
chunks = []
for line in T["lines"]:
    toks = line["cap"].upper().replace("...", "…").split()
    ws = [w for w in W if w["start"] >= line["start"] - 0.25 and w["end"] <= line["end"] + 0.35]
    if len(ws) == len(toks):
        times = [(w["start"], w["end"]) for w in ws]
    else:  # spread the line's duration by character count
        total = sum(len(t) for t in toks); t0 = line["start"]; times = []
        for t in toks:
            d = (line["end"] - line["start"]) * len(t) / total; times.append((t0, t0 + d)); t0 += d
    if line["i"] in SKIP:
        continue
    words = [{"text": t, "start": round(a, 3), "end": round(b, 3)} for t, (a, b) in zip(toks, times)]
    n = MAXW.get(line["i"], 3)
    # break into chunks of up to n words, preferring breaks after punctuation
    cur = []
    for k, w in enumerate(words):
        cur.append(w)
        nxt = words[k + 1]["text"] if k + 1 < len(words) else ""
        closes_next = bool(re.search(r"[.,?!…]$", nxt))
        if re.search(r"[.,?!…]$", w["text"]) or (len(cur) >= n and not (closes_next and len(cur) < 4)):
            chunks.append(cur); cur = []
    if cur:
        chunks.append(cur)
    # never end a chunk on a dangling function word
    WEAK = {"OF", "TO", "THE", "A", "YOUR", "WHO", "THAT", "IT", "AND", "WITH"}
    for k in range(len(chunks) - 1):
        if len(chunks[k]) > 1 and chunks[k][-1]["text"] in WEAK and len(chunks[k + 1]) < 4 and chunks[k + 1][0]["start"] < line["end"]:
            chunks[k + 1].insert(0, chunks[k].pop())
out = []
for i, c in enumerate(chunks):
    nxt = chunks[i + 1][0]["start"] if i + 1 < len(chunks) else c[-1]["end"] + 0.6
    end = min(c[-1]["end"] + 0.45, nxt)
    # never run into the next voice line (even one shown as a graphic) or across a cut
    later = [l["start"] for l in T["lines"] if l["start"] > c[-1]["end"]]
    if later:
        end = min(end, later[0] - 0.03)
    end = min([end] + [cut for cut in CUTS if c[-1]["end"] < cut < end])
    out.append({"start": c[0]["start"], "end": round(end, 3), "words": c})
json.dump(out, open(E + "/captions.json", "w"), indent=1)
for c in out:
    print(f'{c["start"]:6.2f}-{c["end"]:6.2f}  ' + " ".join(w["text"] for w in c["words"]))
