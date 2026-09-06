"""
用法：python tools/framestats_summary.py <framestats.txt> [<framestats.txt> ...]
解析 `adb shell dumpsys gfxinfo <pkg> framestats` 的 PROFILEDATA 段（最近 120 帧的环形缓冲，所以每次交互单独 dump）。
阶段（ms）：
  input  = ANIMATION_START - HANDLE_INPUT_START
  anim   = PERFORM_TRAVERSALS_START - ANIMATION_START      （Choreographer 动画回调：Compose 帧时钟 → 重组多数落在这里）
  layout = DRAW_START - PERFORM_TRAVERSALS_START            （measure / layout）
  draw   = SYNC_QUEUED - DRAW_START                         （录制 display list）
  sync   = ISSUE_DRAW_COMMANDS_START - SYNC_START
  gpu    = SWAP_BUFFERS - ISSUE_DRAW_COMMANDS_START         （RenderThread 发 GPU 命令；saveLayer 成本落在这里与 draw）
  total  = FRAME_COMPLETED - INTENDED_VSYNC
列名按 dumpsys 头行匹配（IntendedVsync 等驼峰名），大小写与下划线不敏感；头行与数据行的尾随逗号忽略。
另输出：首帧（第一个 Flags==0 的帧）是否超预算、动画期间连续超预算的最长帧数——分别对应"首帧长"与"中途掉帧"。
"""
import sys

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

BUDGET_MS = float(sys.argv[sys.argv.index("--budget") + 1]) if "--budget" in sys.argv else 8.3
files = [a for a in sys.argv[1:] if a.endswith(".txt")]

stages = {
    "input": ("HANDLE_INPUT_START", "ANIMATION_START"),
    "anim": ("ANIMATION_START", "PERFORM_TRAVERSALS_START"),
    "layout": ("PERFORM_TRAVERSALS_START", "DRAW_START"),
    "draw": ("DRAW_START", "SYNC_QUEUED"),
    "sync": ("SYNC_START", "ISSUE_DRAW_COMMANDS_START"),
    "gpu": ("ISSUE_DRAW_COMMANDS_START", "SWAP_BUFFERS"),
    "total": ("INTENDED_VSYNC", "FRAME_COMPLETED"),
}


def norm(name):
    return name.replace("_", "").lower()


def parse(path):
    cols, rows, in_data = {}, [], False
    with open(path, encoding="utf-8", errors="ignore") as f:
        for line in f:
            line = line.strip()
            if line == "---PROFILEDATA---":
                in_data = not in_data
                continue
            if not in_data:
                continue
            parts = line.split(",")
            if parts[0] == "Flags":
                cols = {norm(name): i for i, name in enumerate(parts) if name != ""}
                continue
            try:
                vals = [int(p) for p in parts if p != ""]
            except ValueError:
                continue
            if len(vals) >= len(cols):
                rows.append(vals)
    return cols, [r for r in rows if r[cols[norm("Flags")]] == 0]


def ms(cols, row, a, b):
    return (row[cols[norm(b)]] - row[cols[norm(a)]]) / 1e6


def pct(xs, p):
    xs = sorted(xs)
    return xs[min(len(xs) - 1, int(round(p / 100 * (len(xs) - 1))))] if xs else 0.0


for path in files:
    cols, rows = parse(path)
    print(f"\n== {path}: {len(rows)} frames, budget {BUDGET_MS} ms")
    print(f"{'stage':8} {'p50':>7} {'p90':>7} {'p95':>7} {'p99':>7} {'max':>7}")
    for name, (a, b) in stages.items():
        xs = [ms(cols, r, a, b) for r in rows]
        print(f"{name:8} {pct(xs, 50):7.1f} {pct(xs, 90):7.1f} {pct(xs, 95):7.1f} {pct(xs, 99):7.1f} {(max(xs) if xs else 0):7.1f}")
    totals = [ms(cols, r, "INTENDED_VSYNC", "FRAME_COMPLETED") for r in rows]
    first_over = totals[0] > BUDGET_MS if totals else False
    run, longest = 0, 0
    for t in totals:
        run = run + 1 if t > BUDGET_MS else 0
        longest = max(longest, run)
    print(f"首帧超预算: {first_over} (first={totals[0] if totals else 0:.1f} ms)   最长连续超预算: {longest} 帧")
    worst = sorted(rows, key=lambda r: ms(cols, r, "INTENDED_VSYNC", "FRAME_COMPLETED"), reverse=True)[:3]
    for r in worst:
        print("  worst: " + " ".join(f"{k}={ms(cols, r, a, b):.1f}" for k, (a, b) in stages.items()))
