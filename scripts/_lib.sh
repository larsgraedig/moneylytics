# Shared output-verbosity helpers. Sourced (not executed) by scripts/*.sh
# that accept a "-v"/"--verbose" flag. Relies on "$@" of the sourcing script.

VERBOSE=0
for _arg in "$@"; do
  case "$_arg" in
    -v | --verbose) VERBOSE=1 ;;
  esac
done

# run <command|function> [args...]
# Runs a command, suppressing its stdout+stderr unless VERBOSE=1 or it fails.
# On failure, prints the captured output and exits with the command's status.
run() {
  if [ "$VERBOSE" = "1" ]; then
    "$@"
    return
  fi

  local log
  log="$(mktemp)"
  if "$@" >"$log" 2>&1; then
    rm -f "$log"
  else
    local status=$?
    echo "❌ ERROR: command failed: $*" >&2
    cat "$log" >&2
    rm -f "$log"
    exit "$status"
  fi
}
