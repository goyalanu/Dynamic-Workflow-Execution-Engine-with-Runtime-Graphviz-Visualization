#!/usr/bin/env sh
set -eu

BUILD_DIR="build/classes"
mkdir -p "$BUILD_DIR"

find src/main/java -name '*.java' > build/service-sources.txt
javac -d "$BUILD_DIR" @build/service-sources.txt
java -cp "$BUILD_DIR:src/main/resources" com.example.workflow.service.WorkflowHttpServer "${1:-8080}"
