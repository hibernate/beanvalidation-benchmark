#!/usr/bin/env bash
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements. See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to you under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License. You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# Bean Validation benchmark runner.
# Prebuilds the bean model, packages JMH benchmark JARs, and runs them.
# Async-profiler is always enabled, producing JFR recordings and flamegraph
# HTML for every run.
#
# @author Sean Okafor

set -euo pipefail

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
readonly RESULTS_DIR="${SCRIPT_DIR}/.results"

readonly ASYNC_PROFILER_VERSION="4.4"
readonly ASYNC_PROFILER_DOWNLOAD_DIR="${SCRIPT_DIR}/.async-profiler"
readonly ASYNC_PROFILER_SHA256_LINUX_X64="1233f26fc95753e75ce32733bbcaf8f0bedc2c098b0e798af87935b08a63b24e"
readonly ASYNC_PROFILER_SHA256_LINUX_ARM64="86ff97b4436accdb6d7bb65c1cf6e38a756f2037a921994d8fa1dcb97d1dc53c"
readonly ASYNC_PROFILER_SHA256_MACOS="6177ebe56d088d116e1b436618f18b3316b9e41885fe743539f6bc297a487239"

readonly VALID_PROFILES=(
    hv-current hv-9.1 hv-9.0 hv-6.0 hv-5.4
    bval-3.0 bval-1.1
)

readonly BV11_PROFILES="bval-1.1 hv-5.4 hv-6.0"
readonly BV31_PROFILES="bval-3.0 hv-9.0 hv-9.1 hv-current"

# --- Defaults ---
PROFILES=()
SKIP_BUILD=false
FORKS=""
WARMUP=""
MEASUREMENT=""
THREADS=""
QUICK=false
JVM_ARGS=""
BENCHMARK_PATTERNS=()
ASYNC_PROFILER_PATH_ARG=""
ASYNC_PROFILER_AVAILABLE=false
CORES_OVERRIDE=""
TASKSET_CORES=""
RESOLVED_ASYNC_PROFILER_HOME=""
OS=""
ARCH=""

# --- Utility functions ---

log() {
    echo "[INFO $(date '+%H:%M:%S')] $*"
}

warn() {
    echo "[WARN $(date '+%H:%M:%S')] $*" >&2
}

die() {
    echo "[ERROR] $1" >&2
    exit "${2:-1}"
}

usage() {
    cat <<'EOF'
Usage: run-benchmarks.sh [OPTIONS] [BENCHMARK_PATTERNS...]

Prebuild bean models, package JMH benchmark JARs, and run benchmarks.
Async-profiler is always enabled, producing JFR recordings and flamegraph
HTML for every run.

Benchmark patterns are JMH regex patterns matched against benchmark method
names. If none are specified, all benchmarks are run.

Options:
  --profile <name>       Implementation profile to benchmark (repeatable).
                         If omitted, defaults to hv-current.
                         Valid: hv-current, hv-9.1, hv-9.0, hv-6.0, hv-5.4,
                                bval-3.0, bval-1.1
  --skip-build           Skip Maven builds (reuse existing JARs)
  --forks <N>            Number of JMH forks (default: 1)
  --warmup <N>           Number of warmup iterations (default: benchmark-defined)
  --measurement <N>      Number of measurement iterations (default: benchmark-defined)
  --threads <N>          Number of threads (default: benchmark-defined)
  --quick                Quick mode: 1 fork, 3 warmup, 5 measurement, 1 thread
  --jvm-args "<args>"    Extra JVM arguments for benchmark forks
  --async-profiler-path <path>
                         Path to an existing async-profiler installation.
                         If not set, async-profiler is downloaded automatically.
  --cores <spec>         Pin benchmark JVM to specific CPU cores (e.g., 0-7
                         or 0,2,4,6). Linux only; uses taskset. When omitted
                         on Linux with a hybrid CPU, P-cores are auto-detected.
  -h, --help             Show this help message

Environment variables:
  ASYNC_PROFILER_HOME    Fallback path to async-profiler installation (used
                         if --async-profiler-path is not specified)

Examples:
  # Run all benchmarks with default profile
  run-benchmarks.sh

  # Quick dev run
  run-benchmarks.sh --quick --profile hv-9.0

  # Compare two implementations
  run-benchmarks.sh --profile hv-9.0 --profile hv-9.1

  # Run a specific benchmark
  run-benchmarks.sh --quick RawValidationSpeedBenchmark

  # Pin to specific CPU cores
  run-benchmarks.sh --quick --cores 0-7 --profile hv-9.0

  # Use a local async-profiler installation
  run-benchmarks.sh --quick --async-profiler-path /opt/async-profiler
EOF
    exit 0
}

is_valid_profile() {
    local profile="$1"
    for p in "${VALID_PROFILES[@]}"; do
        [[ "${p}" == "${profile}" ]] && return 0
    done
    return 1
}

is_positive_int() {
    [[ "$1" =~ ^[1-9][0-9]*$ ]]
}

generator_for_profile() {
    local profile="$1"
    for p in ${BV11_PROFILES}; do
        [[ "${p}" == "${profile}" ]] && echo "bv-1.1" && return
    done
    echo "bv-3.1"
}

# --- Parse arguments ---

parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --profile)
                [[ $# -ge 2 ]] || die "--profile requires a value"
                is_valid_profile "$2" || die "Invalid profile '$2'. Valid: ${VALID_PROFILES[*]}"
                PROFILES+=("$2")
                shift 2
                ;;
            --skip-build)
                SKIP_BUILD=true
                shift
                ;;
            --forks)
                [[ $# -ge 2 ]] || die "--forks requires a value"
                is_positive_int "$2" || die "--forks must be a positive integer"
                FORKS="$2"
                shift 2
                ;;
            --warmup)
                [[ $# -ge 2 ]] || die "--warmup requires a value"
                is_positive_int "$2" || die "--warmup must be a positive integer"
                WARMUP="$2"
                shift 2
                ;;
            --measurement)
                [[ $# -ge 2 ]] || die "--measurement requires a value"
                is_positive_int "$2" || die "--measurement must be a positive integer"
                MEASUREMENT="$2"
                shift 2
                ;;
            --threads)
                [[ $# -ge 2 ]] || die "--threads requires a value"
                is_positive_int "$2" || die "--threads must be a positive integer"
                THREADS="$2"
                shift 2
                ;;
            --quick)
                QUICK=true
                shift
                ;;
            --jvm-args)
                [[ $# -ge 2 ]] || die "--jvm-args requires a value"
                JVM_ARGS="$2"
                shift 2
                ;;
            --async-profiler-path)
                [[ $# -ge 2 ]] || die "--async-profiler-path requires a value"
                ASYNC_PROFILER_PATH_ARG="$2"
                shift 2
                ;;
            --cores)
                [[ $# -ge 2 ]] || die "--cores requires a value"
                CORES_OVERRIDE="$2"
                shift 2
                ;;
            -h|--help)
                usage
                ;;
            -*)
                die "Unknown option: $1"
                ;;
            *)
                BENCHMARK_PATTERNS+=("$1")
                shift
                ;;
        esac
    done

    if [[ ${#PROFILES[@]} -eq 0 ]]; then
        PROFILES=("hv-current")
    fi

    if [[ "${QUICK}" == true ]]; then
        [[ -z "${FORKS}" ]] && FORKS=1
        [[ -z "${WARMUP}" ]] && WARMUP=3
        [[ -z "${MEASUREMENT}" ]] && MEASUREMENT=5
        [[ -z "${THREADS}" ]] && THREADS=1
    fi
}

# --- Platform detection ---

detect_platform() {
    local machine
    machine="$(uname -m)"
    case "${machine}" in
        x86_64)        ARCH="x64" ;;
        aarch64|arm64) ARCH="arm64" ;;
        *)             die "Unsupported architecture: ${machine}" 2 ;;
    esac

    local kernel
    kernel="$(uname -s)"
    case "${kernel}" in
        Linux)  OS="linux" ;;
        Darwin) OS="macos" ;;
        *)      die "Unsupported OS: ${kernel}" 2 ;;
    esac
}

# --- Prerequisites ---

check_prerequisites() {
    command -v java >/dev/null 2>&1 || die "'java' not found on PATH"
    command -v mvn >/dev/null 2>&1 || die "'mvn' not found on PATH"
}

# --- JVM compatibility ---

check_jvm_hotspot() {
    local vm_info
    vm_info="$(java -version 2>&1)"

    if echo "${vm_info}" | grep -qi 'OpenJ9'; then
        warn "Detected OpenJ9 JVM. async-profiler requires a HotSpot-based JVM."
        warn "Benchmarks will run without profiling. Install a HotSpot JDK for profiler support."
        ASYNC_PROFILER_AVAILABLE=false
        return
    fi

    ASYNC_PROFILER_AVAILABLE=true
}

# --- Async-profiler setup (always enabled on HotSpot) ---

compute_sha256() {
    local file="$1"
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "${file}" | cut -d' ' -f1
    elif command -v shasum >/dev/null 2>&1; then
        shasum -a 256 "${file}" | cut -d' ' -f1
    else
        die "Neither sha256sum nor shasum found. Cannot verify async-profiler archive." 4
    fi
}

get_expected_sha256() {
    case "${OS}-${ARCH}" in
        linux-x64)   echo "${ASYNC_PROFILER_SHA256_LINUX_X64}" ;;
        linux-arm64) echo "${ASYNC_PROFILER_SHA256_LINUX_ARM64}" ;;
        macos-*)     echo "${ASYNC_PROFILER_SHA256_MACOS}" ;;
        *)           die "No SHA-256 hash for platform ${OS}-${ARCH}" 4 ;;
    esac
}

get_async_profiler_archive_name() {
    if [[ "${OS}" == "macos" ]]; then
        echo "async-profiler-${ASYNC_PROFILER_VERSION}-macos.zip"
    else
        echo "async-profiler-${ASYNC_PROFILER_VERSION}-${OS}-${ARCH}.tar.gz"
    fi
}

get_async_profiler_extract_dir() {
    if [[ "${OS}" == "macos" ]]; then
        echo "async-profiler-${ASYNC_PROFILER_VERSION}-macos"
    else
        echo "async-profiler-${ASYNC_PROFILER_VERSION}-${OS}-${ARCH}"
    fi
}

get_profiler_lib_name() {
    if [[ "${OS}" == "macos" ]]; then
        echo "libasyncProfiler.dylib"
    else
        echo "libasyncProfiler.so"
    fi
}

validate_async_profiler_home() {
    local home="$1"
    local lib_name
    lib_name="$(get_profiler_lib_name)"
    if [[ ! -f "${home}/lib/${lib_name}" ]]; then
        die "async-profiler library not found at ${home}/lib/${lib_name}" 4
    fi
}

setup_async_profiler() {
    if [[ "${ASYNC_PROFILER_AVAILABLE}" != true ]]; then
        return
    fi

    if [[ -n "${ASYNC_PROFILER_PATH_ARG}" ]]; then
        RESOLVED_ASYNC_PROFILER_HOME="${ASYNC_PROFILER_PATH_ARG}"
        log "Using async-profiler from --async-profiler-path: ${RESOLVED_ASYNC_PROFILER_HOME}"
        validate_async_profiler_home "${RESOLVED_ASYNC_PROFILER_HOME}"
        return
    fi

    if [[ -n "${ASYNC_PROFILER_HOME:-}" ]]; then
        RESOLVED_ASYNC_PROFILER_HOME="${ASYNC_PROFILER_HOME}"
        log "Using async-profiler from ASYNC_PROFILER_HOME: ${RESOLVED_ASYNC_PROFILER_HOME}"
        validate_async_profiler_home "${RESOLVED_ASYNC_PROFILER_HOME}"
        return
    fi

    local archive_name extract_dir version_file
    archive_name="$(get_async_profiler_archive_name)"
    extract_dir="$(get_async_profiler_extract_dir)"
    version_file="${ASYNC_PROFILER_DOWNLOAD_DIR}/.version"

    RESOLVED_ASYNC_PROFILER_HOME="${ASYNC_PROFILER_DOWNLOAD_DIR}/${extract_dir}"

    if [[ -f "${version_file}" ]] && [[ "$(cat "${version_file}")" == "${ASYNC_PROFILER_VERSION}" ]]; then
        log "Using cached async-profiler ${ASYNC_PROFILER_VERSION}"
        validate_async_profiler_home "${RESOLVED_ASYNC_PROFILER_HOME}"
        return
    fi

    log "Downloading async-profiler ${ASYNC_PROFILER_VERSION} for ${OS}-${ARCH}..."
    mkdir -p "${ASYNC_PROFILER_DOWNLOAD_DIR}"

    local download_url="https://github.com/async-profiler/async-profiler/releases/download/v${ASYNC_PROFILER_VERSION}/${archive_name}"
    local tmp_file="${ASYNC_PROFILER_DOWNLOAD_DIR}/${archive_name}"

    curl -fSL -o "${tmp_file}" "${download_url}" \
        || die "Failed to download async-profiler from ${download_url}" 4

    local expected_sha actual_sha
    expected_sha="$(get_expected_sha256)"
    actual_sha="$(compute_sha256 "${tmp_file}")"

    if [[ "${actual_sha}" != "${expected_sha}" ]]; then
        rm -f "${tmp_file}"
        die "SHA-256 mismatch for ${archive_name}. Expected: ${expected_sha}, Got: ${actual_sha}" 4
    fi
    log "SHA-256 verified: ${actual_sha}"

    if [[ "${OS}" == "macos" ]]; then
        unzip -qo "${tmp_file}" -d "${ASYNC_PROFILER_DOWNLOAD_DIR}"
    else
        tar xzf "${tmp_file}" -C "${ASYNC_PROFILER_DOWNLOAD_DIR}"
    fi
    rm -f "${tmp_file}"

    echo "${ASYNC_PROFILER_VERSION}" > "${version_file}"
    log "async-profiler ${ASYNC_PROFILER_VERSION} installed at ${RESOLVED_ASYNC_PROFILER_HOME}"
    validate_async_profiler_home "${RESOLVED_ASYNC_PROFILER_HOME}"
}

ORIGINAL_PERF_EVENT_PARANOID=""

restore_perf_event_paranoid() {
    if [[ -n "${ORIGINAL_PERF_EVENT_PARANOID}" ]]; then
        log "Restoring perf_event_paranoid to ${ORIGINAL_PERF_EVENT_PARANOID}..."
        sudo sysctl -q kernel.perf_event_paranoid="${ORIGINAL_PERF_EVENT_PARANOID}" 2>/dev/null \
            || warn "Failed to restore perf_event_paranoid. Restore manually: sudo sysctl kernel.perf_event_paranoid=${ORIGINAL_PERF_EVENT_PARANOID}"
    fi
}

check_perf_event_paranoid() {
    [[ "${ASYNC_PROFILER_AVAILABLE}" == true ]] || return 0
    [[ "${OS}" == "linux" ]] || return 0

    local paranoid_file="/proc/sys/kernel/perf_event_paranoid"
    [[ -f "${paranoid_file}" ]] || return 0

    local val
    val="$(cat "${paranoid_file}")"
    if (( val <= 1 )); then
        return 0
    fi

    warn "perf_event_paranoid is ${val} (needs <= 1 for hardware CPU profiling)."
    echo -n "[PROMPT] Temporarily set perf_event_paranoid=1 for this run? (requires sudo) [y/N] " >&2
    local answer
    read -r answer
    case "${answer}" in
        [yY]|[yY][eE][sS])
            ORIGINAL_PERF_EVENT_PARANOID="${val}"
            trap restore_perf_event_paranoid EXIT INT TERM
            sudo sysctl -q kernel.perf_event_paranoid=1 \
                || die "Failed to set perf_event_paranoid. Try: sudo sysctl kernel.perf_event_paranoid=1" 4
            log "perf_event_paranoid set to 1 (will restore to ${val} on exit)."
            ;;
        *)
            warn "Keeping perf_event_paranoid=${val}. async-profiler will fall back to itimer mode (less accurate)."
            ;;
    esac
}

# --- CPU core pinning ---

detect_cores() {
    if [[ -n "${CORES_OVERRIDE}" ]]; then
        TASKSET_CORES="${CORES_OVERRIDE}"
        log "Using user-specified cores: ${TASKSET_CORES}"
        return
    fi

    if [[ "${OS}" != "linux" ]]; then
        return
    fi

    if ! command -v taskset >/dev/null 2>&1; then
        warn "'taskset' not found. Skipping CPU core pinning."
        return
    fi

    local freq_dir="/sys/devices/system/cpu"
    if [[ ! -f "${freq_dir}/cpu0/cpufreq/base_frequency" ]]; then
        return
    fi

    local -A freq_map
    local max_freq=0

    for cpu_dir in "${freq_dir}"/cpu[0-9]*; do
        local cpu_id="${cpu_dir##*cpu}"
        local freq_file="${cpu_dir}/cpufreq/base_frequency"
        [[ -f "${freq_file}" ]] || continue
        local freq
        freq="$(<"${freq_file}")"
        freq_map["${freq}"]+="${cpu_id} "
        if (( freq > max_freq )); then
            max_freq=${freq}
        fi
    done

    if (( ${#freq_map[@]} <= 1 )); then
        return
    fi

    local p_cores="${freq_map[${max_freq}]}"
    TASKSET_CORES="$(echo "${p_cores}" | xargs | tr ' ' ',')"
    TASKSET_CORES="${TASKSET_CORES%,}"
    log "Hybrid CPU detected. Pinning to P-cores (${max_freq} kHz): ${TASKSET_CORES}"
}

# --- Flamegraph conversion ---

convert_jfr_to_flamegraph() {
    local run_dir="$1"
    local jfrconv="${RESOLVED_ASYNC_PROFILER_HOME}/bin/jfrconv"

    if [[ ! -x "${jfrconv}" ]]; then
        warn "jfrconv not found at ${jfrconv}. Skipping flamegraph conversion. JFR recordings are still available."
        return
    fi

    local count=0
    for jfr_file in "${run_dir}"/profile-*.jfr; do
        [[ -f "${jfr_file}" ]] || continue
        local html_file="${jfr_file%.jfr}.html"
        log "Converting $(basename "${jfr_file}") to flamegraph..."
        "${jfrconv}" --cpu --lines "${jfr_file}" "${html_file}" \
            || warn "Failed to convert ${jfr_file} to flamegraph."
        ((count++))
    done

    if (( count > 0 )); then
        log "Async-profiler: ${count} recording(s) in ${run_dir}/"
    fi
}

# --- Build ---

build_generators() {
    local generators_built=()

    for profile in "${PROFILES[@]}"; do
        local gen
        gen="$(generator_for_profile "${profile}")"

        local already_built=false
        for built in "${generators_built[@]+"${generators_built[@]}"}"; do
            [[ "${built}" == "${gen}" ]] && already_built=true
        done
        [[ "${already_built}" == true ]] && continue

        log "Building bean generator: bean-generator-${gen}"
        (cd "${ROOT_DIR}/bean-generator-${gen}" && mvn -q clean install) \
            || die "Failed to build bean-generator-${gen}"
        generators_built+=("${gen}")
    done
}

build_benchmark_jars() {
    log "Cleaning benchmark module"
    (cd "${ROOT_DIR}/jmh-benchmarks" && mvn -q clean) \
        || die "Failed to clean jmh-benchmarks"

    for profile in "${PROFILES[@]}"; do
        log "Packaging benchmark JAR for profile: ${profile}"
        (cd "${ROOT_DIR}/jmh-benchmarks" && mvn -q package -P"${profile}") \
            || die "Failed to build benchmark JAR for profile ${profile}"
    done
}

# --- Run ---

run_benchmarks() {
    mkdir -p "${RESULTS_DIR}"

    local run_id
    run_id="$(date '+%Y%m%d-%H%M%S')"

    for profile in "${PROFILES[@]}"; do
        local jar="${ROOT_DIR}/jmh-benchmarks/target/bv-benchmarks-${profile}.jar"
        [[ -f "${jar}" ]] || die "Benchmark JAR not found: ${jar}. Run without --skip-build."

        local run_dir="${RESULTS_DIR}/${run_id}/${profile}"
        mkdir -p "${run_dir}"

        # Build command array
        local cmd=()

        if [[ -n "${TASKSET_CORES}" ]]; then
            cmd+=(taskset -c "${TASKSET_CORES}")
        fi

        cmd+=(java -jar "${jar}")

        # JVM args: user-supplied + async-profiler agent (when available)
        local jvm_args_parts=()
        [[ -n "${JVM_ARGS}" ]] && jvm_args_parts+=("${JVM_ARGS}")

        if [[ "${ASYNC_PROFILER_AVAILABLE}" == true ]]; then
            local lib_name
            lib_name="$(get_profiler_lib_name)"
            local agent_path="${RESOLVED_ASYNC_PROFILER_HOME}/lib/${lib_name}"
            local ap_out="${run_dir}/profile-%p.jfr"
            jvm_args_parts+=("-agentpath:${agent_path}=start,event=cpu,file=${ap_out},ann")
        fi

        if [[ ${#jvm_args_parts[@]} -gt 0 ]]; then
            cmd+=(-jvmArgs "${jvm_args_parts[*]}")
        fi

        # JMH options
        [[ -n "${FORKS}" ]]       && cmd+=(-f "${FORKS}")
        [[ -n "${WARMUP}" ]]      && cmd+=(-wi "${WARMUP}")
        [[ -n "${MEASUREMENT}" ]] && cmd+=(-i "${MEASUREMENT}")
        [[ -n "${THREADS}" ]]     && cmd+=(-t "${THREADS}")

        cmd+=(-rff "${run_dir}/jmh-results.json")
        cmd+=("${BENCHMARK_PATTERNS[@]+"${BENCHMARK_PATTERNS[@]}"}")

        # Log environment
        {
            echo "Profile:          ${profile}"
            echo "Date:             $(date)"
            echo "Java:             $(java -version 2>&1 | head -1)"
            echo "OS:               $(uname -srm)"
            if [[ "${ASYNC_PROFILER_AVAILABLE}" == true ]]; then
                echo "Async-profiler:   enabled (jfr+flamegraph)"
            else
                echo "Async-profiler:   unavailable (non-HotSpot JVM)"
            fi
            [[ -n "${TASKSET_CORES}" ]] && echo "Pinned cores:     ${TASKSET_CORES}"
            echo "Command:          ${cmd[*]}"
        } > "${run_dir}/environment.txt"

        log "Running benchmarks: ${profile}"
        log "  JAR: ${jar}"
        log "  Results: ${run_dir}"
        echo ""

        "${cmd[@]}" 2>&1 | tee "${run_dir}/output.log"

        echo ""

        if [[ "${ASYNC_PROFILER_AVAILABLE}" == true ]]; then
            convert_jfr_to_flamegraph "${run_dir}"
        fi

        log "Results saved to: ${run_dir}"
    done

    log "All runs complete. Results in: ${RESULTS_DIR}/${run_id}/"
}

# --- Main ---

main() {
    parse_args "$@"
    detect_platform
    check_prerequisites
    check_jvm_hotspot
    setup_async_profiler
    check_perf_event_paranoid
    detect_cores

    if [[ "${SKIP_BUILD}" != true ]]; then
        build_generators
        build_benchmark_jars
    fi

    run_benchmarks
}

main "$@"
