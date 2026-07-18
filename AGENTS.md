# TeXTech / AdvanceDataMonitor429 Agent Guidance

This is the Codex entry point for the repository. Keep it short and use the
existing `.cursor/rules/` files as detailed, shared project knowledge. Cursor
rules are not automatically loaded by Codex; read the relevant rule before
working in that area.

## Working agreement

- Start by checking the current diff and preserve existing user changes. Do
  not reset, discard, or reformat unrelated work.
- Treat the source code and machine-readable data as the implementation
  authority. If a document is stale, update the document to match the code;
  do not change working code merely to match stale prose.
- For a Chinese task, prefer `docs/zh/`; for an English task, prefer
  `docs/en/`. Read the other language tree only when publishing or checking a
  bilingual change. Do not bulk-read `docs/*/design/` or `docs/*/archive/`
  unless the task explicitly needs them.
- Use `docs/zh/developer/documentation-map.md` (or its English counterpart)
  to locate the affected documentation before editing.
- Explain the final result in the user's language and list both synchronized
  paths and any intentionally skipped paths.

## Documentation synchronization

Documentation is part of a behavior-changing implementation task, not a
separate follow-up. When the change affects public behavior, update the
relevant developer documentation, player/admin documentation, in-game manual,
language keys, and agent rules in the same task. For a private refactor,
formatting-only change, or a user instruction that explicitly says not to
touch docs, check the map but do not manufacture documentation changes.

Use this routing table:

| Change | Update/check |
|---|---|
| New, removed, or renamed Java package/class | `.cursor/rules/project-structure.mdc` and the matching section of `project-structure-details.mdc`; relevant technical docs |
| Config key or default | `Config.java` / `Config*Loader.java`, `ConfigDescriptions.java`, relevant developer and player docs, and `manual/chapters/config_reference.json` |
| Network packet or packet ID | `LoaderNetwork.java`, `.cursor/rules/network-packets.mdc`, and the network section of the technical docs |
| AI assistant, voice, or AI command behavior | `.cursor/rules/ai-assistant.mdc`, `.cursor/rules/ai-assistant-docs-sync.mdc`, `docs/*/ai-assistant/`, assistant feature data, lexicon, GUI text, and both language files as applicable |
| WebAE server/API behavior | `docs/*/webae/developer-guide.md`, `docs/*/webae/user-guide.md`, the matching manual chapter, and `.cursor/rules/webae-*.mdc` |
| WebAE frontend behavior or UI contract | `webae-frontend/`, built WebAE assets when the build workflow requires them, the WebAE docs/manual, i18n keys, tests, and `.cursor/rules/webae-frontend*.mdc` |
| Player-visible item, block, command, UI, or mechanic | `docs/*/player/`, the matching `src/main/resources/assets/textech/manual/chapters/*.json`, `manual/index.json` if chapter membership changes, and `lang/{zh_CN,en_US}.lang` |
| Rendering, GUI, or client-only behavior | `.cursor/rules/gui-guidelines.mdc`, relevant `project-structure-details.mdc` section, and player/manual docs when externally visible |

When a user-visible text key is added or changed, keep `zh_CN.lang` and
`en_US.lang` structurally aligned. When a public behavior is released, update
the counterpart language-tree document unless the change is intentionally
single-language and that decision is recorded in the final summary.

## Detailed rule and workflow routing

Read only what applies:

- General layout and package ownership: `.cursor/rules/project-structure.mdc`.
- Per-file inventories: `.cursor/rules/project-structure-details.mdc` on demand.
- Documentation decision table and sync policy: `.cursor/rules/docs-sync.mdc`.
- Packets: `.cursor/rules/network-packets.mdc`.
- AI assistant: `.cursor/rules/ai-assistant.mdc` and the AI docs-sync rule.
- WebAE frontend, icon performance, diagnostics, and build: the matching
  `.cursor/rules/webae-*.mdc` file.
- GTNH lifecycle/config context: `.cursor/rules/gtnh-mod-context.mdc`.
- GUI changes: `.cursor/rules/gui-guidelines.mdc`.
- Temporary artifacts and generated files: `.cursor/rules/workspace-artifacts.mdc`.
- Repeatable workflows: `.cursor/skills/`; follow the relevant `SKILL.md` when
  its task description matches.

Do not treat `.cursor/mcp.json` as a Codex configuration file. If its checks
are needed, run the repository scripts directly or use an already configured
Codex MCP server.

## Verification

Run the smallest relevant checks, and report failures with their cause:

```powershell
python tools/doc-check/doc-consistency-check.py
```

- Java behavior: `./gradlew.bat test` when feasible.
- TypeScript/React changes: from `webae-frontend/`, run `npm.cmd test -- --run`
  and `npm.cmd exec tsc -- --noEmit` when applicable.
- JSON/manual changes: parse the changed JSON and run the documentation check.
- Do not run a clean/build step that deletes generated WebAE assets unless the
  task or the relevant build workflow explicitly requires it.

Before finishing a behavior-changing task, inspect the final diff and confirm
that code, docs, manual, language keys, and rules are either synchronized or
explicitly noted as not applicable.

The GitHub gate is `.github/workflows/doc-check-and-webae.yml`. It runs the
documentation checker for every pull request and for pushes to `main` or
`master`. Repository administrators should mark
`Doc check and WebAE frontend / doc-check` as a required status check on the
protected default branch; this setting cannot be enforced by a repository
file alone.
