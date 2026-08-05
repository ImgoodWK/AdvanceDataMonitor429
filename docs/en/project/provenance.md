# TeXTech public provenance and evidence guide

> Canonical source: repository `docs/` · Last synced: 2026-08

TeXTech records chronology through visible, verifiable mechanisms that do not interfere with normal reading or operation. Its provenance ID is:

`TT-GTNH-PROVENANCE-2025-04-29-E04BDE7`

Together with the oldest reachable commit `e04bde7` (2025-04-29), signed commits, signed annotated tags, release commit SHAs, `SHA256SUMS`, and GitHub Artifact Attestations, it forms a public verification chain.

## How the public monitor works

`.github/workflows/provenance-monitor.yml` runs every Monday at 01:00 UTC and supports manual dispatch. Queries, the official repository, and the allowlist are public in `.github/provenance-monitor.json`. The script requests only GitHub's public code index and independently discards every result marked private or non-public.

Queries cover the provenance ID, both brand slogans, and a small number of distinctive symbol combinations. An unallowed public result produces Markdown and JSON artifacts and a failed workflow status that notifies the maintainer. The workflow never files an Issue, contacts or accuses a repository owner, reads private repositories, or collects identity data such as email addresses or tokens.

## Inherent limitations

- GitHub's public index can be delayed, incomplete, or temporarily unavailable.
- Private repositories and material outside the public index are invisible.
- Renaming, translation, human rewriting, or AI transformation usually defeats literal matching.
- A search match is a lead for human review, not proof of copying or infringement.
- MIT permits copying, modification, and distribution when its notice requirements are followed; ordinary licensed derivative development must not be described as unlawful by default.

## Manual evidence checklist

When a suspicious public result appears, preserve facts before drawing conclusions:

1. Record full public repository, file, and page URLs with access date and time zone.
2. Record the file commit SHA, its parent, and the default branch.
3. Preserve visible commit, tag, and release timestamps.
4. Save dated page snapshots, original files, and download hashes.
5. Produce a focused, reproducible, side-by-side diff of concrete code, prose, or structure.
6. Preserve the licenses, copyright notices, and NOTICE files displayed at that time.
7. Compare against TeXTech's earliest commit, signed tags, release commit, checksums, and attestations.
8. Separate shared dependencies, generic solutions, and compatibility-required structure from genuinely distinctive expression; state only verifiable facts.
9. Let the maintainer decide whether to allowlist, monitor, or seek platform/legal guidance.

This process is not legal advice and does not authorize covert tracking, attacks, callbacks, or collection of personal information.
