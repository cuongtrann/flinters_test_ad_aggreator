# Ad Performance Aggregator — Implementation Plan

## Context

Flinters coding challenge. Process ~1GB CSV of ad performance data, aggregate by `campaign_id`, output two ranked CSVs. Evaluated on correctness, performance, clean code, and error handling.

---

## Requirements Analysis

**Input CSV schema:**
```
campaign_id, date, impressions, clicks, spend, conversions
```

**Aggregate per `campaign_id`:**
- `total_impressions`, `total_clicks`, `total_spend`, `total_conversions`
- `CTR` = total_clicks / total_impressions (0.0 if impressions = 0)
- `CPA` = total_spend / total_conversions (null/excluded if conversions = 0)

**Output files:**
- `top10_ctr.csv` — top 10 by CTR descending
- `top10_cpa.csv` — top 10 by CPA ascending, exclude zero-conversion campaigns

**CLI usage:**
```bash
java -jar aggregator.jar --input ad_data.csv --output results/
```

---

## Performance Bottleneck Analysis

| Bottleneck | Root Cause | Impact |
|---|---|---|
| **Memory** | Loading full 1GB into memory | OOM crash |
| **Object allocation** | New objects per CSV row | GC pressure / pauses |
| **String splitting** | Regex-based split per line | CPU overhead at scale |
| **I/O throughput** | Small read buffer | Disk bottleneck |
| **HashMap overhead** | Too-small initial capacity | Rehashing cost |

**Key insight:** Aggregation map is bounded by number of unique campaigns (thousands), not file size (millions of rows).
Streaming row-by-row keeps peak memory at `O(unique_campaigns)`, not `O(file_size)`.

---

## Architecture

### Processing Strategy: Single-pass Streaming

```
BufferedReader (256KB buffer)
    → line-by-line iteration
    → CsvParser.parseLine()        # split + parse primitives
    → Aggregator.accumulate()      # update HashMap<String, CampaignStats>
    → MetricsCalculator.compute()  # CTR + CPA post-aggregation
    → sort + slice top 10
    → CsvWriter.write()
```

No external CSV library. `String.split(",", -1)` is sufficient — spec has no quoted fields with embedded commas.

### Package Structure

```
src/
├── main/java/com/flinters/adperf/
│   ├── Main.java                     # Entry point + pipeline orchestration
│   ├── cli/
│   │   └── CliArgs.java              # Picocli @Command — --input, --output
│   ├── model/
│   │   └── CampaignStats.java        # Mutable accumulator (primitives only)
│   ├── parser/
│   │   └── CsvParser.java            # Streaming line parser, fail-soft
│   ├── aggregator/
│   │   └── Aggregator.java           # Streaming accumulation logic
│   ├── calculator/
│   │   └── MetricsCalculator.java    # CTR / CPA formulas
│   └── writer/
│       └── CsvWriter.java            # Ranked output CSV writer
└── test/java/com/flinters/adperf/
    ├── parser/CsvParserTest.java
    ├── aggregator/AggregatorTest.java
    ├── calculator/MetricsCalculatorTest.java
    └── integration/EndToEndTest.java
```

### Key Design Decisions

**`CampaignStats` — primitives, not boxed types:**
```java
class CampaignStats {
    String campaignId;
    long   totalImpressions;
    long   totalClicks;
    double totalSpend;
    long   totalConversions;
    // CTR + CPA computed post-aggregation, not stored here
}
```
Avoids boxing overhead on hot-path accumulation.

**`Aggregator` — pre-sized HashMap:**
```java
// Pre-size avoids rehashing; tune if unique campaign count is known
Map<String, CampaignStats> map = new HashMap<>(8192);
```

**`CsvParser` — fail-soft on bad rows:**
```java
// Skip row + log warning on: wrong column count, parse error
// Returns Optional<ParsedRow> — empty = skip row
```

**`BufferedReader` buffer: 256KB**
```java
new BufferedReader(new FileReader(path), 256 * 1024)
```

---

## Build Tool & Dependencies

**Maven** (`pom.xml`) — no Spring, no Lombok, no CSV library.

| Dependency | Version | Purpose |
|---|---|---|
| `info.picocli:picocli` | 4.7.x | CLI arg parsing |
| `org.junit.jupiter:junit-jupiter` | 5.10.x | Unit + integration tests |
| `maven-shade-plugin` | 3.5.x | Fat JAR for `java -jar` |

---

## Error Handling

| Scenario | Behavior |
|---|---|
| Input file not found | Print error, exit code 1 |
| Output dir not writable | Print error, exit code 1 |
| Malformed row (wrong column count) | Skip row, warn, count |
| Non-numeric field | Skip row, warn |
| `impressions = 0` | CTR = 0.0 |
| `conversions = 0` | Exclude from CPA ranking |
| Empty file | Write empty CSVs, print info |

---

## CLI Output Specification

### Success — normal run

```
[INFO] Reading: data/ad_data_1gb.csv
[INFO] Processing...
[INFO] Rows processed : 13,241,500
[INFO] Rows skipped   : 42
[INFO] Unique campaigns: 8,320
[INFO] Time elapsed   : 34.2s
[INFO] Written: results/top10_ctr.csv
[INFO] Written: results/top10_cpa.csv
[INFO] Done.
```

### Success — fewer than 10 campaigns

```
[WARN] Only 5 unique campaigns found — top10_ctr.csv contains 5 rows.
[WARN] Only 3 campaigns have conversions > 0 — top10_cpa.csv contains 3 rows.
```

### Success — all conversions are zero

```
[WARN] No campaigns with conversions > 0. top10_cpa.csv will contain header only.
```

### Errors — exit code 1, print to stderr

| Scenario | stderr message |
|---|---|
| Input file not found | `[ERROR] Input file not found: data/missing.csv` |
| Input file is a directory | `[ERROR] Input path is a directory, not a file: data/` |
| Output dir cannot be created | `[ERROR] Cannot create output directory: /root/results/` |
| Output dir not writable | `[ERROR] Output directory is not writable: results/` |
| Empty file (no data rows) | `[ERROR] No data rows found in input file.` |
| Missing --input flag | picocli default: `Missing required option: '--input=<input>'` |
| Missing --output flag | picocli default: `Missing required option: '--output=<output>'` |

### Malformed row warnings — stderr, one line per skipped row

```
[WARN] Row 142: expected 6 columns, got 4. Skipping.
[WARN] Row 891: invalid number "abc" in column "impressions". Skipping.
```

Suppress individual warnings after 100 skipped rows, print summary only:
```
[WARN] 1,042 rows skipped due to parse errors (suppressing further warnings).
```

### Exit codes

| Code | Meaning |
|---|---|
| `0` | Success |
| `1` | Fatal error (bad input path, unwritable output, empty file) |
| `2` | Bad CLI arguments (picocli default) |

---

## Output CSV Format

**`top10_ctr.csv`** (CTR descending):
```
campaign_id,total_impressions,total_clicks,total_spend,total_conversions,ctr,cpa
CMP042,1200000,85000,42000.50,3200,0.070833,13.125156
```

**`top10_cpa.csv`** (CPA ascending, conversions > 0 only): same columns.

---

## Implementation Steps

- [ ] 1. Scaffold Maven project — `pom.xml`, directory structure, `Main.java` stub
- [ ] 2. `CliArgs.java` — Picocli `--input` / `--output` with file validation
- [ ] 3. `CampaignStats.java` — model POJO
- [ ] 4. `CsvParser.java` — line parser + unit tests
- [ ] 5. `Aggregator.java` — streaming accumulation + unit tests
- [ ] 6. `MetricsCalculator.java` — CTR / CPA formulas + unit tests
- [ ] 7. `CsvWriter.java` — write ranked output CSVs
- [ ] 8. `Main.java` — wire pipeline, log processing time + row/error counts
- [ ] 9. `EndToEndTest.java` — generate 10K-row CSV, verify output correctness
- [x] 10. `benchmark/run_benchmarks.py` — Python benchmark runner that generates temporary CSV datasets
- [ ] 11. `README.md` — setup instructions, run command, benchmark results

---

## Complexity Analysis

### Variables

| Symbol | Meaning |
|---|---|
| `N` | Total rows in CSV (~10–15M rows for 1GB) |
| `K` | Unique campaign IDs (estimated: hundreds to low thousands) |
| `B` | Read buffer size (256KB, constant) |

### Time Complexity

| Phase | Complexity | Notes |
|---|---|---|
| File read + line iteration | O(N) | One pass, no seek |
| Parse each line | O(1) | 6-field split, fixed column count |
| HashMap get/put per row | O(1) amortized | Pre-sized → no rehash |
| Accumulate into stats | O(1) | Primitive addition only |
| Compute CTR/CPA per campaign | O(K) | Post-aggregation, one pass over map |
| Sort campaigns for ranking | O(K log K) | K << N, negligible |
| Write output CSVs | O(1) | Max 10 rows per file |
| **Total** | **O(N)** | Dominated by single file scan |

### Space Complexity

| Structure | Complexity | Concrete estimate |
|---|---|---|
| Read buffer | O(B) = O(1) | 256KB, constant |
| Current line string | O(L) = O(1) | ~100 bytes avg per row |
| `HashMap<String, CampaignStats>` | O(K) | ~100 bytes/entry × K campaigns |
| `CampaignStats` per entry | O(1) | 1 String + 4 primitives ≈ 72 bytes |
| Sorted result list | O(K) | Reuses map values, no copy |
| **Total** | **O(K)** | Independent of file size N |

**Practical estimates at K = 10,000 campaigns:**
- HashMap overhead: ~10MB
- CampaignStats objects: ~720KB
- **Peak heap well under 100MB** — the 512MB limit is very safe

**Worst case:** K = 1,000,000 unique campaigns (unlikely in ad data) → ~100MB still fits in 512MB.

---

## Benchmark Plan

### What to Benchmark

| Aspect | Metric | Tool |
|---|---|---|
| Wall-clock time | Seconds to process full file | `time` command |
| Throughput | MB/s read + parsed | `total_bytes / elapsed_ms` |
| Peak heap memory | MB at highest point | JVM `-verbose:gc` or JFR |
| GC activity | Total GC pause time, GC count | `-Xlog:gc` flag |
| Rows/sec | Parsing throughput | Logged by `Main.java` |
| Memory efficiency | Heap used vs file size ratio | JFR heap snapshot |

### Test Data Sizes

Generate CSVs at multiple scales to observe linear scaling:

| File Size | Approx Rows | Purpose |
|---|---|---|
| 10MB | ~130K rows | Fast correctness check |
| 100MB | ~1.3M rows | Mid-scale sanity |
| 500MB | ~6.5M rows | Near-target scale |
| 1GB | ~13M rows | Full target benchmark |

Generate benchmark datasets with `benchmark/run_benchmarks.py`:
```bash
python3 benchmark/run_benchmarks.py
# ~1GB output, configurable campaign count
```

### Benchmark Commands

```bash
# Wall-clock + throughput (logged by app)
time java -Xmx512m -jar target/aggregator.jar \
  --input data/ad_data_1gb.csv --output results/

# GC activity — total pause time and count
java -Xmx512m -Xlog:gc:file=gc.log \
  -jar target/aggregator.jar \
  --input data/ad_data_1gb.csv --output results/
cat gc.log | grep -E "Pause|GC\("

# Java Flight Recorder — heap + CPU profile
java -Xmx512m \
  -XX:StartFlightRecording=duration=60s,filename=profile.jfr \
  -jar target/aggregator.jar \
  --input data/ad_data_1gb.csv --output results/
# Open profile.jfr in JDK Mission Control

# Confirm max heap not exceeded (OOM test)
java -Xmx256m -jar target/aggregator.jar \
  --input data/ad_data_1gb.csv --output results/
```

### Expected Benchmark Results (targets)

| File Size | Max Time | Max Heap | GC Pauses |
|---|---|---|---|
| 10MB | < 2s | < 100MB | < 5 |
| 100MB | < 8s | < 150MB | < 20 |
| 1GB | < 60s | < 512MB | < 100 |

Scaling should be **linear in N** — doubling file size ≈ doubles processing time. Any super-linear growth indicates GC thrash or HashMap rehashing.

---

## Test Plan

### Unit Tests

**`CsvParserTest`**

| Test case | Input | Expected |
|---|---|---|
| Valid row | `"CMP01,2024-01-01,1000,50,200.0,5"` | Parsed `ParsedRow` with correct fields |
| Too few columns | `"CMP01,2024-01-01,1000"` | `Optional.empty()` + warning logged |
| Too many columns | `"CMP01,2024-01-01,1000,50,200.0,5,extra"` | `Optional.empty()` + warning logged |
| Non-numeric impressions | `"CMP01,2024-01-01,abc,50,200.0,5"` | `Optional.empty()` + warning logged |
| Zero impressions | `"CMP01,2024-01-01,0,0,0.0,0"` | Parsed, all zeros |
| Negative spend | `"CMP01,2024-01-01,1000,50,-5.0,3"` | Parsed (no domain restriction in parser) |
| Whitespace padding | `" CMP01 , 2024-01-01 , 1000,50,200.0,5"` | Trimmed and parsed correctly |
| Empty campaign_id | `",2024-01-01,1000,50,200.0,5"` | Parsed (empty string key — valid per spec) |
| Header line | `"campaign_id,date,impressions,clicks,spend,conversions"` | `Optional.empty()` (skipped as header) |

**`MetricsCalculatorTest`**

| Test case | Input | Expected |
|---|---|---|
| Normal CTR | impressions=1000, clicks=50 | CTR = 0.05 |
| Zero impressions | impressions=0, clicks=0 | CTR = 0.0 (no exception) |
| CTR > 1 (data anomaly) | impressions=100, clicks=200 | CTR = 2.0 (allow, don't clamp) |
| Normal CPA | spend=500.0, conversions=10 | CPA = 50.0 |
| Zero conversions | spend=500.0, conversions=0 | CPA = null (excluded from ranking) |
| Zero spend, positive conversions | spend=0.0, conversions=5 | CPA = 0.0 |
| Double precision | spend=0.1 + 0.2, conversions=1 | CPA ≈ 0.3 (within epsilon) |

**`AggregatorTest`**

| Test case | Expected behavior |
|---|---|
| Single campaign, single row | Stats match row values exactly |
| Single campaign, multiple rows | Correct accumulation of all fields |
| Multiple campaigns | Each campaign accumulated independently |
| 10K rows, 100 campaigns | Correct totals for each campaign |
| Row with parse error | Skipped, error count incremented, other rows unaffected |

**`CsvWriterTest`**

| Test case | Expected |
|---|---|
| Write CTR ranking | Header present, rows ordered by CTR descending |
| Write CPA ranking | Rows ordered by CPA ascending, zero-conversion campaigns absent |
| Fewer than 10 campaigns | Output fewer than 10 rows (no padding) |
| Exactly 10 campaigns | All 10 in output |
| More than 10 campaigns | Exactly 10 rows in output |
| Output directory missing | Directory created automatically |

### Integration Tests (`EndToEndTest`)

All tests use programmatically generated CSV — no file fixtures needed.

| Test case | Setup | Assert |
|---|---|---|
| Golden path | 100 campaigns × 1000 rows each | top10_ctr correct order, top10_cpa correct order |
| All zero conversions | All rows have conversions=0 | top10_cpa.csv has header only, no data rows |
| All zero impressions | All rows have impressions=0 | top10_ctr.csv has CTR=0.0 for all |
| Fewer than 10 campaigns | 5 unique campaigns | Both output files have 5 data rows |
| Mixed valid/malformed rows | 10% malformed rows injected | Correct aggregation on valid rows, error count matches |
| Large campaign count | 50K unique campaigns | Completes without OOM, outputs correct top 10 |
| Single row file | 1 data row | Outputs that 1 campaign in both files |
| Empty file (header only) | Header line only | Both outputs have header only, no data rows |

### Performance Tests

| Test | Command | Pass criterion |
|---|---|---|
| 1GB completes under 60s | `time java -Xmx512m -jar ...` | Exit 0, elapsed < 60s |
| No OOM at 256MB heap | `java -Xmx256m -jar ...` | Exit 0 (K is small, heap sufficient) |
| Scaling is linear | Run 100MB, 500MB, 1GB; compare times | Time ratio ≈ size ratio ±20% |
| GC pause total < 5s | `-Xlog:gc` on 1GB run | Sum of STW pauses < 5000ms |

---

## Performance Targets

| Metric | Target |
|---|---|
| Peak memory | < 512MB for 1GB input |
| Processing time | < 60s on standard hardware |
| Throughput | ~20MB/s+ parsing rate |
| GC total pause | < 5s for 1GB run |

---

## Optional Enhancements (post-MVP)

- Progress indicator: print row count every 1M rows
- `--skip-errors` / `--strict` flag for error tolerance mode
- Dockerfile for reproducible benchmark environment
- Parallel chunk processing via `ForkJoinPool` (complex — skip unless MVP complete)

---

## Verification

```bash
# Build fat JAR
mvn clean package -q

# Run against 1GB sample
java -Xmx512m -jar target/aggregator.jar \
  --input data/ad_data_1gb.csv \
  --output results/

# Verify output structure
head results/top10_ctr.csv
head results/top10_cpa.csv
wc -l results/top10_ctr.csv   # expect: 11 (header + 10 rows)

# Run test suite
mvn test

# Benchmark
time java -Xmx512m -jar target/aggregator.jar \
  --input data/ad_data_1gb.csv \
  --output results/
```
