#!/bin/sh
# Usage: ./run.sh --input ad_data.csv --output results/
exec java -jar "$(dirname "$0")/target/aggregator.jar" "$@"
