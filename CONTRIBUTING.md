# Contributing to TeXTech

Thank you for helping TeXTech. Small, reviewable changes with reproducible
evidence are especially valuable in a Minecraft 1.7.10 / GTNH codebase.

## Choose the right channel

- Reproducible defects and concrete feature requests belong in
  [Issues](https://github.com/ImgoodWK/TeXTech-GTNH/issues).
- Questions, ideas, and showcases belong in
  [Discussions](https://github.com/ImgoodWK/TeXTech-GTNH/discussions).
- Vulnerabilities and leaked credentials must follow [`SECURITY.md`](SECURITY.md),
  never a public Issue.

Before opening a PR, read the
[documentation map](docs/en/developer/documentation-map.md), the relevant
`.cursor/rules/` contract, and the issue/PR history. Keep runtime `TeXTech/`
data, world saves, logs, `.workspace/`, build output, tokens, API keys, bot
secrets, player UUIDs, and private network details out of Git.

## Development workflow

Create a focused branch from `master`. Do not mix feature behavior, generated
assets, broad formatting, or unrelated cleanup in one commit. Public behavior
changes must synchronize the matching source, tests, Chinese and English
documentation, in-game manual, language keys, machine-readable data, and
agent rules when applicable.

Run the checks that match your change. The full release gate is:

```powershell
python tools/doc-check/doc-consistency-check.py
python tools/release/validate_repository.py
python tools/release/scan_secrets.py
python -m unittest discover -s integrations/astrbot/tests -p "test_*.py"

Push-Location webae-frontend
npm.cmd ci
npm.cmd test -- --run
npm.cmd exec tsc -- --noEmit
npm.cmd run build
Pop-Location

.\gradlew.bat spotlessCheck
.\gradlew.bat test
.\gradlew.bat build
git diff --check
```

Official Maven/Gradle sources are the default. Developers who need domestic
mirrors may add `-Ptextech.useChinaMirrors=true`; CI and releases do not use
local Maven state or mirror-only resolution.

## Pull requests

Use the PR template and state:

1. the user-visible result and why it is needed;
2. compatibility, migration, network, persistence, and security impact;
3. synchronized docs/manual/language/rule files;
4. exact commands run and their results;
5. sanitized screenshots for visible UI changes;
6. limitations and deliberately deferred work.

The protected branch requires a PR, resolved conversations, passing checks,
and signed commits. Maintainers may ask for a smaller patch, clearer tests, or
separate mechanical formatting.

By contributing, you agree that your contribution may be distributed under
the repository's MIT License and that you have the right to submit it.
