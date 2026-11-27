# NOTICE

**IVRTOP Dialer – חייגן השיחות של אהבת ישראל**
Copyright © 2026 אהבת ישראל – פתרונות תקשורת ואוטומציות (https://ivr.top)

## Source code
The source code in this repository is licensed under the **GNU General Public License v3.0 or later** (`GPL-3.0-or-later`) – see [LICENSE](LICENSE).

GPLv3 (rather than v2) is required because the app links PJSIP (licensed *GPLv2 or later*) together with Apache-2.0 components (AndroidX, OpenSSL 3, OkHttp, …), and Apache-2.0 is compatible with GPLv3 only.

Third-party components keep their own licences – see [THIRD-PARTY-LICENSES.md](THIRD-PARTY-LICENSES.md).

## Trademarks & brand assets – NOT covered by the GPL
The name **"אהבת ישראל"**, the **IVRTOP** name, the logo, the sparkle mark and every file derived from them – including:

- `android/core/designsystem/src/main/res/drawable-nodpi/brand_logo_full.png`
- `android/app/src/main/res/mipmap-*/ic_launcher_*.png`, `android/app/src/main/res/drawable-*/splash_mark.png`
- `android/telephony/telecom/src/main/res/drawable-*/ic_stat_ivrtop.png`

are © אהבת ישראל, **all rights reserved**. They are included only so that the official app can be built from this repository. **Forks and modified versions must remove or replace all of them, and must not use the names "אהבת ישראל", "IVRTOP" or the package name `com.ivrtop.dialer`.**

## Fonts
Rubik – SIL Open Font License 1.1 (`RUBIK_OFL.txt`).

## No server, no proprietary SDKs
The app needs no backend: setup links are parsed on the device and calls go directly to the SIP server. No Google Play services / Firebase or other proprietary libraries are linked.
