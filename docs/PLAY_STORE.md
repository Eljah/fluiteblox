# Google Play publish checklist for Fluiteblox

Current app identity:

- Package name: `tatar.eljah.fluitblox`
- Play Console app id: `4972771354641032915`
- Play Console dashboard: `https://play.google.com/console/u/1/developers/4849820391120439995/app/4972771354641032915/app-dashboard`
- Version: `versionCode=1`, `versionName=1.0.0`
- Min SDK: 21
- Target SDK: 36
- Artifact for new Play apps: `target/app-release.aab`
- Release signing key: `keystore/release-key.jks`

## Internal testing

Internal testing is configured in Play Console:

- Track id: `4701695168286427488`
- Internal testing page: `https://play.google.com/console/u/1/developers/4849820391120439995/app/4972771354641032915/tracks/4701695168286427488?tab=releases`
- Join link: `https://play.google.com/apps/internaltest/4701695168286427488`
- Release: `1 (1.0.0)`
- Uploaded bundle: `target/app-release.aab`
- New install size reported by Play Console: `11.3 MB`
- Tester list: `testers` with 3 users.

Observed non-blocking Play Console warnings for the first internal release:

- No tester access before selecting a tester list; resolved by selecting `testers`.
- No deobfuscation file attached.
- No native debug symbols attached for the native code in the App Bundle.

## Android developer verification

Package name registration is complete in Play Console:

- Play Console package page: `https://play.google.com/console/u/1/developers/4849820391120439995/android-developer-verification/packages/tatar.eljah.fluitblox`
- Package name: `tatar.eljah.fluitblox`
- Friendly name: `Fluiteblox`
- Status observed in Play Console: confirmed.
- Verification snippet used for proof-of-key APK: `DGILD3IC7CURKAAAAAAAAAAAAA`
- Verification APK: `target/fluiteblox-adi-verification.apk`

Release certificate fingerprints:

- SHA-1: `B4:A9:1B:73:95:49:97:A4:F6:40:49:69:D5:28:CB:49:BB:44:95:A8`
- SHA-256: `0C:F1:6F:59:C6:35:87:CB:05:AB:FD:17:66:0B:BE:6B:66:9A:D9:C9:63:13:AB:B6:9A:53:AC:9D:02:07:CB:22`

Certificate identity:

- Owner/issuer: `CN=Recorder Coach, OU=Music, O=FluiteBlox, L=Kazan, ST=Tatarstan, C=RU`
- Valid from: `2026-02-13`
- Valid until: `2053-07-01`

## Build artifact

Google Play requires Android App Bundles for new apps. Build the release bundle on Windows with:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\build-aab.ps1 -AndroidHome D:\Java\sdks\android
```

On a Unix-like Android SDK, the bash script is also available:

```bash
ANDROID_HOME=/path/to/android/sdk ./scripts/build-aab.sh
```

The resulting upload artifact is:

```text
target/app-release.aab
```

Increment `android:versionCode` in `src/main/AndroidManifest.xml` for every release submitted to Play.

## Play Console setup values

- Default language: Russian or English.
- Name: Fluiteblox.
- Type: Game.
- Price: Free.
- Category: Educational / Music game.
- Target audience: children and families, depending on the final policy choice in Play Console.
- Contact email: `ilya.evlampiev@gmail.com`.
- Privacy policy URL: `https://raw.githubusercontent.com/Eljah/fluiteblox/codex/find-out-if-audiveris-supports-mobile-scanning/docs/PRIVACY_POLICY.md`.

## Policy notes

The app requests:

- `RECORD_AUDIO`: real-time pitch recognition during recorder practice and the tank game.
- `CAMERA`: optional photo capture of sheet music for adding melodies.

Current implementation has no `INTERNET` permission. Audio and captured sheet images are processed locally by the app and are not uploaded by this app.

For the Data safety form, describe the app as handling microphone and camera data locally for core functionality. If no analytics, ads, accounts, or network upload are added, mark no sharing and no server-side collection.

## Play Console assets

Configured in the Standard Store Listing draft:

- App icon: `docs/play-assets/fluiteblox-icon-512.png`.
- Feature graphic: `docs/play-assets/feature-graphic.png`.
- Phone screenshots: `docs/play-assets/phone-screenshots/`.
