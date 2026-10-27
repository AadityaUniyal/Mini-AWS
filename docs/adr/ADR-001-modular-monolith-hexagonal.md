# ADR-001: Modular Monolith with Hexagonal Architecture vs Microservices

## Status
Accepted

## Context
MiniCloud is engineered to simulate a full-featured AWS cloud environment (EC2, S3, IAM, VPC, CloudWatch, Lambda, SQS, SNS, FinOps Billing) directly on consumer laptops without cloud infrastructure costs.

The traditional architecture for cloud emulators often falls into one of two extremes:
1. **Distributed Microservices**: Emulating each service as an independent container or process (e.g., Docker Compose with 10+ services). This causes massive memory overhead (>4GB RAM idle), slow cold starts (>60s), fragile IPC network ports, and high CPU usage that degrades laptop battery life.
2. **Spaghetti Monolith**: Consolidating all logic into tightly coupled services with direct database cross-talk, making it unmaintainable, hard to test, and non-modular.

## Decision
We adopted a **Modular Monolith with Hexagonal (Ports & Adapters) Architecture** inside a single Spring Boot 3 process:
- Each cloud subsystem is an isolated domain module (`compute`, `storage`, `network`, `iam`, `monitoring`, `billing`, `messaging`, `aws`).
- In-process communication is decoupled through typed domain events (`S3ObjectCreatedEvent`, `InstanceStateChangedEvent`) published via Spring's `ApplicationEventPublisher`.
- Dual interfaces: Wire protocol adapters (`AwsS3WireController`, AWS SigV4 auth) translate standard AWS SDK HTTP requests directly to domain service ports; internal REST adapters serve the management console and Swing UI.
- Single-process H2 embedded relational database with MVCC and zero-latency in-memory query execution.

## Consequences
### Positive
- **Instantaneous Cold Start**: The complete cloud platform boots in under 4 seconds on standard laptop hardware.
- **Minimal Footprint**: Operates comfortably in under 250MB heap RAM with near-zero idle CPU consumption.
- **Zero Inter-Process Latency**: S3 event dispatch to Lambda execution occurs in sub-millisecond in-process time.
- **Strict Domain Boundaries**: Modules can be extracted into standalone microservices in the future with zero changes to domain business logic.

### Negative / Trade-offs
- Relies on in-process JVM execution; process failure crashes the whole simulated cloud (mitigated via automated PID tracking and self-healing chaos engineering tests).
