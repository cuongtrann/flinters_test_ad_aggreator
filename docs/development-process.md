# Development Process with Claude Code

This project was built iteratively with Claude Code: requirement analysis, planning, implementation, testing, benchmarking, and documentation cleanup.

## Config

I used Claude Code with a few project-specific helpers:

- Context7 MCP for library documentation when needed.
- Caveman mode and `rtk` to reduce token usage during long sessions.
- `CLAUDE.md` to keep repository-specific working rules.

## Prompt Logging Hook

The challenge asks for a prompt log when AI tools are used, so I created a hook to save prompts into [`../PROMPTS.md`](../PROMPTS.md). This file makes the work traceable, including planning, benchmarking, refactoring, Docker setup, README updates, and reusable skill creation.

## Planning Workflow

I followed Anthropic's [Explore, plan, code, commit](https://www.anthropic.com/engineering/claude-code-best-practices) workflow.

First, I asked Claude to read the original challenge README and create `PLAN.md` before writing code. The plan focused on:

- analyzing the near-1 GB CSV input,
- identifying likely bottlenecks: I/O, line parsing, aggregation, and top-N ranking,
- keeping the architecture simple and not over-engineered,
- defining clear package and class responsibilities,
- planning benchmark metrics for runtime, throughput, memory, and correctness.

After the first plan, I asked Claude to expand the plan with time complexity, memory complexity, benchmark scope, test scope, and CLI success/error output. This kept the implementation work guided by a concrete plan instead of jumping straight into code.

## Architecture Decisions

The main solution streams the CSV line by line instead of loading the whole file into memory. Each valid row is parsed and accumulated into `Map<campaignId, CampaignStats>`.

Main complexity:

- Read and aggregate: `O(R)`, where `R` is the number of CSV rows.
- Aggregation memory: `O(C)`, where `C` is the number of unique campaigns.
- Initial full-sort top-N idea: `O(C log C)`.
- Current top-N with a size-10 `PriorityQueue`: `O(C log 10)`, followed by sorting at most 10 rows for stable output.

I used `Picocli` for CLI arguments because it keeps command-line parsing readable and avoids custom argument code. For CSV parsing, I kept a small custom parser because the schema is fixed and simple. If the CSV format becomes more complex later, a dedicated CSV library would be easy to introduce.

## Subagent for Test and Benchmark

After the plan was approved, I created a test and benchmark subagent. The goal was to move repeated testing and performance work out of the main context.

The subagent was responsible for:

- unit and integration tests,
- aggregation correctness checks,
- malformed CSV edge cases,
- synthetic benchmark datasets,
- runtime, throughput, and heap measurements,
- constrained heap checks.

This was useful because benchmark and test work creates a lot of repeated output.

## Implementation

Because the project is small and the plan was clear, I asked Claude to implement the first version directly from the plan, then run the tests.

Main implementation pieces:

- `Aggregator`: streams input and groups rows by campaign.
- `CsvParser`: parses and validates each row.
- `CampaignStats`: stores aggregated metrics.
- `CsvWriter`: writes `top10_ctr.csv` and `top10_cpa.csv`.
- `PeakHeapTracker`: measures peak heap during aggregation.
- `run.sh` and `docker-run.sh`: simplify local and Docker execution.

## Code Review and Cleanup

After the first implementation, I used Claude for a separate review pass before changing more code. I first asked it to inspect readability and optimization opportunities without editing. Then I asked it to apply the cleanup suggestions and run tests again.

One concrete cleanup target was `CsvWriter`, which had ranking, formatting, and output logic in one place. The final version keeps the file small and separates top-N selection from CSV row formatting.

## Benchmark and Optimization

Benchmarking started with generated datasets and the real `ad_data.csv` file. Later, I expanded the benchmark log to include at least 30 raw runs, file size, row count, throughput, skipped rows, unique campaigns, and peak heap.

The benchmark script is [`../benchmark/run_benchmarks.py`](../benchmark/run_benchmarks.py). It generates datasets from small to large and also runs the real `ad_data.csv` file when it exists at the repository root.

The benchmark log is [`../benchmark/benchmark_log.md`](../benchmark/benchmark_log.md). The latest real-file benchmark used a 995 MB CSV with 26,843,544 rows. It reported about 3.56s average app time and 222.4 MiB max peak heap.

After the first benchmark, I reviewed the top-10 ranking step. The first approach was to sort all campaigns and take the first 10. The current approach uses a size-10 `PriorityQueue`, so only the best candidates are kept while scanning campaign stats.

To benchmark this fairly, I created a temporary full-sort baseline. Because the repository had no initial commit yet, a clean git branch could not isolate the change, so I copied the source to `/private/tmp/adperf_sort_baseline_20260510_1` and changed `CsvWriter.selectTop(...)` to:

```java
return stats.stream()
    .sorted(outputOrder)
    .limit(TOP_N)
    .toList();
```

Then I generated a high-cardinality dataset with 1,000,000 rows and 1,000,000 unique campaigns. The real file has only 50 unique campaigns, so it does not show much difference between full sort and heap top-N.

Top-N benchmark result:

| Strategy | Runs | Avg App Time (s) | Avg Harness Wall (s) | Best Harness Wall (s) | Max Peak Heap (MiB) |
|---|---:|---:|---:|---:|---:|
| `priority_queue_top10` | 5 | 0.300 | 0.622 | 0.567 | 287.7 |
| `full_sort_baseline` | 5 | 0.300 | 1.102 | 1.056 | 285.0 |

The CLI app time is rounded to 0.1s, so the isolated comparison also uses harness wall time. In the high-cardinality case, `PriorityQueue` was about 44% faster. Peak heap stayed similar because most memory is used by the aggregation map.

Raw results are stored in the benchmark log.

## Documentation and Delivery

After the app worked correctly, I used Claude to prepare the submission materials:

- Java 21 multi-stage Dockerfile.
- `docker-run.sh`, added after the raw Docker command felt too easy to misuse with bind mounts.
- Short README with highlights and links only.
- `docs/usage.md` for setup, run, test, benchmark, input, and output.
- `docs/architecture.md` for structure, libraries, charts, and optimization details.
- `docs/development-process.md` for the Claude Code workflow.
- `.gitignore` for generated files and the large local `ad_data.csv`.
- Cleanup of unused files before push.

## Reusable Skill

Near the end of the work, I created a reusable skill for large CSV processing. The goal is to reuse the same workflow later: stream the file, aggregate by key, benchmark multiple dataset sizes, measure memory, and keep run/test docs clear.
