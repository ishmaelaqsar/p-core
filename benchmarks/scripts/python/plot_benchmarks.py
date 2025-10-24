#!/usr/bin/env python3
"""
Usage:
    python interactive_benchmarks.py --file benchmarks.txt
"""

import re
import os
import sys
import argparse
import itertools
import pandas as pd
import matplotlib.pyplot as plt
from matplotlib.widgets import CheckButtons, Button
import mplcursors

# -------------------------------------
# Parsing and extraction helpers
# -------------------------------------
LINE_RE = re.compile(r"(\S+)\s+(\d+)\s+\S+\s+\d+\s+([\d\.]+)")

def parse_bench_text(text: str):
    records = []
    for line in text.splitlines():
        m = LINE_RE.search(line)
        if m:
            benchmark, size, score = m.groups()
            records.append((benchmark, int(size), float(score)))
    return records

def method_from_shortname(shortname: str):
    KNOWN = [
        "indexOf_miss", "indexOf_hit", "indexOf",
        "contains", "randomGet", "iteration",
        "sequentialAdd", "sequentialGet",
        "memoryFootprint", "mixedWorkload", "pooledIterator"
    ]
    for op in sorted(KNOWN, key=len, reverse=True):
        if op.lower() in shortname.lower():
            return op
    if "_" in shortname:
        parts = shortname.split("_", 1)
        return parts[1]
    if "." in shortname:
        return shortname.split(".")[-1]
    return shortname

def list_type_from_shortname(shortname: str):
    # Extract base type name before first underscore
    parts = shortname.split("_", 1)[0]
    return parts.replace(".", "").strip()

# -------------------------------------
# CLI setup
# -------------------------------------
parser = argparse.ArgumentParser()
parser.add_argument("--file", "-f", default="benchmarks.txt",
                    help="Benchmark output file (use '-' for stdin). Default: benchmarks.txt")
args = parser.parse_args()

raw = sys.stdin.read() if args.file == "-" else open(args.file, encoding="utf-8").read()
records = parse_bench_text(raw)
if not records:
    print("No benchmark lines parsed. Check file format.", file=sys.stderr)
    sys.exit(1)

# -------------------------------------
# DataFrame setup
# -------------------------------------
df = pd.DataFrame(records, columns=["Benchmark", "Size", "Score"])
prefix = os.path.commonprefix(df["Benchmark"].tolist())
df["ShortName"] = df["Benchmark"].str.replace(prefix, "", regex=False)
df["Method"] = df["ShortName"].apply(method_from_shortname)
df["ListType"] = df["ShortName"].apply(list_type_from_shortname)
df.sort_values(["ListType", "Method", "ShortName"], inplace=True)

methods = sorted(df["Method"].unique())
method_to_shortnames = {m: list(grp["ShortName"]) for m, grp in df.groupby("Method")}
list_types = sorted(df["ListType"].unique())

# -------------------------------------
# Plot setup
# -------------------------------------
plt.style.use("default")
fig, ax = plt.subplots(figsize=(15, 9))
ax.set_xscale("log")
ax.set_yscale("log")
ax.set_xlabel("Size (log scale)")
ax.set_ylabel("Score (ns/op, log scale)")
ax.set_title(prefix.rstrip(".") or "Benchmarks")
ax.grid(True, which="both", ls="--", lw=0.5)

# -------------------------------------
# Color coding per list type
# -------------------------------------
color_map = plt.cm.tab10
colors = {lt: color_map(i / max(1, len(list_types)-1)) for i, lt in enumerate(list_types)}
markers = ["o", "s", "^", "v", "D", "x", "*", "P", "h", "+"]
linestyles = ["-", "--", "-.", ":"]

# -------------------------------------
# Draw all lines
# -------------------------------------
lines = {}
for name, group in df.groupby("ShortName"):
    list_type = group["ListType"].iloc[0]
    method = group["Method"].iloc[0]
    color = colors[list_type]
    marker = markers[hash(method) % len(markers)]
    ls = linestyles[hash(name) % len(linestyles)]

    ln, = ax.plot(
        group["Size"], group["Score"],
        label=name,
        color=color,
        marker=marker,
        linestyle=ls,
        linewidth=1.8,
        markersize=6,
        alpha=0.9
    )
    lines[name] = ln

# -------------------------------------
# Filter box layout
# -------------------------------------
fig_w, fig_h = fig.get_size_inches()
dpi = fig.dpi
fig_w_px = fig_w * dpi
char_px = 7
max_label_len = max(len(m) for m in methods)
desired_px = min(max(100, max_label_len * char_px + 30), int(fig_w_px * 0.25))
box_width_frac = max(0.12, min(desired_px / fig_w_px, 0.25))
box_height_frac = min(0.04 * len(methods), 0.7)
box_left, box_bottom = 0.02, 0.12
plt.subplots_adjust(left=box_left + box_width_frac + 0.03, right=0.88)

# -------------------------------------
# Method filter buttons
# -------------------------------------
visibility = [False] * len(methods)
rax = plt.axes([box_left, box_bottom, box_width_frac, box_height_frac])
check = CheckButtons(rax, methods, visibility)

def update_legend():
    visible_lines = [ln for ln in lines.values() if ln.get_visible()]
    visible_labels = [ln.get_label() for ln in visible_lines]
    if ax.get_legend() is not None:
        ax.get_legend().remove()
    if visible_lines:
        ax.legend(visible_lines, visible_labels, bbox_to_anchor=(1.01, 1),
                  loc="upper left", fontsize="small", frameon=False)
    fig.canvas.draw_idle()

def on_method_toggle(label):
    # Toggle visibility for this method group
    targets = method_to_shortnames[label]
    any_visible = any(lines[s].get_visible() for s in targets)
    for s in targets:
        lines[s].set_visible(not any_visible)
    update_legend()

check.on_clicked(on_method_toggle)

# -------------------------------------
# Select/Unselect All buttons
# -------------------------------------
btn_h = 0.05
btn_w = box_width_frac
select_ax = plt.axes([box_left, box_bottom - 0.06, btn_w, btn_h])
unselect_ax = plt.axes([box_left + btn_w + 0.01, box_bottom - 0.06, btn_w, btn_h])
btn_select = Button(select_ax, "Select All")
btn_unselect = Button(unselect_ax, "Unselect All")

def select_all(event):
    for ln in lines.values():
        ln.set_visible(True)
    update_legend()

def unselect_all(event):
    for ln in lines.values():
        ln.set_visible(False)
    update_legend()

btn_select.on_clicked(select_all)
btn_unselect.on_clicked(unselect_all)

# -------------------------------------
# Hover tooltips (rich format)
# -------------------------------------
cursor = mplcursors.cursor(ax.get_lines(), hover=True)

@cursor.connect("add")
def on_hover(sel):
    name = sel.artist.get_label()
    list_type = df.loc[df["ShortName"] == name, "ListType"].iloc[0]
    method = df.loc[df["ShortName"] == name, "Method"].iloc[0]
    x, y = sel.target
    sel.annotation.set_text(
        f"**{list_type}**\nMethod: {method}\nSize: {int(x)}\nScore: {y:.3f} ns/op"
    )
    sel.annotation.get_bbox_patch().set(fc="white", alpha=0.85, ec=colors[list_type])

# -------------------------------------
# Start with all visible
# -------------------------------------
select_all(None)
plt.show()
