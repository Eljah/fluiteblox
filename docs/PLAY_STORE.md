# Google Play publish checklist for this project

This project currently builds an **APK** via the legacy `android-maven-plugin`. Google Play now expects **signed release artifacts** and, for new uploads, **Android App Bundles (AAB)** rather than debug APKs. Below is what is missing and what needs to change to make a publishable build.

## 1) Release signing (required)
Google Play only accepts **signed** release artifacts.

**What to do**
- Create or use an existing keystore (`.jks`) and **sign the release build**.
- Keep the keystore and passwords out of the repo (use environment variables or a local properties file).

**Where it matters in this repo**
- The build is driven by `android-maven-plugin` in `pom.xml`, which does not currently define any signing configuration.

## 2) Build type: AAB (required for new uploads)
New apps and updates must be uploaded as **AAB** (Android App Bundle). The current Maven setup packages as `apk` only, so we add a bundletool step on top of Maven to create a release AAB.

**What to change**
- Keep Maven for APK builds, and use the bundletool AAB step for the release bundle (see `scripts/build-aab.sh`).

## 3) Versioning metadata (required)
Google Play requires `versionCode` and `versionName` to be set.

**What to change**
- Add `android:versionCode` and `android:versionName` to `AndroidManifest.xml`.
- Increment `versionCode` for every release.

## 4) Target / min SDK (required and enforced by Play)
Play enforces minimum **targetSdkVersion** levels (updated yearly). The manifest currently omits SDK levels.

**What to change**
- Add `<uses-sdk android:minSdkVersion="..." android:targetSdkVersion="..." />` in `AndroidManifest.xml`.
- Update the build toolchain to a modern Android SDK / build tools level.

## 5) Package name / app identity (required)
`com.example.*` is a placeholder package. Play requires a unique application ID.

**What to change**
- Replace `com.example.tonetrainer` with your own unique package name.

## 6) Store listing / policy requirements (required)
These are outside the build system but required to publish:
- App name, icon, screenshots, feature graphic.
- Privacy policy URL if you collect or transmit data (this app uses microphone and may send speech data).
- Data safety form in the Play Console.

---

### Summary: “what is missing to publish?”
1. **Release signing** (keystore + signing config).
2. **AAB output** (bundletool step after Maven APK build).
3. **Manifest versioning** (`versionCode` / `versionName`).
4. **Modern SDK targeting** (`minSdkVersion`, `targetSdkVersion`).
5. **Unique package name** (not `com.example`).
6. Store listing + policy assets.
