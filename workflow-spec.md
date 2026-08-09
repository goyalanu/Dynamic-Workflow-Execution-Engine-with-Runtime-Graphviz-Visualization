Workflow: OrderFulfillment

Description:
A configurable execution workflow for order fulfillment. Each run is tracked independently by a unique workflow run ID. The workflow persists execution state in PostgreSQL and generates runtime Graphviz output from persisted state.

Steps:
1. ValidateOrder
2. ProcessPayment
3. ReserveInventory
4. GenerateInvoice
5. SendNotification

Dependencies:
ValidateOrder: []
ProcessPayment: [ValidateOrder]
ReserveInventory: [ProcessPayment]
GenerateInvoice: [ProcessPayment]
SendNotification: [ReserveInventory, GenerateInvoice]

Allowed states:
PENDING
RUNNING
COMPLETED
FAILED
BLOCKED
RETRYING

State rules:
- Steps begin in `PENDING`.
- A step may transition to `RUNNING` only when all required dependencies are `COMPLETED`.
- A `RUNNING` step may transition to `COMPLETED` or `FAILED`.
- A step that fails and still has retries remaining transitions to `RETRYING` and may return to `RUNNING` according to the retry policy.
- A step with no retries remaining transitions to permanent `FAILED`.
- Any step dependent on a permanently `FAILED` prerequisite transitions to `BLOCKED` and is not executed.
- `BLOCKED` steps do not satisfy dependency requirements and remain blocked for the duration of the workflow run.

Retry policy:
- `maxRetries`: 2
- `retryIntervalSeconds`: 5
- `backoff`: exponential
- Retries apply to transient failures on any step unless the failure is classified as permanent.
- When a step is retried, the workflow must persist retry count and next retry metadata.

Run identification and idempotency:
- Each workflow execution is tracked by a unique workflow run ID.
- Multiple workflow runs may coexist in PostgreSQL.
- Duplicate execution requests for the same workflow run ID and step must be idempotent.
- If a step is already `RUNNING`, `COMPLETED`, `FAILED`, or `BLOCKED`, repeated execution requests must not execute the step again or corrupt persisted state.

Visualization requirement:
- Runtime Graphviz/DOT output must be generated from persisted PostgreSQL state for a specific workflow run.
- The generated graph must include step names, current state, and dependency edges.
- Node styling should distinguish states such as `COMPLETED`, `RUNNING`, `PENDING`, `FAILED`, and `BLOCKED`.

Execution examples:
- Successful path:
  - `ValidateOrder` → `COMPLETED`
  - `ProcessPayment` → `COMPLETED`
  - `ReserveInventory` → `RUNNING`
  - `GenerateInvoice` → `PENDING`
  - `SendNotification` → `PENDING`
- Failure path:
  - `ValidateOrder` → `COMPLETED`
  - `ProcessPayment` → `COMPLETED`
  - `ReserveInventory` → `FAILED`
  - `GenerateInvoice` → `BLOCKED`
  - `SendNotification` → `BLOCKED`

Concurrency note:
- `ReserveInventory` and `GenerateInvoice` may execute concurrently after `ProcessPayment` completes.
- `SendNotification` can start only when all required dependencies are `COMPLETED`.
- If any required dependency becomes permanently `FAILED`, `SendNotification` becomes `BLOCKED`.
