# Ad Performance Aggregator

Fast Java CLI for aggregating large ad performance CSV files by `campaign_id`.

I used Claude Code to support the coding process, including planning, implementation review, benchmarking, and documentation cleanup. The detailed process is documented in [`docs/development-process.md`](docs/development-process.md).

The solution is optimized for large files: it streams the CSV line by line, keeps only campaign aggregates in memory, and uses a fixed-size `PriorityQueue` to select top-10 results without sorting every campaign.

## Solution Highlights

| Area | Decision |
|---|---|
| Large file handling | Stream input with `BufferedReader`; never load the full CSV |
| Memory model | `O(C)`, where `C` is unique campaign count |
| Aggregation | Mutable `CampaignStats` per campaign in a `HashMap` |
| Top 10 ranking | Size-10 `PriorityQueue`, `O(C log 10)` instead of full sort `O(C log C)` |
| Error handling | Malformed rows are skipped with warnings and final skipped count |

See the full technical design in [`docs/architecture.md`](docs/architecture.md).

## Performance

| Metric | Result |
|---|---:|
| Real input tested | 995 MB CSV |
| Rows processed | 26,843,544 |
| Average app time | ~3.56 s |
| Max peak heap used | 222.4 MiB |
| Test suite | 30 JUnit tests |

Top-N benchmark on a high-cardinality dataset shows why `PriorityQueue` is used:

| Strategy | Avg Harness Wall Time |
|---|---:|
| `priority_queue_top10` | 0.622 s |
| `full_sort_baseline` | 1.102 s |

Full raw results are in [`benchmark/benchmark_log.md`](benchmark/benchmark_log.md).

## Outputs

- [`results/top10_ctr.csv`](results/top10_ctr.csv): top 10 campaigns by highest CTR.
- [`results/top10_cpa.csv`](results/top10_cpa.csv): top 10 campaigns by lowest CPA, excluding zero-conversion campaigns.

Example CLI output:

```text
[INFO] Rows processed  : 26,843,544
[INFO] Rows skipped    : 0
[INFO] Unique campaigns: 50
[INFO] Time elapsed    : 3.5s
[INFO] Peak heap used  : 222.4 MiB
[INFO] Written: results/top10_ctr.csv
[INFO] Written: results/top10_cpa.csv
```

## Documentation
- [Development process with Claude Code](docs/development-process.md)
- [Architecture and optimization details](docs/architecture.md)
- [Benchmark log and raw runs](benchmark/benchmark_log.md)
- [Setup, usage, testing, input, and output](docs/usage.md)
- [Original requirements](requirements/README.md)

## Quick Start

Clone the repository, enter the project, and place the challenge CSV at the repository root as `ad_data.csv`.

```bash
git clone git@github.com:cuongtrann/flinters_test_ad_aggreator.git
cd flinters_test_ad_aggreator
mvn package -DskipTests
./run.sh --input ad_data.csv --output results/
```

Docker:

```bash
./docker-run.sh
```

Tests:

```bash
mvn test
```
