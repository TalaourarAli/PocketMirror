# PocketMirror

**English** · [Français](README.fr.md)

A pocket mirror on your Android lock screen: a small floating button opens the
front camera as an overlay, without unlocking the phone.

> Co-developed with OpenCode (Gemini + DeepSeek).

![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84)
![Kotlin](https://img.shields.io/badge/Kotlin-1.9.24-7F52FF)
![License](https://img.shields.io/badge/License-MIT-blue)

## Download

No need to compile: a ready-to-install APK is published on the
**[Releases](https://github.com/TalaourarAli/PocketMirror/releases/latest)** page.

1. Download the `.apk` file from the latest release, on your phone.
2. Allow installing from that source if Android asks (browser or file manager →
   “Install unknown apps”).
3. Open the file and install.

The APK is signed, so updates install **on top of** the previous version and
your settings are kept. The only requirement is always using the same signing
key (see [Release signing](#release-signing)).

**Requirements**: Android 8.0 (API 26) or newer, with a front camera.

### First run

1. Open **PocketMirror** and grant the **Camera** permission.
2. Tap **Service Lockscreen** and enable
   “PocketMirror (Écran verrouillé)” in the accessibility settings.
3. Lock the screen: the mirror button appears.

The “Show while unlocked” option lets you test the button without locking the
screen.

## Features

- **Instant mirror**: the front camera opens over the lock screen, full frame,
  without leaving the locked session.
- **Discreet, customisable button**: free position (drag and drop), 36–84 dp
  size, 20–100 % opacity.
- **Two opening modes**:
  - *Hold*: the mirror stays open while your finger is down;
  - *Toggle*: one tap opens, the next one closes.
- **Three window shapes**: circle glued to the camera cutout, rounded square,
  tall oval.
- **Four frames**: scalloped blue (pocket mirror), smooth blue, gold, or none.
  Scallops only apply to the circle shape.
- **Fine tuning**: horizontal and vertical offset of the popup from the camera
  cutout, popup size from 180 to 380 dp.
- **Screen kept on** while the mirror is open (`FLAG_KEEP_SCREEN_ON`, no
  wakelock and no extra permission).
- **No network access at all**: the app does not even declare the `INTERNET`
  permission. Camera images never leave the device.

## How it works

The app relies on an **accessibility service** (`AccessibilityService`), the
only way for an Android app to draw a window above the lock screen. The service:

1. displays the floating button (`TYPE_ACCESSIBILITY_OVERLAY`);
2. creates a second overlay window, containing a CameraX `PreviewView`, when the
   mirror opens;
3. clips the preview to the chosen shape and paints the frame around it
   (`MaskedMirrorContainer`), with a “growing out of the camera cutout” animation;
4. releases the camera and removes the window on close.

The service pre-warms `ProcessCameraProvider` so that opening is immediate.
Settings travel through `SharedPreferences` and an internal broadcast
(`ACTION_PREFS_CHANGED`), so changes apply to the mirror even while it is open.

## What the accessibility service does — and does not do

An accessibility service is powerful by nature, which is why Android shows a
warning. Here is its exact scope in PocketMirror, verifiable in the code and in
`app/src/main/res/xml/accessibility_service_config.xml`:

| It does **not** | Proof |
|---|---|
| Read window content | `android:canRetrieveWindowContent="false"` |
| Read on-screen text, input fields or passwords | no use of `event.source` or `getNodeInfo()` |
| Receive key events | `requestFilterKeyEvents=false` |
| Capture user gestures | `captureFingerprintGestures=false`, no `onGesture` |
| Enable touch exploration | `requestTouchExplorationMode=false` |
| Access the network | no `INTERNET` permission in the manifest |

The only event it subscribes to is `TYPE_WINDOW_STATE_CHANGED` (lock/unlock). It
is used purely as a refresh signal: the service asks `KeyguardManager` whether
the screen is locked, and never inspects the notified window.

In other words, the service does not “see” the screen: it is granted the right
to draw a window above the lock screen, and nothing more.

### Disable or uninstall cleanly

**Disable the service** (the app stays installed, the button disappears
immediately):
Settings → **Accessibility** → **PocketMirror (Écran verrouillé)** → turn off.

**Uninstall**:

1. disable the service as above;
2. then Settings → **Apps** → **PocketMirror** → **Uninstall** (or long-press
   the icon → Uninstall).

Android revokes the accessibility permission on uninstall anyway; disabling
first simply avoids leaving an orphan entry in the services list. Preferences
live in the app's private storage and are removed with it — nothing is kept
elsewhere.

## Settings

| Setting | Values |
|---|---|
| Opening mode | Hold / Toggle |
| Popup shape | Circle / Rounded square / Tall oval |
| Frame | Scalloped blue / Smooth blue / Gold / None |
| Popup height | −40 to +200 dp from the camera cutout |
| Horizontal offset | −120 to +120 dp |
| Popup size | 180 to 380 dp |
| Button size | 36 to 84 dp |
| Button opacity | 20 to 100 % |

## Build from source

Requirements: **JDK 17** and **Android SDK 36** (Android Studio, or the command
line only).

```bash
git clone https://github.com/TalaourarAli/PocketMirror.git
cd PocketMirror
./gradlew :app:assembleDebug          # debug APK, installable as-is
```

Windows:

```powershell
.\gradlew.bat :app:assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`, installable with:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

> Gradle must find the Android SDK: either through the `ANDROID_HOME`
> environment variable, or through `local.properties`
> (`sdk.dir=/path/to/Android/Sdk`). That file is not committed.

## Release signing

This section only matters if you publish builds for other people. For personal
use, the debug APK is enough.

`keystore.properties` is **not** committed (it holds the passwords). Without it,
`assembleRelease` produces an **unsigned** APK and Gradle prints a warning. To
sign locally, create the file at the repository root:

```properties
storeFile=path/to/pocketmirror.jks
storePassword=...
keyAlias=pocketmirror
keyPassword=...
```

Generate a keystore if you do not have one yet:

```bash
keytool -genkeypair -v -keystore pocketmirror.jks -alias pocketmirror \
        -keyalg RSA -keysize 2048 -validity 10000
```

> ⚠️ **Back this file up, along with its passwords.** It is the only
> irreplaceable part of the project: a lost or changed key forces users to
> uninstall the app (and lose their settings) before they can install an update.

## CI and releases

| Workflow | Trigger | What it does |
|---|---|---|
| `.github/workflows/android.yml` | push to `main`, pull request, manual | `assembleDebug` + `lintDebug`, debug APK as an artifact. **Publishes nothing.** |
| `.github/workflows/release.yml` | **manual only** | Builds the release APK, signs it if the secrets exist, and creates a GitHub Release with generated notes and the APK attached |

Pushing code publishes nothing, and pushing a tag publishes nothing either:
that is intentional. To publish, pick one of two mutually exclusive paths.

### Option A — local signing (default)

The keystore stays on your machine and is never sent to GitHub.

1. Update `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Commit, then tag with the same number:

   ```bash
   git tag -a v1.8 -m "Version 1.8 (versionCode 8)"
   git push origin main
   git push origin v1.8
   ```

3. Build the signed APK locally:

   ```bash
   ./gradlew :app:assembleRelease
   ```

4. On GitHub: **Releases → Draft a new release**, pick the `v1.8` tag, then
   attach `app/build/outputs/apk/release/app-release.apk` (renamed
   `PocketMirror-v1.8.apk` if you want to match the naming used by the
   workflow).

### Option B — signing in GitHub Actions (optional)

The `Release` workflow can sign and publish by itself, started manually from
**Actions → Release → Run workflow**. In that case the keystore is stored in the
repository secrets. Add them in **Settings → Secrets and variables → Actions**:

| Secret | Content |
|---|---|
| `KEYSTORE_BASE64` | the keystore encoded in base64 (see below) |
| `KEYSTORE_PASSWORD` | keystore password (`storePassword`) |
| `KEY_ALIAS` | key alias (`keyAlias`) |
| `KEY_PASSWORD` | key password (`keyPassword`) |

Encoding the keystore:

```bash
base64 -w0 pocketmirror.jks > keystore.b64   # Linux
base64 -i pocketmirror.jks -o keystore.b64   # macOS
```

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("pocketmirror.jks")) | Set-Content keystore.b64
```

> 🧹 **Delete `keystore.b64` right after pasting it into GitHub Secrets.** That
> file holds your private key encoded, not encrypted: it must never be committed
> or left in the project folder. `.gitignore` now ignores `*.b64`, but do not
> rely on it alone.

> ⚠️ The keystore must be **the one used for the versions already installed**.
> An APK signed with a different key cannot update the app: users would have to
> uninstall, which erases their settings.

Without the secrets, option B produces an **unsigned** APK, which Android
refuses to install over an existing version. In that case, use option A.

## Project structure

```
app/src/main/java/com/pocketmirror/app/
├── MainActivity.kt                         # settings screen
├── ButtonPositionActivity.kt               # drag-and-drop button placement
├── LockScreenMirrorAccessibilityService.kt # overlays, camera, animations
├── MaskedMirrorContainer.kt                # clipping + drawn frame
├── MirrorPrefs.kt                          # SharedPreferences + broadcast
└── MirrorStyle.kt                          # InteractionMode, MirrorShape, MirrorFrame
```

## Privacy

The app has no network permission and sends no data. The camera is used only for
the local preview while the mirror is open. Settings are stored in the app's
private storage.

No analytics, no crash reporting, no ad libraries.

## Known limitations

- The accessibility service is mandatory: public Android APIs cannot draw over
  the lock screen.
- In toggle mode the mirror can stay open indefinitely, and the screen stays on
  while it is visible.
- **16 KB memory pages (Android 16 and newer)**: Android can allocate memory in
  16 KB blocks instead of 4 KB, which breaks native libraries built for the old
  size. CameraX ships two such small libraries; they are **excluded** from the
  build to avoid the startup warning. Practical consequence: a few optional
  image optimisations are disabled, and the mirror preview works normally.

## License

MIT — see [LICENSE](LICENSE).
