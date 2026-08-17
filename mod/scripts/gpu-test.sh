#!/usr/bin/env bash
#
# Run the compute suite on the GPU path, so the OpenCL kernel is compiled by a real clBuildProgram.
#
# Without this, `LB_DEV_TEST=compute ./gradlew runServer` silently falls back to the CPU: the suite
# still reports 6/6 PASS, but its three GPU checks are SKIPped and the kernel is never built. That
# is worth a script rather than a note, because the difference between 6/6 and 9/9 is easy to miss.
#
# Two things trip it up on Fedora, neither of which needs root:
#
#   1. Mesa's OpenCL (rusticl) registers its ICD but exposes NO device unless RUSTICL_ENABLE names
#      the driver. clinfo then lists a platform with nothing in it, which reads like "no GPU".
#   2. JOCL dlopens the UNVERSIONED "libOpenCL.so". Fedora's OpenCL-ICD-Loader ships only
#      libOpenCL.so.1; the bare symlink lives in the -devel package. Missing it surfaces as
#      "UnsatisfiedLinkError: Implementation library could not be loaded", which looks like a
#      missing GPU rather than a missing symlink. We create our own under build/ instead.
#
# Prerequisite (once): sudo dnf install mesa-libOpenCL
# Optional:            sudo dnf install OpenCL-ICD-Loader-devel   # then step 2 is unnecessary
#
# Usage: mod/scripts/gpu-test.sh [suite]     (default: compute)
set -euo pipefail

SUITE="${1:-compute}"
MOD_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SHIM_DIR="$MOD_DIR/build/opencl-shim"

OPENCL_LOADER=""
for candidate in /usr/lib64/libOpenCL.so.1 /usr/lib/x86_64-linux-gnu/libOpenCL.so.1; do
  [ -e "$candidate" ] && { OPENCL_LOADER="$candidate"; break; }
done
if [ -z "$OPENCL_LOADER" ]; then
  echo "No OpenCL loader (libOpenCL.so.1) found. Install mesa-libOpenCL, then re-run." >&2
  exit 1
fi

mkdir -p "$SHIM_DIR"
ln -sf "$OPENCL_LOADER" "$SHIM_DIR/libOpenCL.so"

echo "loader   : $OPENCL_LOADER"
echo "shim     : $SHIM_DIR/libOpenCL.so"
echo "suite    : $SUITE"
command -v clinfo >/dev/null && RUSTICL_ENABLE=radeonsi clinfo -l 2>/dev/null | sed 's/^/clinfo   : /'

exec env \
  RUSTICL_ENABLE="${RUSTICL_ENABLE:-radeonsi}" \
  LD_LIBRARY_PATH="$SHIM_DIR${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}" \
  LB_DEV_TEST="$SUITE" \
  "$MOD_DIR/gradlew" -p "$MOD_DIR" runServer --console=plain
