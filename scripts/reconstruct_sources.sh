#!/usr/bin/env bash
set -euo pipefail
cat app/src/main/java/fr/cap6/app/MainActivity.kt.part* > app/src/main/java/fr/cap6/app/MainActivity.kt
