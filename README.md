# Dynamic Workflow Execution Engine - Java

Java 17 implementation of the workflow proposal in `../task-proposal-1.md`.

It supports:

- configuration-driven workflow definitions
- unique workflow run IDs
- idempotent step execution within a run
- dependency-aware execution with parallel ready steps
- persisted runtime state through a repository abstraction
- retry metadata for transient failures
- blocked-step propagation after permanent failures
- runtime Graphviz/DOT generation from persisted state

The included implementation uses an in-memory repository for local execution
and tests. PostgreSQL DDL is provided in `src/main/resources/db/schema.sql`.

## Run Tests

```bash
./run-tests.sh
```

## Generate Example DOT Files

```bash
./run-examples.sh
```

Outputs are written to `generated/success.dot` and `generated/failure.dot`.
If Graphviz `dot` is available, the script also renders SVGs and writes a
small dashboard at `generated/dashboard.html`.

If Graphviz is installed:

```bash
dot -Tpng generated/failure.dot -o generated/failure.png
```

## Run Live Service

```bash
./run-service.sh 8080
```

Then open:

```text
http://localhost:8080/dashboard
```

Useful endpoints:

- `POST /api/runs?mode=success`
- `POST /api/runs?mode=failure`
- `POST /api/runs?mode=retry`
- `GET /api/runs/{runId}/state`
- `GET /api/runs/{runId}/graph.dot`
- `GET /api/runs/{runId}/graph.svg`
