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

## Repo-specific rules

- Document every new config key (`servers`, `reconnect-delay-seconds`, `shutdown-timeout-seconds`, `startup-timeout-seconds`, ...) in the README Install section, and keep defaults safe: an empty `servers` list must do nothing.
- Don't block inside `synchronized (this)` in `ReconnectManager` (no pings or chat calls under the lock); use callbacks and re-enter the lock.
- A push to `main` only publishes to Maven; the jar reaches the proxy on `/plugins update` and needs a proxy restart, so tell Rawb. This is not the dev server's auto-deploy path.
- This is a Velocity plugin: use `com.velocitypowered.api`, no Bukkit/Paper classes, no SneakyUtils.
