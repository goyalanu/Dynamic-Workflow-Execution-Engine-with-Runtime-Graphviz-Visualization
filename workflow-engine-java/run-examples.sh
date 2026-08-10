#!/usr/bin/env sh
set -eu

BUILD_DIR="build/classes"
mkdir -p "$BUILD_DIR"

find src/main/java -name '*.java' > build/example-sources.txt
javac -d "$BUILD_DIR" @build/example-sources.txt
java -cp "$BUILD_DIR:src/main/resources" com.example.workflow.examples.ExampleRunner

if command -v dot >/dev/null 2>&1; then
  dot -Tsvg generated/success.dot -o generated/success.svg
  dot -Tsvg generated/failure.dot -o generated/failure.svg
  echo "wrote $(pwd)/generated/success.svg"
  echo "wrote $(pwd)/generated/failure.svg"
else
  echo "Graphviz dot not found; generated dashboard.html will show SVGs after dot renders them."
fi
