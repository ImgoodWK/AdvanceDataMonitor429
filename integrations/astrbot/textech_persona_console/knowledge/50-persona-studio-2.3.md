# Persona Studio 2.3

- Console 2.3 adds a web-only preview target (`preview:local`) for persona answers that are never sent to QQ.
- The message center exposes only hashed persona keys, display names, and bounded tags. Private personas and raw Persona Lib store keys are excluded.
- Console Bridge 1.2.0 accepts the preview target only for a draft with an empty UMO. It follows AstrBot's configured fallback chat models for draft generation only. A real QQ send still requires a known, allowed target and the fixed `SEND` confirmation, with no automatic resend.
