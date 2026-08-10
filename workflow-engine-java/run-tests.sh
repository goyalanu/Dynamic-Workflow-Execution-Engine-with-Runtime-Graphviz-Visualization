#!/usr/bin/env sh
set -eu

BUILD_DIR="build/classes"
rm -rf build
mkdir -p "$BUILD_DIR"

find src/main/java src/test/java -name '*.java' > build/sources.txt
javac -d "$BUILD_DIR" @build/sources.txt
java -cp "$BUILD_DIR:src/main/resources" com.example.workflow.WorkflowEngineTests
