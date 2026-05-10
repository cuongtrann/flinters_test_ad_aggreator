#!/bin/sh
set -eu

IMAGE_NAME="${IMAGE_NAME:-ad-aggregator}"
PROJECT_ROOT="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
INPUT_PATH="ad_data.csv"
OUTPUT_DIR="results_docker"
BUILD_IMAGE=1

usage() {
    cat <<EOF
Usage: ./docker-run.sh [options]

Options:
  -i, --input PATH    Input CSV path. Default: ad_data.csv
  -o, --output PATH   Output directory. Default: results_docker
      --no-build      Skip docker build and use existing image
  -h, --help          Show this help

Examples:
  ./docker-run.sh
  ./docker-run.sh --input ad_data.csv --output results_docker
  ./docker-run.sh --no-build --input /absolute/path/ad_data.csv --output ./results_docker
EOF
}

while [ "$#" -gt 0 ]; do
    case "$1" in
        -i|--input)
            shift
            [ "$#" -gt 0 ] || { echo "Missing value for --input" >&2; exit 1; }
            INPUT_PATH="$1"
            ;;
        -o|--output)
            shift
            [ "$#" -gt 0 ] || { echo "Missing value for --output" >&2; exit 1; }
            OUTPUT_DIR="$1"
            ;;
        --no-build)
            BUILD_IMAGE=0
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            echo "Unknown option: $1" >&2
            usage >&2
            exit 1
            ;;
    esac
    shift
done

case "$INPUT_PATH" in
    /*) HOST_INPUT="$INPUT_PATH" ;;
    *)  HOST_INPUT="$PROJECT_ROOT/$INPUT_PATH" ;;
esac

case "$OUTPUT_DIR" in
    /*) HOST_OUTPUT="$OUTPUT_DIR" ;;
    *)  HOST_OUTPUT="$PROJECT_ROOT/$OUTPUT_DIR" ;;
esac

[ -f "$HOST_INPUT" ] || { echo "Input file not found: $HOST_INPUT" >&2; exit 1; }
mkdir -p "$HOST_OUTPUT"

INPUT_DIR="$(dirname "$HOST_INPUT")"
INPUT_FILE="$(basename "$HOST_INPUT")"
INPUT_DIR_ABS="$(CDPATH= cd -- "$INPUT_DIR" && pwd)"
OUTPUT_DIR_ABS="$(CDPATH= cd -- "$HOST_OUTPUT" && pwd)"

if [ "$BUILD_IMAGE" -eq 1 ]; then
    docker build -t "$IMAGE_NAME" "$PROJECT_ROOT"
fi

docker run --rm \
    --user "$(id -u):$(id -g)" \
    --mount "type=bind,source=$INPUT_DIR_ABS,target=/input,readonly" \
    --mount "type=bind,source=$OUTPUT_DIR_ABS,target=/output" \
    "$IMAGE_NAME" \
    --input "/input/$INPUT_FILE" \
    --output /output
