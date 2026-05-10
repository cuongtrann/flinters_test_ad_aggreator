# Usage Guide

## Requirements

| Tool | Version |
|---|---|
| Java | 21+ |
| Maven | 3.9+ |
| Docker | 28+ optional |

## Setup

```bash
git clone <repo-url>
cd ad-performance-aggregator
mvn package -DskipTests
```

Place the challenge CSV in the repository root:

```text
ad-performance-aggregator/
`-- ad_data.csv
```

`ad_data.csv` is ignored by Git because it is a large local input file.

## Run Locally

```bash
./run.sh --input ad_data.csv --output results/
```

## Run with Docker

```bash
./docker-run.sh
```

This builds the image, mounts the local input/output directories, and writes results to `results_docker/`.

Custom paths:

```bash
./docker-run.sh --input ad_data.csv --output results_docker
```

Skip rebuild if the image already exists:

```bash
./docker-run.sh --no-build --input ad_data.csv --output results_docker
```

## CLI Options

| Flag | Required | Description |
|---|---|---|
| `--input`, `-i` | Yes | Path to input CSV file |
| `--output`, `-o` | Yes | Path to output directory, created if absent |
| `--help` | No | Show usage |

## Testing

```bash
mvn test
```

The suite includes 30 unit, integration, and end-to-end tests using JUnit Jupiter.

## Benchmarking

Run the benchmark suite:

```bash
python3 benchmark/run_benchmarks.py
```

The script builds `target/aggregator.jar`, generates temporary benchmark CSV files under `/private/tmp/adperf_benchmark/data`, runs 30 benchmark executions, and updates [`../benchmark/benchmark_log.md`](../benchmark/benchmark_log.md).

The benchmark includes:

- generated datasets from 1,000 to 1,000,000 rows
- the real `ad_data.csv` file placed in the repository root
- app time, row count, file size, throughput, skipped rows, unique campaigns, and peak heap

## Input

Input CSV header:

```csv
campaign_id,date,impressions,clicks,spend,conversions
```

Example row:

```csv
CMP001,2024-01-01,50000,2500,1200.00,42
```

`campaign_id` is the aggregation key. `date` is ignored for aggregation. Malformed rows are skipped with warnings.

## Output

Both output files use this schema:

```csv
campaign_id,total_impressions,total_clicks,total_spend,total_conversions,ctr,cpa
```

| File | Ranking |
|---|---|
| [`../results/top10_ctr.csv`](../results/top10_ctr.csv) | Highest CTR first |
| [`../results/top10_cpa.csv`](../results/top10_cpa.csv) | Lowest CPA first |

If no campaign has conversions, `top10_cpa.csv` contains only the header.
