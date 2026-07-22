# Place Meowa-generated card faces here as `<cardId>.png`.
# See `.cursor/skills/textech-card-art/SKILL.md`.
# Pilot (gt + thaum, 20 cards) generated via Meowa `image-2-run` (1K 1:1).
# Production jar also serves copies from `assets/textech/cardbattle/card-art/`.
# Run `npm run art:requirements` from `cardbattle-server/` to create one Meowa
# requirement per missing card under ignored `.workspace/card-art/`.
# The exporter never reads or writes MEOWART_API_KEY and never calls the network.
# With MEOWART_API_KEY configured locally, run `npm run art:generate -- --limit 5`
# to generate a resumable small batch. Successful 1024x1024 PNGs are validated
# against Meowa `final_outputs.json` before promotion; interrupted jobs are kept
# in `.workspace/card-art/meowa-state.json` and are never resubmitted implicitly.
# Keep the default serial execution for reliable downloads. A submission that
# failed before receiving a job id can be retried explicitly with
# `--ids <cardId> --retry-unsubmitted`; accepted jobs must be recovered instead.
# Run `npm run art:recover` only after the generator has stopped to retrieve
# completed-but-undownloaded media by saved job id without submitting new jobs.
