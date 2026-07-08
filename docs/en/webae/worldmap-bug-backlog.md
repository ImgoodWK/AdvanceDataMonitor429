# World Map Bug Backlog

> Last updated: 2026-07  
> Multi-source rendering is merged; the two bugs below are **still open** per user testing.

See [Chinese version](../zh/webae/worldmap-bug-backlog.md) for full detail.

## Bug A: AE chunks show overlay only, no terrain

- **Symptom**: Chunks with AE devices render AE tint but terrain stays blank.
- **Next steps**: Decouple terrain/ae enqueue (A2), align quality tiers (A3), fix upgrading cache-bust (A4).

## Bug B: AE badge keeps flashing on non-AE chunks

- **Symptom**: Chunks without AE devices still show pending AE corner badge after render completes.
- **Next steps**: `showAe={aeVisible}` (B1), skip ae enqueue for empty chunks (B2), narrow AE prefetch (B4).

## Priority

Implement **A2 + B1 + B2** first.
