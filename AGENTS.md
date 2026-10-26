# Agent instructions

Velocity proxy plugin (Java 25, `velocity-api` 4.2.0, Gradle). See `README.md` for behavior.

| Task | Command |
|------|---------|
| Build + tests | `./gradlew build` |
| Tests + publish preflight | `./gradlew check` |
| Tests only | `./gradlew test` |
| Publish (CI) | `./gradlew publishMavenPublicationToSneakyrpRepository` (requires Maven credentials) |

Builds need a git checkout with at least one commit (version comes from `HEAD`). Output goes to
`~/.cache/SneakyReconnect-gradle-build`.

- Keep logic that can be tested without a proxy in `ReconnectSession` / `ReconnectSettings`, and cover it in `src/test/`.
- `ReconnectManager` state is guarded by `synchronized (this)`; events, ping callbacks and the ticker run on different threads.
- Pushing to `main` publishes to maven.sneakyrp.com. The POM's `sneakyrp.deployment.type` must stay `velocity-plugin` so Mother Mouse only offers it to proxies.
- No secrets in the repo; Maven credentials come from Infisical in CI.
