# SneakyReconnect

Velocity plugin. When a watched server (for example `dev`) restarts and the proxy moves you to a
fallback server, it sends you back once the server is up again.

1. You're kicked from `dev` and the proxy moves you to another server (for example `live`).
   Chat says you'll be reconnected.
2. The plugin pings `dev` until it goes offline, then until it comes back.
3. When it's back, chat shows a countdown (5 seconds by default) and the action bar counts down.
4. You're connected to `dev`.

Click any of its chat messages, or run `/autoreconnect cancel`, to stay where you are.
Switching servers yourself (for example `/server hub`) also cancels it.

It only handles kicks that move you to another server. If the proxy disconnects you entirely, the
plugin can't do anything.

If `dev` never goes offline within `shutdown-timeout-seconds` of the kick, it assumes the kick
wasn't a restart (for example `/kick`) and stops. This also stops it from sending you back into a
server that is still shutting down.

## Install

Run `/plugins update` in Discord (Mother Mouse) to upload the latest release to the proxy, or put
`SneakyReconnect-<version>.jar` in the proxy's `plugins/` folder yourself. Restart the proxy to
load it. Edit `plugins/sneakyreconnect/config.yml` so `servers` lists the names from `velocity.toml`, then
run `/autoreconnect reload` (permission `sneakyreconnect.reload`).

## Build

JDK 25. Compiles against `velocity-api` 4.2.0.

```powershell
.\gradlew.bat build
```

Output: `~/.cache/SneakyReconnect-gradle-build/libs/SneakyReconnect-<version>.jar` (kept off the
checkout so NTFS/FUSE mounts don't wedge Gradle). The version is `yyyy.MM.dd-<12-char git hash>`,
so builds need a git checkout with at least one commit.

`./gradlew check` runs the tests and `scripts/publish-preflight.sh`.

## Publishing

Every push to `main` runs `.github/workflows/publish.yml`, which calls the shared
[`Team-Sneakymouse/ci`](https://github.com/Team-Sneakymouse/ci) Gradle workflow. It runs `check`,
fetches Maven credentials from Infisical, and publishes to `https://maven.sneakyrp.com/releases`
as `io.github.team-sneakymouse:sneakyreconnect:<version>`. Pull requests run `ci.yml`, which
builds and checks without publishing.

The POM sets `sneakyrp.deployment.type` to `velocity-plugin`. Mother Mouse never deploys these
automatically; it only offers them to proxy servers through `/plugins update`. Publishing does not
restart the proxy.

Public repositories inherit the `INFISICAL_IDENTITY_ID` / `INFISICAL_DOMAIN` organization secrets.
A private repository must set them itself. The Infisical identity must allow OIDC from
`Team-Sneakymouse/SneakyReconnect`.

## Code

- `ReconnectSession`: the wait / countdown state machine. No Velocity types; unit tested.
- `ReconnectManager`: Velocity events, pings, scheduler, chat messages.
- `AutoReconnectCommand`: `/autoreconnect cancel|reload`.
