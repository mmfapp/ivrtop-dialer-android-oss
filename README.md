# IVRTOP Dialer – חייגן השיחות של אהבת ישראל (Android)

Source code of the Android SIP dialer app, published under **GPL-3.0-or-later** – see [LICENSE](LICENSE) and [NOTICE.md](NOTICE.md).

- Privacy policy: https://ivr.top/dialer/android/privacy
- Terms of use: https://ivr.top/dialer/android/terms

## Build
The Gradle project is in [`android/`](android/). The PJSIP (pjsua2) native libraries and Java bindings are generated artefacts and are not committed – see [`android/telephony/sip-engine/pjsua2/README.md`](android/telephony/sip-engine/pjsua2/README.md).

Release signing reads `IVRTOP_KEYSTORE_PASSWORD` / `IVRTOP_KEY_PASSWORD` from the environment; no keys are stored here.

## Brand assets
The names "אהבת ישראל" and "IVRTOP", the logo and the icons are **not** covered by the GPL. Forks must replace them and must not use the package name `com.ivrtop.dialer` – see [NOTICE.md](NOTICE.md).
