# Benchmark Log - Ad Performance Aggregator

Raw benchmark runs across generated datasets and the real input file.

## Key Results

| Area | Result |
|---|---:|
| Real input size | 995 MB |
| Real input rows | 26,843,544 |
| Real-file average app time | 3.560 s |
| Real-file best app time | 3.500 s |
| Real-file max peak heap | 222.4 MiB |
| Real-file average throughput | 279.8 MB/s |
| Real-file average row rate | 7,548,485 rows/s |

The real file has only 50 unique campaigns, so it mainly validates streaming throughput and memory behavior. To test the top-N ranking optimization, a separate high-cardinality dataset was used.

## Top-N Strategy Comparison

This comparison isolates `CsvWriter.selectTop(...)`.

- Current implementation: size-10 `PriorityQueue`
- Baseline: full sort followed by `limit(10)`
- Dataset: 1,000,000 rows, 1,000,000 unique campaigns, 36.4 MB

| Strategy | Runs | Avg Harness Wall (s) | Best Harness Wall (s) | Max Peak Heap (MiB) |
|---|---:|---:|---:|---:|
| `priority_queue_top10` | 5 | 0.622 | 0.567 | 287.7 |
| `full_sort_baseline` | 5 | 1.102 | 1.056 | 285.0 |

`PriorityQueue` is about 44% faster in the high-cardinality case. Peak heap is similar because the aggregation `HashMap` dominates memory.

### Top-N Raw Runs

| Strategy | Run | Rows | Unique Campaigns | Size MB | Harness Wall (s) | Peak Heap (MiB) | MB/s | Rows/s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| `priority_queue_top10` | 1 | 1,000,000 | 1,000,000 | 36.4 | 0.714 | 253.5 | 121.2 | 3,333,333 |
| `priority_queue_top10` | 2 | 1,000,000 | 1,000,000 | 36.4 | 0.621 | 285.0 | 121.2 | 3,333,333 |
| `priority_queue_top10` | 3 | 1,000,000 | 1,000,000 | 36.4 | 0.567 | 267.5 | 121.2 | 3,333,333 |
| `priority_queue_top10` | 4 | 1,000,000 | 1,000,000 | 36.4 | 0.638 | 254.4 | 121.2 | 3,333,333 |
| `priority_queue_top10` | 5 | 1,000,000 | 1,000,000 | 36.4 | 0.571 | 287.7 | 121.2 | 3,333,333 |
| `full_sort_baseline` | 1 | 1,000,000 | 1,000,000 | 36.4 | 1.140 | 279.8 | 121.2 | 3,333,333 |
| `full_sort_baseline` | 2 | 1,000,000 | 1,000,000 | 36.4 | 1.094 | 275.7 | 121.2 | 3,333,333 |
| `full_sort_baseline` | 3 | 1,000,000 | 1,000,000 | 36.4 | 1.056 | 269.7 | 121.2 | 3,333,333 |
| `full_sort_baseline` | 4 | 1,000,000 | 1,000,000 | 36.4 | 1.105 | 285.0 | 121.2 | 3,333,333 |
| `full_sort_baseline` | 5 | 1,000,000 | 1,000,000 | 36.4 | 1.116 | 282.0 | 121.2 | 3,333,333 |

## Environment

- Generated at: 2026-05-10 19:27:24
- Command per run: `java -jar target/aggregator.jar --input <csv> --output <dir>`
- Java executable: `/Users/kyle/.sdkman/candidates/java/current/bin/java`
- Generated benchmark CSVs are written under `/private/tmp/adperf_benchmark/data`.
- Result files are written under `/private/tmp/adperf_benchmark/results`.
- App time is reported by the CLI and used as the runtime metric.
- Tiny startup-bound runs can report 0.0s because CLI output is rounded to one decimal.

## Datasets

| Dataset | Rows | Size GB | Runs |
|---|---:|---:|---:|
| `generated_1k` | 1,000 | 0.0000 | 5 |
| `generated_10k` | 10,000 | 0.0004 | 5 |
| `generated_100k` | 100,000 | 0.0039 | 5 |
| `generated_500k` | 500,000 | 0.0195 | 5 |
| `generated_1m` | 1,000,000 | 0.0391 | 5 |
| `real_995mb` | 26,843,544 | 0.9717 | 5 |

## Streaming Benchmark Raw Runs

| Dataset | Run | Rows | Size GB | Unique Campaigns | Skipped Rows | App Time (s) | Peak Heap (MiB) | MB/s | Rows/s | Result |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| `generated_1k` | 1 | 1,000 | 0.0000 | 10 | 0 | 0.000 | 8.9 | 0.3 | 8,780 | startup-bound |
| `generated_1k` | 2 | 1,000 | 0.0000 | 10 | 0 | 0.000 | 8.9 | 0.4 | 9,890 | startup-bound |
| `generated_1k` | 3 | 1,000 | 0.0000 | 10 | 0 | 0.000 | 8.9 | 0.4 | 11,329 | startup-bound |
| `generated_1k` | 4 | 1,000 | 0.0000 | 10 | 0 | 0.000 | 8.9 | 0.3 | 9,241 | startup-bound |
| `generated_1k` | 5 | 1,000 | 0.0000 | 10 | 0 | 0.000 | 8.9 | 0.4 | 9,915 | startup-bound |
| `generated_10k` | 1 | 10,000 | 0.0004 | 50 | 0 | 0.000 | 14.8 | 3.3 | 89,034 | startup-bound |
| `generated_10k` | 2 | 10,000 | 0.0004 | 50 | 0 | 0.000 | 14.8 | 3.3 | 89,317 | startup-bound |
| `generated_10k` | 3 | 10,000 | 0.0004 | 50 | 0 | 0.000 | 14.8 | 3.3 | 88,973 | startup-bound |
| `generated_10k` | 4 | 10,000 | 0.0004 | 50 | 0 | 0.000 | 14.8 | 3.3 | 87,647 | startup-bound |
| `generated_10k` | 5 | 10,000 | 0.0004 | 50 | 0 | 0.000 | 14.8 | 3.3 | 88,368 | startup-bound |
| `generated_100k` | 1 | 100,000 | 0.0039 | 500 | 0 | 0.100 | 48.8 | 40.3 | 1,000,000 | fast |
| `generated_100k` | 2 | 100,000 | 0.0039 | 500 | 0 | 0.100 | 49.2 | 40.3 | 1,000,000 | fast |
| `generated_100k` | 3 | 100,000 | 0.0039 | 500 | 0 | 0.100 | 48.8 | 40.3 | 1,000,000 | fast |
| `generated_100k` | 4 | 100,000 | 0.0039 | 500 | 0 | 0.100 | 48.8 | 40.3 | 1,000,000 | fast |
| `generated_100k` | 5 | 100,000 | 0.0039 | 500 | 0 | 0.100 | 48.8 | 40.3 | 1,000,000 | fast |
| `generated_500k` | 1 | 500,000 | 0.0195 | 1,000 | 0 | 0.200 | 145.4 | 100.1 | 2,500,000 | fast |
| `generated_500k` | 2 | 500,000 | 0.0195 | 1,000 | 0 | 0.100 | 150.4 | 200.1 | 5,000,000 | fast |
| `generated_500k` | 3 | 500,000 | 0.0195 | 1,000 | 0 | 0.100 | 139.4 | 200.1 | 5,000,000 | fast |
| `generated_500k` | 4 | 500,000 | 0.0195 | 1,000 | 0 | 0.100 | 137.5 | 200.1 | 5,000,000 | fast |
| `generated_500k` | 5 | 500,000 | 0.0195 | 1,000 | 0 | 0.100 | 141.4 | 200.1 | 5,000,000 | fast |
| `generated_1m` | 1 | 1,000,000 | 0.0391 | 5,000 | 0 | 0.200 | 151.0 | 200.0 | 5,000,000 | fast |
| `generated_1m` | 2 | 1,000,000 | 0.0391 | 5,000 | 0 | 0.200 | 142.0 | 200.0 | 5,000,000 | fast |
| `generated_1m` | 3 | 1,000,000 | 0.0391 | 5,000 | 0 | 0.200 | 145.0 | 200.0 | 5,000,000 | fast |
| `generated_1m` | 4 | 1,000,000 | 0.0391 | 5,000 | 0 | 0.200 | 151.0 | 200.0 | 5,000,000 | fast |
| `generated_1m` | 5 | 1,000,000 | 0.0391 | 5,000 | 0 | 0.200 | 148.0 | 200.0 | 5,000,000 | fast |
| `real_995mb` | 1 | 26,843,544 | 0.9717 | 50 | 0 | 3.500 | 188.4 | 284.3 | 7,669,584 | fast |
| `real_995mb` | 2 | 26,843,544 | 0.9717 | 50 | 0 | 3.500 | 186.3 | 284.3 | 7,669,584 | fast |
| `real_995mb` | 3 | 26,843,544 | 0.9717 | 50 | 0 | 3.500 | 186.4 | 284.3 | 7,669,584 | fast |
| `real_995mb` | 4 | 26,843,544 | 0.9717 | 50 | 0 | 3.500 | 186.4 | 284.3 | 7,669,584 | fast |
| `real_995mb` | 5 | 26,843,544 | 0.9717 | 50 | 0 | 3.800 | 222.4 | 261.8 | 7,064,091 | fast |

## Streaming Benchmark Summary by Dataset

| Dataset | Runs | Rows | Size GB | Avg App Time (s) | Best App Time (s) | Max Peak Heap (MiB) | Avg MB/s | Avg Rows/s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| `generated_1k` | 5 | 1,000 | 0.0000 | 0.000 | 0.000 | 8.9 | 0.4 | 9,831 |
| `generated_10k` | 5 | 10,000 | 0.0004 | 0.000 | 0.000 | 14.8 | 3.3 | 88,668 |
| `generated_100k` | 5 | 100,000 | 0.0039 | 0.100 | 0.100 | 49.2 | 40.3 | 1,000,000 |
| `generated_500k` | 5 | 500,000 | 0.0195 | 0.120 | 0.100 | 150.4 | 180.1 | 4,500,000 |
| `generated_1m` | 5 | 1,000,000 | 0.0391 | 0.200 | 0.200 | 151.0 | 200.0 | 5,000,000 |
| `real_995mb` | 5 | 26,843,544 | 0.9717 | 3.560 | 3.500 | 222.4 | 279.8 | 7,548,485 |
