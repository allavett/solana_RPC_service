#!/usr/bin/env bash
# Minimal Gradle launcher for environments where we can't commit the full Gradle wrapper.

if ! command -v gradle >/dev/null 2>&1; then
  echo "Error: 'gradle' command not found on PATH. Please install Gradle." >&2
  exit 1
fi

gradle "$@"
