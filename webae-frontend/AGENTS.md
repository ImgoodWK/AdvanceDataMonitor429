# WebAE frontend agent guidance

The repository root `AGENTS.md` applies here. For frontend work, also read
`.cursor/rules/webae-frontend.mdc` and, when relevant,
`.cursor/rules/webae-icon-performance.mdc`,
`.cursor/rules/webae-perf-diagnostics.mdc`, and
`.cursor/rules/webae-frontend-build.mdc`.

- Preserve the existing dashboard data-source and `columns` contracts.
- Keep Chinese and English i18n keys aligned for user-visible changes.
- Add or update focused Vitest coverage for changed utility or state logic.
- Run `npm.cmd test -- --run` and `npm.cmd exec tsc -- --noEmit` for relevant
  changes. Run the root documentation check when API, behavior, manual, or
  user documentation is affected.
- Do not delete or regenerate `src/main/resources/assets/textech/webae/`
  assets except through the requested build workflow.
