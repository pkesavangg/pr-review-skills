#!/usr/bin/env python3
import argparse
import csv
import datetime as dt
import html
import json
import subprocess
import sys
from pathlib import Path
from typing import Dict, Iterable, List, Tuple

EXCLUDED_UI_SEGMENTS = {"Views", "ViewModifiers", "Modifiers", "Previews"}
EXCLUDED_UI_SUFFIXES = (
    "View.swift",
    "Modifier.swift",
    "Screen.swift",
    "UIKitView.swift",
    "Cell.swift",
)


def coverage_scope_label() -> str:
    return "meApp/**/*.swift (excluding UI views/modifiers)"


def run_command(cmd: List[str]) -> str:
    proc = subprocess.run(cmd, capture_output=True, text=True)
    if proc.returncode != 0:
        raise RuntimeError(f"Command failed: {' '.join(cmd)}\n{proc.stderr.strip()}")
    return proc.stdout


def find_latest_xcresult(repo_root: Path) -> Path:
    candidates: List[Path] = []

    local_test_logs = repo_root / "DerivedData" / "Logs" / "Test"
    if local_test_logs.exists():
        candidates.extend(local_test_logs.glob("*.xcresult"))

    global_derived_data = Path.home() / "Library" / "Developer" / "Xcode" / "DerivedData"
    if global_derived_data.exists():
        candidates.extend(global_derived_data.glob("**/Logs/Test/*.xcresult"))

    candidates = [path for path in candidates if path.exists()]
    if not candidates:
        raise FileNotFoundError(
            "No .xcresult found. Run tests with coverage first, then retry with --xcresult <path>."
        )

    candidates.sort(key=lambda path: path.stat().st_mtime, reverse=True)
    return candidates[0]


def collect_file_entries(node: dict) -> Iterable[dict]:
    files = node.get("files", [])
    for entry in files:
        yield entry
        if entry.get("files"):
            yield from collect_file_entries(entry)


def to_repo_relative(path: str, repo_root: Path) -> str:
    path_obj = Path(path)
    if not path_obj.is_absolute():
        return path.replace("\\", "/")

    try:
        return path_obj.relative_to(repo_root).as_posix()
    except ValueError:
        return path_obj.as_posix()


def format_percent(value: float) -> str:
    return f"{value:.2f}%"


def format_number(value: float) -> str:
    return f"{value:.2f}"


def is_excluded_ui_file(relative_path: str) -> bool:
    path = Path(relative_path)
    if any(part in EXCLUDED_UI_SEGMENTS for part in path.parts):
        return True
    return path.name.endswith(EXCLUDED_UI_SUFFIXES)


def parse_report_data(report: dict, repo_root: Path) -> Tuple[List[Dict[str, float]], float, int, float]:
    aggregated: Dict[str, Dict[str, float]] = {}

    for target in report.get("targets", []):
        for file_entry in collect_file_entries(target):
            file_path = file_entry.get("path") or file_entry.get("name")
            if not file_path:
                continue

            relative_path = to_repo_relative(file_path, repo_root)

            if not relative_path.endswith(".swift"):
                continue
            if not relative_path.startswith("meApp/"):
                continue
            if is_excluded_ui_file(relative_path):
                continue

            executable_lines = float(file_entry.get("executableLines", 0) or 0)
            line_coverage = float(file_entry.get("lineCoverage", 0) or 0)
            covered_lines = line_coverage * executable_lines

            if relative_path not in aggregated:
                aggregated[relative_path] = {"covered": 0.0, "executable": 0.0}

            aggregated[relative_path]["covered"] += covered_lines
            aggregated[relative_path]["executable"] += executable_lines

    rows: List[Dict[str, float]] = []
    total_executable = 0
    total_covered = 0.0

    for relative_path, values in aggregated.items():
        executable = int(round(values["executable"]))
        covered = values["covered"]
        coverage_ratio = covered / executable if executable > 0 else 0.0

        rows.append(
            {
                "file": relative_path,
                "covered_lines": covered,
                "executable_lines": executable,
                "coverage_percent": coverage_ratio * 100,
            }
        )

        total_executable += executable
        total_covered += covered

    rows.sort(key=lambda row: row["coverage_percent"], reverse=True)
    total_percent = (total_covered / total_executable * 100) if total_executable else 0.0

    return rows, total_covered, total_executable, total_percent


def write_csv(rows: List[Dict[str, float]], out_path: Path) -> None:
    with out_path.open("w", newline="", encoding="utf-8") as file_handle:
        writer = csv.writer(file_handle)
        writer.writerow(["file", "covered_lines", "executable_lines", "coverage_percent"])
        for row in rows:
            writer.writerow(
                [
                    row["file"],
                    format_number(row["covered_lines"]),
                    int(row["executable_lines"]),
                    format_percent(row["coverage_percent"]),
                ]
            )


def write_markdown(
    rows: List[Dict[str, float]],
    out_path: Path,
    xcresult_path: Path,
    total_covered: float,
    total_executable: int,
    total_percent: float,
) -> None:
    generated_at = dt.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    scope = coverage_scope_label()

    with out_path.open("w", encoding="utf-8") as file_handle:
        file_handle.write("# iOS Unit Test Coverage Report\n\n")
        file_handle.write(f"- Generated: {generated_at}\n")
        file_handle.write(f"- Source: `{xcresult_path}`\n")
        file_handle.write(f"- Swift files included: {len(rows)} (`{scope}`)\n")
        file_handle.write(f"- App-only covered lines (`{scope}`): {format_number(total_covered)}\n")
        file_handle.write(f"- App-only executable lines (`{scope}`): {total_executable}\n")
        file_handle.write(f"- App-only coverage: **{format_percent(total_percent)}**\n\n")

        file_handle.write("## Per-File Coverage\n\n")
        file_handle.write("| File | Covered Lines | Executable Lines | Coverage |\n")
        file_handle.write("| --- | ---: | ---: | ---: |\n")

        for row in rows:
            file_handle.write(
                "| {file} | {covered} | {executable} | {coverage} |\n".format(
                    file=row["file"],
                    covered=format_number(row["covered_lines"]),
                    executable=int(row["executable_lines"]),
                    coverage=format_percent(row["coverage_percent"]),
                )
            )


def write_html(
    rows: List[Dict[str, float]],
    out_path: Path,
    xcresult_path: Path,
    total_covered: float,
    total_executable: int,
    total_percent: float,
) -> None:
    generated_at = dt.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    scope = coverage_scope_label()
    table_rows = []
    for row in rows:
        table_rows.append(
            "<tr>"
            f"<td>{html.escape(str(row['file']))}</td>"
            f"<td class='num'>{format_number(row['covered_lines'])}</td>"
            f"<td class='num'>{int(row['executable_lines'])}</td>"
            f"<td class='num'><strong>{format_percent(row['coverage_percent'])}</strong></td>"
            "</tr>"
        )

    html_content = f"""<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>iOS Coverage Report</title>
  <style>
    :root {{
      color-scheme: light;
      --bg: #f6f8fb;
      --card: #ffffff;
      --text: #1f2937;
      --muted: #6b7280;
      --border: #d7dce3;
      --accent: #0b6e4f;
    }}
    body {{
      margin: 0;
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
      color: var(--text);
      background: linear-gradient(135deg, #f8fbff, var(--bg));
    }}
    .container {{
      max-width: 1100px;
      margin: 0 auto;
      padding: 24px;
    }}
    .card {{
      background: var(--card);
      border: 1px solid var(--border);
      border-radius: 12px;
      padding: 16px 18px;
      margin-bottom: 16px;
      box-shadow: 0 4px 16px rgba(15, 23, 42, 0.04);
    }}
    h1 {{
      margin: 0 0 8px;
      font-size: 1.45rem;
    }}
    .muted {{
      color: var(--muted);
      margin: 4px 0;
      font-size: 0.95rem;
    }}
    .metric {{
      font-size: 1.1rem;
      font-weight: 600;
      color: var(--accent);
    }}
    table {{
      width: 100%;
      border-collapse: collapse;
      font-size: 0.92rem;
    }}
    thead th {{
      position: sticky;
      top: 0;
      background: #f2f5fa;
    }}
    th, td {{
      border: 1px solid var(--border);
      padding: 8px 10px;
      text-align: left;
    }}
    .num {{
      text-align: right;
      white-space: nowrap;
    }}
  </style>
</head>
<body>
  <div class="container">
    <div class="card">
      <h1>iOS Unit Test Coverage Report</h1>
      <p class="muted">Generated: {generated_at}</p>
      <p class="muted">Source: {html.escape(str(xcresult_path))}</p>
      <p class="muted">Swift files included: {len(rows)} ({html.escape(scope)})</p>
      <p class="muted">App-only covered lines ({html.escape(scope)}): {format_number(total_covered)}</p>
      <p class="muted">App-only executable lines ({html.escape(scope)}): {total_executable}</p>
      <p class="metric">App-only coverage: {format_percent(total_percent)}</p>
    </div>
    <div class="card">
      <table>
        <thead>
          <tr>
            <th>File</th>
            <th class="num">Covered Lines</th>
            <th class="num">Executable Lines</th>
            <th class="num">Coverage</th>
          </tr>
        </thead>
        <tbody>
          {''.join(table_rows)}
        </tbody>
      </table>
    </div>
  </div>
</body>
</html>
"""

    out_path.write_text(html_content, encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Export Swift per-file and total coverage from an Xcode .xcresult bundle."
    )
    parser.add_argument(
        "--xcresult",
        help="Path to .xcresult bundle. If omitted, the script picks the latest available bundle.",
    )
    parser.add_argument(
        "--output-dir",
        default="meAppTests/Reports",
        help="Directory for generated reports (default: meAppTests/Reports).",
    )
    parser.add_argument(
        "--output-prefix",
        default="coverage-report",
        help="Base file name for output files (default: coverage-report).",
    )

    args = parser.parse_args()

    repo_root = Path(__file__).resolve().parent.parent
    output_dir = (repo_root / args.output_dir).resolve()
    output_dir.mkdir(parents=True, exist_ok=True)

    try:
        if args.xcresult:
            xcresult_path = Path(args.xcresult).expanduser().resolve()
        else:
            xcresult_path = find_latest_xcresult(repo_root)
    except FileNotFoundError as error:
        print(str(error), file=sys.stderr)
        return 1

    if not xcresult_path.exists() or xcresult_path.suffix != ".xcresult":
        print(f"Invalid xcresult path: {xcresult_path}", file=sys.stderr)
        return 1

    try:
        report_json = run_command(["xcrun", "xccov", "view", "--report", "--json", str(xcresult_path)])
        report = json.loads(report_json)
    except Exception as error:
        print(str(error), file=sys.stderr)
        return 1

    rows, total_covered, total_executable, total_percent = parse_report_data(report, repo_root)
    if not rows:
        print(
            "No Swift source coverage found under filtered meApp scope. Ensure coverage is enabled and tests were run.",
            file=sys.stderr,
        )
        return 1

    csv_path = output_dir / f"{args.output_prefix}.csv"
    md_path = output_dir / f"{args.output_prefix}.md"
    html_path = output_dir / f"{args.output_prefix}.html"

    write_csv(rows, csv_path)
    write_markdown(
        rows,
        md_path,
        xcresult_path,
        total_covered,
        total_executable,
        total_percent,
    )
    write_html(
        rows,
        html_path,
        xcresult_path,
        total_covered,
        total_executable,
        total_percent,
    )

    print(f"Coverage CSV: {csv_path}")
    print(f"Coverage Markdown: {md_path}")
    print(f"Coverage HTML: {html_path}")
    print(f"App-only coverage ({coverage_scope_label()}): {format_percent(total_percent)}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
