# Security policy

## Supported versions

| Version | Status |
| --- | --- |
| `v3.0.0-rc.1` | Supported pre-release; compatibility testing in progress |
| `v2.0.0` | Supported stable line during the RC observation period |
| Older releases | Best effort only |

## Report a vulnerability privately

Do not publish exploit details, credentials, private server data, or a working
attack in an Issue or Discussion. Use GitHub
[private vulnerability reporting](https://github.com/ImgoodWK/TeXTech-GTNH/security/advisories/new).
If that form is unavailable, contact
[@ImgoodWK](https://github.com/ImgoodWK) and request a private channel without
including sensitive details in the first public message.

Include, when safe:

- affected release or exact commit SHA;
- Minecraft, Forge, GTNH, Java, and deployment versions;
- minimal reproduction, impact, and expected/actual behavior;
- whether WebAE, AI, voice, QQ/AstrBot, MCEF, or map integrations are enabled;
- sanitized logs or a private proof of concept;
- your preferred credit and disclosure coordination.

The maintainer will validate scope, coordinate a fix and release, and discuss
disclosure timing. Please allow a reasonable remediation window before public
disclosure.

## Operator hardening

WebAE tokens, AI provider keys, voice credentials, QQ bot secrets, and persona
console credentials must remain in local configuration or environment
variables. Never commit `.env`, generated token stores, runtime `TeXTech/`
data, private addresses, full world saves, or unsanitized server logs.

WebAE is disabled by default. Bind it to loopback or a trusted private
interface, use a reverse proxy with transport security when remote access is
required, issue the minimum token scope, and rotate a credential immediately
after suspected disclosure. See the
[WebAE operator guide](docs/en/webae/user-guide.md).

## Supply-chain verification

Releases produced by the current release workflow provide `SHA256SUMS`, the
exact release commit, and GitHub artifact attestations. A signed annotated Git
tag is included when release signing is configured; verify the tag signature
when GitHub marks it as verified. Verify an asset with:

```bash
sha256sum --check SHA256SUMS
gh attestation verify textech-v3.0.0-rc.1.jar --repo ImgoodWK/TeXTech-GTNH
```

The public provenance monitor described in
[`docs/en/project/provenance.md`](docs/en/project/provenance.md) is an
attribution aid, not security telemetry and not a vulnerability-reporting
channel.
