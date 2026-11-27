# רישוי · Licensing

## Path: open-source app under GPL-3.0-or-later, no proprietary libraries
PJSIP is dual-licensed **GPLv2-or-later / commercial (Teluu)**. The app is published as open source instead of buying a commercial licence:

- App source (this repository): **GPL-3.0-or-later** – `LICENSE`, `NOTICE.md`.
- Why v3: Apache-2.0 components (AndroidX, OpenSSL 3, …) are compatible with GPLv3 but not GPLv2; PJSIP's "or later" clause allows GPLv3.
- **No proprietary libraries** are linked (removed 2026-11-26: Firebase Messaging, Play services code scanner, Play Install Referrer). This resolves the GPL ⇄ proprietary-library question – every component in the APK is GPL-compatible.
- No server is required by the app (setup links are parsed locally; calls via persistent SIP connection).
- Brand assets: **not** licensed under the GPL (`NOTICE.md`).

## Obligations when publishing builds (Google Play / APK)
1. Every distributed build must correspond to public source: tag each release (`v1.2.3`) and keep `scripts/pjsip/` (PJSIP/OpenSSL/Opus are downloaded at pinned versions – pin checksums before release).
2. Settings › Support › Open-source licences: state GPLv3 and link to the source repository.
3. Store listing / About: link to the source repository.
4. Keep third-party copyright and licence notices intact.
5. Signing keys are not source and must never be published.

## iOS caveat

## Component licences
| Component | Licence | Use |
|---|---|---|
| PJSIP / PJSUA2 2.16 | GPLv2-or-later (used under GPLv3) | SIP/RTP engine |
| OpenSSL 3.0 | Apache-2.0 | TLS / SRTP crypto |
| Opus 1.5 | BSD-3-Clause | codec |
| WebRTC AEC (PJSIP third_party) | BSD-3-Clause | echo cancellation |
| AndroidX, Compose, Material, Hilt/Dagger, Room, WorkManager, Navigation, Core-Telecom | Apache-2.0 | app |
| Kotlin, kotlinx.coroutines, kotlinx.serialization | Apache-2.0 | app |
| libphonenumber | Apache-2.0 | numbers |
| minidns | Apache-2.0 (dual) | DNS SRV |
| ZXing + zxing-android-embedded | Apache-2.0 | QR scanning |
| Coil 3, sh.calvin.reorderable, Timber | Apache-2.0 | app |
| Rubik font | SIL OFL 1.1 | typography (`RUBIK_OFL.txt`) |
| אהבת ישראל logo & brand | © all rights reserved | branding (NOTICE.md) |

Excluded on purpose: G.729 / bcg729, video codecs, any Google Play services / Firebase library.
