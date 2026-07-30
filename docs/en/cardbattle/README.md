# Standalone Card Battle integration

The Card Battle frontend, Node/TypeScript backend, game rules, assets, accounts, and runtime data have been separated from the ADM mod repository. ADM no longer embeds a Card Battle HTTP server, starts one with a world, or packages the SPA and card art in the mod jar.

The standalone [TeXTech: Overclocked Arcana](https://github.com/ImgoodWK/TeXTech-Overclocked-Arcana) repository is authoritative for the game implementation, assets, and design documentation. ADM maintains only the Minecraft integration adapter.

## Capabilities retained by ADM

- `[cardBattle] externalApiBaseUrl`: standalone service URL.
- `[cardBattle] bridgeToken`: private shared secret matching `CARDBATTLE_BRIDGE_TOKEN` in the standalone service.
- `/textech card status`: checks `GET /api/bridge/v1/status`.
- `/textech card bind`: calls `POST /api/bridge/v1/bind-codes` for the current player.

The bridge stays disabled when either setting is empty. Legacy `enabled`, `port`, `bindAddress`, `devToken`, `serverDir`, `frontendDir`, and `nodePath` keys are no longer read; stale keys in an existing cfg cannot start a service.

## Not enabled yet

The standalone service can retain a pending-reward ledger, but ADM does not fetch rewards or grant items. Automatic redemption must remain disabled until the item allowlist, full-inventory behavior, world-side idempotency ledger, and administrator audit flow are implemented. See the [reward bridge](rewards-bridge.md) for the exact boundary.
