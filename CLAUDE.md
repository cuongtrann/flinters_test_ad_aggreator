# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
# Build fat JAR (runs tests)
mvn clean package -q

# Build without tests
mvn package -DskipTests

# Run
./run.sh --input ad_data.csv --output results/

# Docker
./docker-run.sh --input ad_data.csv --output results_docker/
./docker-run.sh --no-build --input ad_data.csv --output results_docker/

# Tests
mvn test

# Single test class
mvn test -Dtest=CsvParserTest

# Benchmark
python3 benchmark/run_benchmarks.py
```

## Architecture

Single-pass streaming pipeline: CSV in → aggregate in memory → write top-10 CSVs out.

```
Main → CliArgs → Aggregator → MetricsCalculator → CsvWriter
                 ↑
              CsvParser (per line)
```

**Key design constraints** (O(N) time, O(K) memory where K = unique campaigns):
- `Aggregator` streams via 256KB `BufferedReader`; never loads full file into memory
- `HashMap(8192)` pre-sized for campaigns; accumulates into mutable `CampaignStats`
- `CsvWriter` uses `PriorityQueue(11)` for top-10 selection — O(K log 10), not O(K log K)
- `CsvParser` uses `String.split(",")` — no quoted field support (per spec)
- Fail-soft parsing: bad rows skip + warn, suppress after 100 errors

**Output**: `top10_ctr.csv` (CTR desc) and `top10_cpa.csv` (CPA asc, conversions > 0 only). CPA is `NaN` if conversions = 0.

## Package Structure

- `cli/` — Picocli `@Command` in `CliArgs`, wires pipeline, validates paths
- `parser/` — `CsvParser` (streaming), `ParsedRow` (record)
- `aggregator/` — `Aggregator`, `AggregationResult` (record)
- `model/` — `CampaignStats` (mutable accumulator)
- `calculator/` — `MetricsCalculator` (static CTR/CPA methods)
- `writer/` — `CsvWriter` (two output files)
- `util/` — `PeakHeapTracker` (AutoCloseable heap monitor)
- `benchmark/run_benchmarks.py` — benchmark runner, writes `benchmark/benchmark_log.md`

## Testing

4 test classes mirror production packages:
- `parser/CsvParserTest` — valid rows, malformed columns, whitespace
- `calculator/MetricsCalculatorTest` — zero impressions, zero conversions, precision
- `aggregator/AggregatorTest` — single/multi campaign, accumulation, error handling
- `integration/EndToEndTest` — golden path, 50K unique campaigns, mixed malformed rows

## Conventions (from AGENTS.md)

- Commits: Conventional Commits format (`feat:`, `fix:`, `perf:`, `test:`, `docs:`, `refactor:`)
- No Lombok, no reflection frameworks — plain Java only
- Streaming mandatory for any file I/O — no `readAllBytes` / `readAllLines`
- Performance regressions must include benchmark evidence
- New public methods need a test
