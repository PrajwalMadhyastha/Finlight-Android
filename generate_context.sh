#!/bin/bash
#
# This script generates the project context files for the Finlight app,
# splitting the output into three manageable files:
# 1. project_context.txt: Main config, build files, and core/analyzer source.
# 2. app_src.txt: Main source code for the :app module.
# 3. app_tests.txt: All test code (unit + instrumented) for the :app module.
#
# Run this script from the root of your project.
# Make it executable first: chmod +x generate_context.sh
#

# Exit immediately if a command exits with a non-zero status.
set -e

# --- 1. Generate project_context.txt (Main Config + Core) ---
echo "Generating project_context.txt (Main Config + Core)..."
(
  echo "Project Context for Finlight (Main Config + Core)" && \
  echo "Generated on: $(date)" && \
  echo "========================================" && echo "" && \
  echo "================== FILE: ./build.gradle.kts ==================" && cat ./build.gradle.kts && echo -e "\n\n" && \
  echo "================== FILE: ./settings.gradle.kts ==================" && cat ./settings.gradle.kts && echo -e "\n\n" && \
  echo "================== FILE: ./version.properties ==================" && cat ./version.properties && echo -e "\n\n" && \
  echo "================== FILE: ./train_sms_classifier.py ==================" && cat ./train_sms_classifier.py && echo -e "\n\n" && \
  echo "================== FILE: ./.github/workflows/android-ci.yml ==================" && cat ./.github/workflows/android-ci.yml && echo -e "\n\n" && \
  echo "################## MODULE: app ##################" && echo "" && \
  echo "================== FILE: ./app/build.gradle.kts ==================" && cat ./app/build.gradle.kts && echo -e "\n\n" && \
  echo "================== FILE: ./app/src/main/AndroidManifest.xml ==================" && cat ./app/src/main/AndroidManifest.xml && echo -e "\n\n" && \
  find ./app/src/main/res/xml -name "*.xml" -exec sh -c 'echo "================== FILE: {} =================="; cat "{}"; echo -e "\n\n"' \; && \
  echo "################## MODULE: core ##################" && echo "" && \
  echo "================== FILE: ./core/build.gradle.kts ==================" && cat ./core/build.gradle.kts && echo -e "\n\n" && \
  echo "---------- core: Main Source Files ----------" && \
  find ./core/src/main/java -name "*.kt" -exec sh -c 'echo "================== FILE: {} =================="; cat "{}"; echo -e "\n\n"' \; && \
  echo "################## MODULE: analyzer ##################" && echo "" && \
  echo "================== FILE: ./analyzer/build.gradle.kts ==================" && cat ./analyzer/build.gradle.kts && echo -e "\n\n" && \
  echo "---------- analyzer: Main Source Files ----------" && \
  find ./analyzer/src/main/java -name "*.kt" -exec sh -c 'echo "================== FILE: {} =================="; cat "{}"; echo -e "\n\n"' \;
) > project_context.txt
echo "Done."

# --- 2. Generate app_src.txt (:app Module Source) ---
echo "Generating app_src.txt (:app module source)..."
(
  echo "Project Context for Finlight (:app module source)" && \
  echo "Generated on: $(date)" && \
  echo "========================================" && echo "" && \
  echo "################## MODULE: app ##################" && echo "" && \
  echo "---------- app: Main Source Files ----------" && \
  find ./app/src/main/java -name "*.kt" -exec sh -c 'echo "================== FILE: {} =================="; cat "{}"; echo -e "\n\n"' \;
) > app_src.txt
echo "Done."

# --- 3. Generate app_tests.txt (:app Module Tests) ---
echo "Generating app_tests.txt (:app module tests)..."
(
  echo "Project Context for Finlight (:app module tests)" && \
  echo "Generated on: $(date)" && \
  echo "========================================" && echo "" && \
  echo "################## MODULE: app (Tests) ##################" && echo "" && \
  echo "---------- app: Instrumented Test Files (UI Tests) ----------" && \
  find ./app/src/androidTest/java -name "*.kt" -exec sh -c 'echo "================== FILE: {} =================="; cat "{}"; echo -e "\n\n"' \; && \
  echo "---------- app: Unit Test Files ----------" && \
  find ./app/src/test/java -name "*.kt" -exec sh -c 'echo "================== FILE: {} =================="; cat "{}"; echo -e "\n\n"' \;
) > app_tests.txt
echo "Done."

echo -e "\nAll context files generated successfully!"