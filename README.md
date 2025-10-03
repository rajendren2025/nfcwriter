# NfcWriter (Android, Kotlin)

This repo writes/reads **plain text** NDEF to NFC tags and stays rewritable.

## Run in Gitpod (no local Android Studio required)

1. Push this project to a GitHub repo.
2. Prefix the repo URL with `https://gitpod.io/#` to open in Gitpod.
3. Gitpod will build using the provided `.gitpod.Dockerfile`.
4. The build task runs `gradle assembleDebug`. Download the APK from:
   `app/build/outputs/apk/debug/app-debug.apk`

> Note: NFC testing requires a real device. Download the APK and install on your Android phone.

## Run locally (Android Studio)
- Open the project and Run on a physical NFC device.

## Files added for Gitpod
- `.gitpod.Dockerfile` – Installs Android SDK command line tools, platform 35, build-tools 35.0.0
- `.gitpod.yml` – Workspace tasks to accept licenses & build
- `scripts/setup-android-sdk.sh` – Manual setup helper

## App features
- Read & write NDEF Text records
- Tag info: UID, tech list, NDEF size & writable
- Live character count
- Clear button
