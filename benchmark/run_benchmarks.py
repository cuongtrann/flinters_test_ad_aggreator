#!/usr/bin/env python3
import csv
import os
import re
import subprocess
import time
from datetime import datetime
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
JAR = ROOT / "target" / "aggregator.jar"
REAL_INPUT = ROOT / "ad_data.csv"
TEMP_ROOT = Path("/private/tmp/adperf_benchmark")
DATA_DIR = TEMP_ROOT / "data"
RESULTS_DIR = TEMP_ROOT / "results"
LOG_FILE = ROOT / "benchmark" / "benchmark_log.md"
JAVA_BIN = Path(os.environ["JAVA_HOME"]) / "bin" / "java" if os.environ.get("JAVA_HOME") else None

GENERATED_DATASETS = [
    ("generated_1k", 1_000, 10),
    ("generated_10k", 10_000, 50),
    ("generated_100k", 100_000, 500),
    ("generated_500k", 500_000, 1_000),
    ("generated_1m", 1_000_000, 5_000),
]
REPEATS = 5


def run(cmd: list[str], cwd: Path = ROOT) -> subprocess.CompletedProcess:
    return subprocess.run(cmd, cwd=cwd, text=True, capture_output=True, check=True)


def build_jar() -> None:
    run(["mvn", "package", "-DskipTests"])


def java_bin() -> str:
    if JAVA_BIN and JAVA_BIN.exists():
        return str(JAVA_BIN)
    try:
        java_home = run(["/usr/libexec/java_home", "-v", "21"]).stdout.strip()
        candidate = Path(java_home) / "bin" / "java"
        if candidate.exists():
            return str(candidate)
    except (subprocess.CalledProcessError, FileNotFoundError):
        pass
    return "java"


def generate_dataset(name: str, rows: int, campaigns: int) -> Path:
    path = DATA_DIR / f"{name}.csv"
    if path.exists():
        return path

    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(["campaign_id", "date", "impressions", "clicks", "spend", "conversions"])
        for i in range(rows):
            campaign = (i % campaigns) + 1
            impressions = 1_000 + (i % 100_000)
            clicks = i % max(1, impressions // 10)
            spend = round(10.0 + ((i * 17) % 100_000) / 100, 2)
            conversions = i % max(1, clicks // 5 + 1)
            writer.writerow([
                f"CMP{campaign:05d}",
                f"2024-{(i % 12) + 1:02d}-{(i % 28) + 1:02d}",
                impressions,
                clicks,
                spend,
                conversions,
            ])
    return path


def file_size_gb(path: Path) -> float:
    return path.stat().st_size / (1024 ** 3)


def count_lines(path: Path) -> int:
    output = run(["wc", "-l", str(path)]).stdout.strip()
    return int(output.split()[0])


def parse_stdout(stdout: str) -> dict[str, str]:
    patterns = {
        "processed": r"Rows processed\s+:\s+([\d,]+)",
        "skipped": r"Rows skipped\s+:\s+([\d,]+)",
        "unique": r"Unique campaigns:\s+([\d,]+)",
        "app_time": r"Time elapsed\s+:\s+([\d.]+)s",
        "peak_heap_mib": r"Peak heap used\s+:\s+([\d.]+) MiB",
    }
    parsed = {}
    for key, pattern in patterns.items():
        match = re.search(pattern, stdout)
        parsed[key] = match.group(1).replace(",", "") if match else ""
    return parsed


def benchmark(dataset_name: str, input_path: Path, repeat: int, rows: int, size_gb: float) -> dict[str, str]:
    output_dir = RESULTS_DIR / dataset_name / f"run_{repeat:02d}"
    output_dir.mkdir(parents=True, exist_ok=True)

    start = time.perf_counter()
    completed = run([java_bin(), "-jar", str(JAR), "--input", str(input_path), "--output", str(output_dir)])
    wall_seconds = time.perf_counter() - start

    parsed = parse_stdout(completed.stdout)
    app_seconds = float(parsed["app_time"]) if parsed["app_time"] else wall_seconds
    measured_seconds = app_seconds if app_seconds > 0 else wall_seconds
    throughput_mb_s = (size_gb * 1024) / measured_seconds if measured_seconds > 0 else 0.0
    rows_s = rows / measured_seconds if measured_seconds > 0 else 0.0
    result = "startup-bound" if rows < 100_000 else "fast" if rows_s >= 1_000_000 else "ok"

    return {
        "dataset": dataset_name,
        "run": str(repeat),
        "rows": f"{rows}",
        "size_gb": f"{size_gb:.4f}",
        "unique_campaigns": parsed["unique"],
        "skipped_rows": parsed["skipped"],
        "app_seconds": f"{app_seconds:.3f}",
        "peak_heap_mib": parsed["peak_heap_mib"],
        "throughput_mb_s": f"{throughput_mb_s:.1f}",
        "rows_s": f"{rows_s:.0f}",
        "result": result,
    }


def write_log(results: list[dict[str, str]]) -> None:
    generated_at = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    by_dataset: dict[str, list[dict[str, str]]] = {}
    for row in results:
        by_dataset.setdefault(row["dataset"], []).append(row)

    lines = [
        "# Benchmark Log - Ad Performance Aggregator",
        "",
        "Raw benchmark runs across generated datasets and the real input file.",
        "",
        "## Environment",
        "",
        f"- Generated at: {generated_at}",
        "- Command per run: `java -jar target/aggregator.jar --input <csv> --output <dir>`",
        f"- Java executable: `{java_bin()}`",
        "- Generated benchmark CSVs are written under `/private/tmp/adperf_benchmark/data`.",
        "- Result files are written under `/private/tmp/adperf_benchmark/results`.",
        "- App time is reported by the CLI and used as the runtime metric.",
        "- Tiny startup-bound runs can report 0.0s because CLI output is rounded to one decimal.",
        "",
        "## Datasets",
        "",
        "| Dataset | Rows | Size GB | Runs |",
        "|---|---:|---:|---:|",
    ]

    for dataset, rows in by_dataset.items():
        first = rows[0]
        lines.append(f"| `{dataset}` | {int(first['rows']):,} | {first['size_gb']} | {len(rows)} |")

    lines.extend([
        "",
        "## Raw Runs",
        "",
        "| Dataset | Run | Rows | Size GB | Unique Campaigns | Skipped Rows | App Time (s) | Peak Heap (MiB) | MB/s | Rows/s | Result |",
        "|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|",
    ])

    for row in results:
        lines.append(
            f"| `{row['dataset']}` | {row['run']} | {int(row['rows']):,} | {row['size_gb']} | "
            f"{int(row['unique_campaigns']):,} | {int(row['skipped_rows']):,} | "
            f"{row['app_seconds']} | {row['peak_heap_mib']} | {row['throughput_mb_s']} | "
            f"{int(row['rows_s']):,} | {row['result']} |"
        )

    lines.extend([
        "",
        "## Summary by Dataset",
        "",
        "| Dataset | Runs | Rows | Size GB | Avg App Time (s) | Best App Time (s) | Max Peak Heap (MiB) | Avg MB/s | Avg Rows/s |",
        "|---|---:|---:|---:|---:|---:|---:|---:|---:|",
    ])

    for dataset, rows in by_dataset.items():
        app_times = [float(row["app_seconds"]) for row in rows if float(row["app_seconds"]) > 0]
        summary_times = app_times if app_times else [0.0]
        peak_heaps = [float(row["peak_heap_mib"]) for row in rows]
        throughputs = [float(row["throughput_mb_s"]) for row in rows]
        row_rates = [float(row["rows_s"]) for row in rows]
        first = rows[0]
        lines.append(
            f"| `{dataset}` | {len(rows)} | {int(first['rows']):,} | {first['size_gb']} | "
            f"{sum(summary_times) / len(summary_times):.3f} | {min(summary_times):.3f} | "
            f"{max(peak_heaps):.1f} | {sum(throughputs) / len(throughputs):.1f} | "
            f"{sum(row_rates) / len(row_rates):,.0f} |"
        )

    LOG_FILE.write_text("\n".join(lines) + "\n")


def main() -> None:
    if not REAL_INPUT.exists():
        raise SystemExit(f"Real input file not found: {REAL_INPUT}")

    build_jar()
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    RESULTS_DIR.mkdir(parents=True, exist_ok=True)

    datasets = []
    for name, rows, campaigns in GENERATED_DATASETS:
        path = generate_dataset(name, rows, campaigns)
        datasets.append((name, path, rows, file_size_gb(path)))

    real_rows = count_lines(REAL_INPUT) - 1
    datasets.append(("real_995mb", REAL_INPUT, real_rows, file_size_gb(REAL_INPUT)))

    results = []
    for name, path, rows, size_gb in datasets:
        for repeat in range(1, REPEATS + 1):
            print(f"Running {name} repeat {repeat}/{REPEATS}...")
            results.append(benchmark(name, path, repeat, rows, size_gb))

    write_log(results)
    print(f"Wrote {LOG_FILE}")


if __name__ == "__main__":
    main()
