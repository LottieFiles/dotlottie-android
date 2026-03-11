#!/bin/bash
#
# Download dotlottie-player C API binaries from GitHub Actions artifacts
# Repository: LottieFiles/dotlottie-rs
# Artifact: dotlottie-player.android
#
# Requires: gh cli (https://cli.github.com/)
#
# Usage:
#   ./download-binaries.sh                    # Download latest
#   ./download-binaries.sh -r <RUN_ID>        # Download specific run
#

set -e

# Configuration
REPO="LottieFiles/dotlottie-rs"
ARTIFACT_NAME="dotlottie-player.android.tar.gz"
OUTPUT_DIR="dotlottie/src/main"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Default values
RUN_ID="${RUN_ID:-}"

usage() {
    echo "Usage: $0 [options]"
    echo ""
    echo "Download dotlottie-rs C API binaries from GitHub Actions"
    echo ""
    echo "Requires: gh cli (https://cli.github.com/)"
    echo "  Install: brew install gh"
    echo "  Login:   gh auth login"
    echo ""
    echo "Options:"
    echo "  -r, --run-id ID         Specific workflow run ID"
    echo "  -o, --output DIR        Output directory (default: dotlottie/src/main)"
    echo "  -l, --list              List recent workflow runs with artifacts"
    echo "  -h, --help              Show this help"
    echo ""
    echo "Examples:"
    echo "  $0                            # Download latest successful build"
    echo "  $0 -r 21383366902             # Download specific run"
    echo "  $0 -l                         # List available runs"
    echo ""
    echo "Run ID can be found in the GitHub Actions URL:"
    echo "  https://github.com/LottieFiles/dotlottie-rs/actions/runs/21383366902"
    echo "                                                           ^^^^^^^^^^^"
    exit 1
}

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

list_runs() {
    log_info "Listing recent workflow runs with '$ARTIFACT_NAME' artifact..."
    echo ""

    # Get recent successful runs
    RUNS=$(gh run list --repo "$REPO" --status success --limit 10 --json databaseId,displayTitle,conclusion,createdAt,headBranch)

    echo "Recent successful runs:"
    echo "$RUNS" | jq -r '.[] | "  \(.databaseId) - \(.displayTitle) (\(.headBranch)) - \(.createdAt)"'
    echo ""
    log_info "Use: $0 -r <RUN_ID> to download a specific run"
    exit 0
}

# Check for gh cli
if ! command -v gh &> /dev/null; then
    log_error "gh cli is not installed"
    echo ""
    echo "Install with: brew install gh"
    echo "Then login:   gh auth login"
    exit 1
fi

# Check gh auth status - just verify we can make API calls
if ! gh api user --jq '.login' &> /dev/null; then
    log_error "Not authenticated with gh cli"
    echo ""
    echo "Please run: gh auth login"
    exit 1
fi

log_info "Authenticated as: $(gh api user --jq '.login')"

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -r|--run-id)
            RUN_ID="$2"
            shift 2
            ;;
        -o|--output)
            OUTPUT_DIR="$2"
            shift 2
            ;;
        -l|--list)
            list_runs
            ;;
        -h|--help)
            usage
            ;;
        *)
            log_error "Unknown option: $1"
            usage
            ;;
    esac
done

# Change to project root
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_ROOT"

log_info "Working directory: $PROJECT_ROOT"

# Find a run with our artifact if not specified
if [ -z "$RUN_ID" ]; then
    log_info "Finding latest successful workflow run with '$ARTIFACT_NAME' artifact..."

    # Get recent successful runs and find one with our artifact
    RUNS=$(gh run list --repo "$REPO" --status success --limit 20 --json databaseId)
    RUN_IDS=$(echo "$RUNS" | jq -r '.[].databaseId')

    for rid in $RUN_IDS; do
        # Check if this run has our artifact
        ARTIFACTS=$(gh api "repos/$REPO/actions/runs/$rid/artifacts" --jq '.artifacts[].name' 2>/dev/null || echo "")
        if echo "$ARTIFACTS" | grep -q "^${ARTIFACT_NAME}$"; then
            RUN_ID="$rid"
            log_info "Found artifact in run $RUN_ID"
            break
        fi
    done

    if [ -z "$RUN_ID" ]; then
        log_error "Could not find any workflow run with artifact '$ARTIFACT_NAME'"
        log_warn "Try listing available runs with: $0 -l"
        exit 1
    fi
fi

log_info "Using workflow run ID: $RUN_ID"

# Create temp directory
TEMP_DIR=$(mktemp -d)
trap "rm -rf $TEMP_DIR" EXIT

log_info "Downloading artifact '$ARTIFACT_NAME'..."

# Download artifact using gh cli
cd "$TEMP_DIR"
if ! gh run download "$RUN_ID" --repo "$REPO" --name "$ARTIFACT_NAME" --dir extracted 2>&1; then
    log_error "Failed to download artifact"
    log_warn "The artifact may have expired (GitHub keeps artifacts for 90 days)"
    log_warn "Try a more recent run with: $0 -l"
    exit 1
fi

log_info "Download complete. Processing..."

# Find the content - could be tar.gz or directly extracted
cd extracted

# Check if there's a tar.gz to extract
TAR_FILE=$(find . -name "*.tar.gz" -o -name "*.tgz" 2>/dev/null | head -1)
if [ -n "$TAR_FILE" ]; then
    log_info "Extracting $TAR_FILE..."
    mkdir -p final
    tar -xzf "$TAR_FILE" -C final
    EXTRACT_DIR="final"
else
    # Content might be directly in extracted folder
    EXTRACT_DIR="."
fi

cd "$PROJECT_ROOT"

# Verify expected structure
log_info "Verifying artifact structure..."

HEADER_FILE=$(find "$TEMP_DIR/extracted/$EXTRACT_DIR" -name "dotlottie_player.h" 2>/dev/null | head -1)
JNILIBS_DIR=$(find "$TEMP_DIR/extracted/$EXTRACT_DIR" -type d -name "jniLibs" 2>/dev/null | head -1)

# Also check in include directory
if [ -z "$HEADER_FILE" ]; then
    HEADER_FILE=$(find "$TEMP_DIR/extracted" -path "*/include/dotlottie_player.h" 2>/dev/null | head -1)
fi

if [ -z "$HEADER_FILE" ]; then
    log_error "Header file (dotlottie_player.h) not found"
    log_warn "Archive contents:"
    find "$TEMP_DIR/extracted" -type f 2>/dev/null | head -30
    exit 1
fi

if [ -z "$JNILIBS_DIR" ]; then
    log_error "jniLibs directory not found"
    log_warn "Archive contents:"
    find "$TEMP_DIR/extracted" -type d 2>/dev/null | head -30
    exit 1
fi

log_info "Found header: $HEADER_FILE"
log_info "Found jniLibs: $JNILIBS_DIR"

# Create output directories
log_info "Installing binaries to $OUTPUT_DIR..."

mkdir -p "$OUTPUT_DIR/cpp"
mkdir -p "$OUTPUT_DIR/jniLibs"

# Copy header file
cp "$HEADER_FILE" "$OUTPUT_DIR/cpp/"
log_info "  Installed: cpp/dotlottie_player.h"

# Copy native libraries for each ABI
INSTALLED_ABIS=0
for ABI in arm64-v8a armeabi-v7a x86 x86_64; do
    ABI_DIR="$JNILIBS_DIR/$ABI"
    if [ -d "$ABI_DIR" ]; then
        mkdir -p "$OUTPUT_DIR/jniLibs/$ABI"

        # Copy libdotlottie_player.so
        if [ -f "$ABI_DIR/libdotlottie_player.so" ]; then
            cp "$ABI_DIR/libdotlottie_player.so" "$OUTPUT_DIR/jniLibs/$ABI/"
            log_info "  Installed: jniLibs/$ABI/libdotlottie_player.so"
            INSTALLED_ABIS=$((INSTALLED_ABIS + 1))
        fi

        # Copy libc++_shared.so
        if [ -f "$ABI_DIR/libc++_shared.so" ]; then
            cp "$ABI_DIR/libc++_shared.so" "$OUTPUT_DIR/jniLibs/$ABI/"
            log_info "  Installed: jniLibs/$ABI/libc++_shared.so"
        fi
    else
        log_warn "  Missing ABI directory: $ABI"
    fi
done

if [ "$INSTALLED_ABIS" -eq 0 ]; then
    log_error "No native libraries were installed!"
    exit 1
fi

# Copy version file if present
VERSION_FILE=$(find "$TEMP_DIR/extracted" -name "version.txt" 2>/dev/null | head -1)
if [ -n "$VERSION_FILE" ]; then
    cp "$VERSION_FILE" "$OUTPUT_DIR/cpp/"
    log_info "  Installed: cpp/version.txt"
    echo ""
    log_info "Version info:"
    cat "$OUTPUT_DIR/cpp/version.txt"
fi

echo ""
log_info "Installation complete!"
