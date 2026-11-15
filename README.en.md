# PocketMirror

[Français](README.md) · **English**

A pocket mirror on your Android lock screen: a small floating button opens the
front camera as an overlay, without unlocking the phone.

> Co-developed with OpenCode (Gemini + DeepSeek).

![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84)
![Kotlin](https://img.shields.io/badge/Kotlin-1.9.24-7F52FF)
![License](https://img.shields.io/badge/License-MIT-blue)

## Download

A ready-to-install APK is published on the
**[Releases](https://github.com/TalaourarAli/PocketMirror/releases/latest)** page.

1. Download the `.apk` file from the latest release on your phone.
2. Allow installing from that source when Android asks.
3. Open the file and install.

The APK is signed, so updates install **on top of** the previous version and
your settings are kept. Requires Android 8.0 (API 26) or newer, with a front
camera.

### First run

1. Open **PocketMirror** and grant the **Camera** permission.
2. Tap **Service Lockscreen** and enable
   “PocketMirror (Écran verrouillé)” in the accessibility settings.
3. Lock the screen: the mirror button appears.

## Features

- Instant front camera overlay on the lock screen, no unlock needed.
- Discreet, movable button: 36–84 dp, 20–100 % opacity, free position.
- Two modes: hold to show, or tap to toggle.
- Three window shapes: circle, rounded square, tall oval.
- Four frames: scalloped blue, smooth blue, gold, or none.
- Fine tuning of the popup size and of its offset from the camera cutout.
- Keeps the screen on while the mirror is open.
- **No network access at all**: the app does not even declare the `INTERNET`
  permission.

## About the accessibility service

Android only allows an app to draw above the lock screen through an
accessibility service, which is why one is required. Its scope is deliberately
minimal — all of it verifiable in
`app/src/main/res/xml/accessibility_service_config.xml`:

| It does **not** | Proof |
|---|---|
| Read window content | `android:canRetrieveWindowContent="false"` |
| Read on-screen text, input fields or passwords | no use of `event.source` or `getNodeInfo()` |
| Receive key events | `requestFilterKeyEvents=false` |
| Capture gestures | `captureFingerprintGestures=false` |
| Enable touch exploration | `requestTouchExplorationMode=false` |
| Access the network | no `INTERNET` permission in the manifest |

The only event it subscribes to is `TYPE_WINDOW_STATE_CHANGED` (lock/unlock),
used purely as a refresh signal; the lock state itself comes from
`KeyguardManager`. It never inspects the notified window.

### Disable or uninstall

- **Disable**: Settings → Accessibility → **PocketMirror** → turn off. The
  button disappears immediately.
- **Uninstall**: disable the service first, then Settings → Apps →
  **PocketMirror** → Uninstall. Preferences live in the app's private storage
  and are removed with it.

## Build from source

Requires **JDK 17** and **Android SDK 36**.

```bash
git clone https://github.com/TalaourarAli/PocketMirror.git
cd PocketMirror
./gradlew :app:assembleDebug
```

On Windows: `.\gradlew.bat :app:assembleDebug`.

The APK lands in `app/build/outputs/apk/debug/app-debug.apk`
(`adb install -r <path>`).

## Release signing

`keystore.properties` is not committed. Create it at the repository root:

```properties
storeFile=path/to/pocketmirror.jks
storePassword=...
keyAlias=pocketmirror
keyPassword=...
```

There are two publishing paths — pick one:

- **A. Sign locally (default)**: build with `./gradlew :app:assembleRelease`,
  create the tag, then attach the APK to a GitHub Release by hand. The keystore
  never leaves your machine.
- **B. Sign in GitHub Actions (optional)**: add the `KEYSTORE_BASE64`,
  `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD` repository secrets, then
  run the `Release` workflow manually. Delete the base64 file right after
  pasting it into GitHub.

Either way, the key must stay the same across versions: Android refuses an
update signed with a different key, and users would have to uninstall (losing
their settings).

## Privacy

No network permission, no analytics, no crash reporting, no ads. The camera is
used only for the local preview while the mirror is open. Settings are stored
in the app's private storage.

## Known limitations

- The accessibility service is mandatory: public Android APIs cannot draw over
  the lock screen.
- In toggle mode the mirror can stay open indefinitely, and the screen stays on
  while it is visible.
- **16 KB memory pages (Android 16+)**: two optional CameraX native libraries
  are prebuilt for 4 KB pages, so they are excluded from the build to avoid the
  startup warning. A few optional image optimizations are therefore disabled;
  the preview works normally.

## License

MIT — see [LICENSE](LICENSE).
