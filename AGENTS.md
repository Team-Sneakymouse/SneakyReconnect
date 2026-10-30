# Agent instructions

Resolve shared policy using `.team-policy/pointer.json` (paths relative to this repository). Clone the configured remote into the first candidate if missing; otherwise fetch and fast-forward the tracked branch when safe. Report blocked updates.

Before implementing, read the resolved checkout's `README.md` and follow its linked guidance relevant to the task.

This is a Velocity proxy plugin: the Paper plugin conventions and SneakyUtils do not apply.
