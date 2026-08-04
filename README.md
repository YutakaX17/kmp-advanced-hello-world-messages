# KMP Advanced Hello World Messages

Kotlin Multiplatform message domain and durable local-first storage package.

`SqlDelightMessageRepository` validates message text and atomically writes the
message plus its CREATE outbox operation. Local reads survive process restarts,
and `observeLocal` publishes SQLDelight invalidation updates as a `Flow`.
Messages are ordered by a normalized integer timeline rather than mixed SQLite
TEXT/INTEGER values.

The committed version-1 schema snapshot and numbered migration are verified
during `check`. The platform owns the SQLDelight driver; Android driver wiring
and backend synchronization follow in their ordered implementation slices.

## Build

Production builds resolve the immutable KMP core `0.1.0` coordinate. For
adjacent-repository development only, clone KMP core beside this repository and
opt into composite substitution:

```bash
./gradlew -PuseLocalKmpCore=true check
```
