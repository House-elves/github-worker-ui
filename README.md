# github-worker-ui

Web dashboard for monitoring and controlling the [github-worker](../github-worker/) House Elf. Built with Quarkus.

## Install

Requires the `github-worker` to be installed first.

```bash
./install.sh
```

The installer will:

1. Build the Quarkus production jar
2. Install it to `~/.local/share/github-worker-ui/`
3. Set up an auto-starting service (systemd on Linux, launchd on macOS)
4. Optionally add `github-worker.house-elves` to `/etc/hosts`

## Access

- **http://github-worker.house-elves:7478** (if hostname was added)
- **http://localhost:7478** (always works)

## Features

- **Discovery panel** — on-demand topic-based issue discovery. Expand the panel to load, with 👀 and assign quick actions. Refresh button for manual re-fetch.
- **Manual tracking** — + button in the header to add any issue or PR by URL or `owner/repo#123`
- **Issues panel** — tracked issues with current state, title, PR link, last updated. Click a row to see the state machine flow diagram with the current step highlighted. Retry (↻) and remove (✕) buttons per row.
- **Reviews panel** — tracked review requests with state badges and retry/remove buttons
- **Logs panel** — Worker and Claude tabs for systemd journal and Claude agent output
- **Config panel** — view and edit all configuration values. Secrets are masked, booleans render as toggle switches.
- **Trigger Now** — run the worker immediately from the UI
- **Dark/light theme** — toggle in the header, persists via localStorage
- **Live updates** — WebSocket pushes state changes to the browser every 5 seconds

## Development

```bash
./mvnw quarkus:dev
```

The dev UI is available at http://localhost:7478/q/dev/

## API

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/state` | GET | Current worker state (issues, reviews, discoveries) |
| `/api/state/add` | POST | Add an issue or PR manually (`{"item":"owner/repo#123"}`) |
| `/api/state/{key}/retry` | POST | Reset a tracked item to a retryable state |
| `/api/state/{key}` | DELETE | Remove a tracked item |
| `/api/config` | GET | Config values (secrets redacted) |
| `/api/config` | PUT | Update config values |
| `/api/logs` | GET | Recent worker logs (systemd journal) |
| `/api/logs/claude` | GET | Recent Claude agent logs |
| `/api/trigger` | POST | Trigger a worker run |
| `/api/preview` | POST | Run worker in preview mode |
| `/api/discover` | POST | Run on-demand topic discovery |
| `/api/react/{owner}/{repo}/{number}` | POST | Add 👀 reaction to an issue/PR |
| `/api/assign/{owner}/{repo}/{number}` | POST | Self-assign an issue |
| `/api/live` | WebSocket | Live state updates |
