# My Launcher — External Provider Integration

## Direction

My Launcher remains the Android launcher and user interface. It consumes established third-party services rather than introducing a new MY SERVICES backend.

Cubiq Launcher is the primary reference implementation for Android launcher capabilities. It is an application/codebase, not a hosted API provider, so My Launcher must integrate its compatible components selectively instead of sending requests to a fictional "Cubiq API".

Reference:
- Cubiq Launcher: https://github.com/VivxKumar07/Cubiq-Launcher
- Cubiq website: https://cubiq.antideploy.app/
- Cubiq license: GNU GPL-3.0
- Cubiq releases: https://github.com/VivxKumar07/Cubiq-Launcher/releases

## Provider map

| Capability | Primary source | Additional source | Integration rule |
|---|---|---|---|
| Mod and modpack discovery | Modrinth | CurseForge | Query independently; keep results from a healthy source when another source is unavailable. |
| Modpack formats | Modrinth `.mrpack` | CurseForge manifest | Detect format from archive contents; validate manifests before installing. |
| Minecraft version metadata and files | Mojang version manifest | Trusted mirrors only where explicitly configured | Verify downloaded files against official hashes when available. |
| Microsoft account | Microsoft/Xbox/Minecraft Services | None | Use official OAuth/device-code flow; never collect Microsoft passwords. |
| Ely.by account | Ely.by authentication service | Authlib Injector-compatible servers where supported | Use the actual Yggdrasil/Authlib flow; never substitute a website link for in-app authentication. |
| Mod loaders | Fabric, Forge, NeoForge, Quilt project metadata/installers | Existing launcher-compatible installers | Keep loader installers isolated by loader and Minecraft version. |
| Java runtimes | Eclipse Temurin / Azul Zulu / BellSoft Liberica distributions | Existing compatible runtime packages | Match runtime major version and device ABI; verify checksums. |
| Skins and capes | Official Minecraft profile services | Ely.by where account type supports it; local PNG import | Keep account ownership and skin source visible. |
| Graphics/renderers | Existing compatible renderer projects | Cubiq's documented renderer choices as a reference | Integrate only Android/ABI-compatible binaries with their original licenses and checksums. |

## Current implementation status

- [x] Modrinth search adapter exists in the project.
- [x] CurseForge search adapter exists in the project.
- [x] Combined search uses both catalogs.
- [x] A failed catalog request no longer discards successful results from the other catalog.
- [ ] Cubiq-derived components have not yet been ported into this repository.
- [ ] Ely.by in-app game authentication is not yet implemented; the current button opens the Ely.by website.
- [ ] Additional runtime mirrors and renderer packages are not yet integrated.
- [ ] A full redesigned launcher UI is not yet complete.

## Integration and reliability rules

1. Do not treat Cubiq Launcher itself as a network API. Reuse or adapt code only after checking its source, dependencies, license and compatibility with this Android project.
2. Preserve upstream copyright and license notices. Cubiq is GPL-3.0; any copied or adapted covered code must comply with GPL-3.0 and retain required notices. Do not copy branding, private keys, credentials or hosted services.
3. Each external provider must have an isolated adapter and a clear source identifier.
4. A provider outage must not crash the launcher or erase results already returned by other providers.
5. Validate archive paths, manifests, checksums, file sizes and Minecraft/loader compatibility before installation.
6. Do not store provider secrets in the Android client. Do not log OAuth tokens, passwords or authorization headers.
7. Do not mark a feature complete until its real flow is implemented and tested. CI success alone is not proof of successful use on a physical Android device.

## Next implementation order

1. Stabilize the existing Modrinth + CurseForge catalog and pagination.
2. Port compatible Cubiq improvements for download queue, installation management and renderer selection after comparing the actual source implementations.
3. Implement Ely.by authentication using its supported Authlib/Yggdrasil protocol.
4. Expand runtime and loader sources with checksum and compatibility validation.
5. Rebuild the launcher UI around these real capabilities; remove controls that do not have a working action.
