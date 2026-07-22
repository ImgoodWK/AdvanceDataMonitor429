# TeXTech Card Battle Server

Standalone Node/TS authority for the card game. It does **not** require Minecraft,
a Forge server, or an MC tick loop. Runs, matches, and pending rewards are persisted
under `CARDBATTLE_DATA_DIR`.

```powershell
copy .env.example .env
npm.cmd install
npm.cmd run build
npm.cmd start
```

Default: `http://127.0.0.1:8787`  
Health: `GET /api/health`

For a self-contained frontend + backend image, build from the repository root:

```powershell
docker build -f cardbattle-server/Dockerfile -t textech-cardbattle .
docker run --rm -p 8787:8787 -v cardbattle-data:/data -e CARDBATTLE_DEV_TOKEN=replace-me textech-cardbattle
```

Without an MC bridge, item rewards remain in the backend ledger and are shown in
the lobby. A bridge may mark a reward claimed only when
`CARDBATTLE_BRIDGE_TOKEN` is configured and supplied through the private bridge
header after external delivery.

See `docs/zh/cardbattle/README.md`.
