# KMP Advanced Hello World Messages

Kotlin Multiplatform message domain, validation, local persistence, durable
outbox, remote integration, and synchronization package.

The current foundation defines the message domain and SQLDelight schema. Network
and sync implementations follow after the backend idempotency contract is fixed.

## Build

Clone `kmp-advanced-hello-world-core` beside this repository, then run:

```bash
./gradlew check
./gradlew publishAllPublicationsToLocalRepository
```
