# TeXTech notices and provenance

TeXTech (铽丝科技) is the current name of the Minecraft 1.7.10 / GTNH
project that began as **AdvanceDataMonitor**. The canonical repository is:

<https://github.com/ImgoodWK/TeXTech-GTNH>

The current Mod ID is `textech`. Historical release names, configuration
paths, migration code, or Java symbols may retain `AdvanceDataMonitor` or
`advancedatamonitor` where changing them would break compatibility.

## Project authorship

Copyright (c) 2025-2026 ImgoodWK. TeXTech's original gameplay, integration,
documentation, and brand work is distributed under the repository's MIT
License. See [`AUTHORS.md`](AUTHORS.md) for the maintained authorship record.

The public chronology is anchored by the repository's oldest reachable
commit, `e04bde7` (2025-04-29), immutable tags, release checksums and
attestations where available, and this human-readable provenance identifier:

`TT-GTNH-PROVENANCE-2025-04-29-E04BDE7`

The bilingual project timeline deliberately separates Git-verifiable facts
from the author's recollection:

- [`docs/zh/project/timeline.md`](docs/zh/project/timeline.md)
- [`docs/en/project/timeline.md`](docs/en/project/timeline.md)

## Retained build-template material

TeXTech retains and updates parts of the
[GTNewHorizons/ExampleMod1.7.10](https://github.com/GTNewHorizons/ExampleMod1.7.10)
build skeleton, including the Gradle Wrapper, GTNH convention configuration,
and files under `gtnhShared/`. The initial repository import also contained
the template README and publication workflows; those player-irrelevant
starter/migration workflows are no longer used by TeXTech.

The imported template carried this MIT copyright notice:

> Copyright (c) 2021 Johann Bernhardt

That attribution applies only to retained ExampleMod build scaffolding and
does not identify Johann Bernhardt as an author or copyright holder of
TeXTech's mod implementation, documentation, or branding. The template also
credits SinTh0r4s, TheElan, and basdxz as template authors.

## Components included in release artifacts

The main or optional TeXTech artifacts include or shade the following
third-party components. Their original licenses and copyright notices remain
applicable to those components.

| Component | Release scope | License / upstream |
| --- | --- | --- |
| NanoHTTPD 2.3.1 | Main JAR WebAE HTTP server | [BSD 3-Clause](https://github.com/NanoHttpd/nanohttpd/blob/2.3.1/LICENSE.md) |
| Java-WebSocket 1.5.7 | Main JAR WebAE socket transport | [MIT](https://github.com/TooTallNate/Java-WebSocket/blob/v1.5.7/LICENSE) |
| Vosk API 0.3.45 | Optional voice integration classes/native bridge | [Apache-2.0](https://github.com/alphacep/vosk-api/blob/v0.3.45/COPYING) |
| Java Native Access (JNA) 5.14.0 | Optional voice native bridge | [Apache-2.0 OR LGPL-2.1-or-later](https://github.com/java-native-access/jna/blob/5.14.0/LICENSE); TeXTech uses the Apache-2.0 option |
| PinIn 1.6.0 | Main JAR Chinese text matching | [MIT](https://github.com/Towdium/PinIn/blob/master/LICENSE) |
| vosk-model-small-cn-0.22 | Optional voice JAR speech model | [Apache-2.0](https://alphacephei.com/vosk/models) |

Minecraft, Minecraft Forge, GregTech: New Horizons, Applied Energistics 2,
and other referenced project names and marks belong to their respective
owners. Compatibility references do not imply endorsement.

## Transparent provenance, not covert tracking

This public repository cannot identify who reads, clones, or submits its
contents to an AI system. TeXTech does not use hidden prompt injection,
tracking pixels, covert callbacks, malicious code, or unconsented telemetry
to try to identify readers. The public provenance monitor searches only
GitHub's public index for disclosed phrases and identifiers; a match is an
investigation lead, not proof of infringement.

The MIT License permits use, modification, and distribution when its notice
requirements are followed. Provenance records establish chronology and help
find missing attribution; they do not revoke permissions granted by MIT.
