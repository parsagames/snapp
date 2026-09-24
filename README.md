# SnappBox Voice Helper

Android/Kotlin project for testing the SnappBox voice helper.

## Build

- Use Android Studio with JDK 17.
- Gradle wrapper target: 8.9.
- Android Gradle Plugin: 8.7.3.
- If Gradle reports that `com.android.application:8.7.3` cannot be resolved, this is a repository/network/proxy issue rather than a Kotlin source-code error. In Android Studio, make sure Gradle is not in Offline mode and that Google/Maven Central are reachable.

## Important

The project has not been validated against a real SnappBox installation in this environment. The Accessibility package name may need to be adjusted if the installed SnappBox app uses a different package name.

## Building an APK automatically on GitHub (no local setup needed)

This repo includes a GitHub Actions workflow (`.github/workflows/build-apk.yml`) that builds a **debug APK** on every push and lets you download it — you don't need Android Studio installed.

1. Push this project to a new GitHub repository (see steps below).
2. Go to the repo's **Actions** tab on GitHub.
3. Open the latest **Build APK** run (or click **Run workflow** to trigger it manually).
4. When it finishes, open the run and download the **SnappBoxVoiceHelper-debug-apk** artifact from the "Artifacts" section at the bottom of the run page. It contains `app-debug.apk`.
5. Copy that APK to your phone and install it (you'll need to allow "install from unknown sources" once, since it isn't signed for a store).

### Pushing this project to GitHub

```bash
cd SnappBoxVoiceHelper
git init
git add .
git commit -m "Initial commit"
git branch -M main
git remote add origin https://github.com/<your-username>/<your-repo>.git
git push -u origin main
```

After the push, the workflow starts automatically. No secrets or signing keys are required for the debug build.

### Notes
- The debug APK is unsigned for release/Play Store purposes but is installable directly on a device (debug builds use Android's built-in debug signing key).
- If you want a signed **release** APK (e.g. to share more widely), you'll need to generate a keystore, add it as GitHub repo secrets, and extend the workflow with a signing step — ask if you'd like that added.
- This app automates the SnappBox rider app on your own device via Android's Accessibility API. Make sure this use is consistent with SnappBox's terms of service before relying on it.
