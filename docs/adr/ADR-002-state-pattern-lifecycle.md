# ADR-002: Deterministic State Pattern for Virtual Machine Instance Lifecycles

## Status
Accepted

## Context
In cloud virtualization (such as AWS EC2), instances transition through a strictly governed lifecycle:
`PENDING -> RUNNING -> STOPPING -> STOPPED -> TERMINATED`.

A naive implementation relies on procedural switch-cases or mutable status flags in controller methods. This anti-pattern introduces severe issues:
1. **Illegal State Transitions**: An instance in `TERMINATED` could erroneously be restarted, corrupting filesystem state.
2. **Race Conditions**: Concurrent API requests (e.g., simultaneous `StopInstance` and `RebootInstance`) can create orphaned background OS processes and leaked ports.
3. **Scattered Side Effects**: Spawning OS processes, releasing assigned ENIs (Elastic Network Interfaces), and updating billing meters get scattered across different service classes.

## Decision
We implemented the **Gang of Four (GoF) State Pattern** via `InstanceStateContext` and a deterministic transition matrix:
- Each state (`PendingState`, `RunningState`, `StoppingState`, `StoppedState`, `TerminatedState`) is encapsulated in a dedicated state handler implementing `InstanceStateHandler`.
- Valid transitions are strictly defined in an immutable `Map<InstanceState, Set<InstanceState>>` matrix.
- Every state mutation verifies transition validity, executes necessary lifecycle actions (e.g., invoking `ProcessManager` to spawn or kill local runtime processes, releasing private IP allocations), and emits state change telemetry events.
- Invalid transition requests fail fast with standard HTTP 409 Conflict and descriptive error codes.

## Consequences
### Positive
- **Mathematical Correctness**: Impossible state jumps (e.g. `TERMINATED -> RUNNING`) are structurally impossible at the type and matrix validation levels.
- **Auditable Side Effects**: All process terminations, IP deallocations, and billing meter terminations are guaranteed to execute during state transitions.
- **Extensibility**: Adding new states (such as `HIBERNATING` or `CRASHED`) requires implementing one handler and updating the transition graph without modifying core business loops.

### Negative / Trade-offs
- Modest increase in class count (one interface plus concrete state handlers) compared to simple string enum assignment.
