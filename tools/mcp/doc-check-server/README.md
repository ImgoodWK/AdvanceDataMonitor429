# TeXTech doc-check MCP (stdio)

Wraps `tools/doc-check/doc-consistency-check.py` and exposes focused tools.

## Tools

| Name | Purpose |
|------|---------|
| `run_doc_consistency_check` | Full script (packets, stale phrases, zh/en drift, worldMap, lang, manual) |
| `check_lang_parity` | `en_US.lang` vs `zh_CN.lang` key sets |
| `check_manual_chapters` | `manual/index.json` vs `chapters/*.json` |

Registered in `.cursor/mcp.json` as `textech-doc-check`. Reload MCP after changes.
