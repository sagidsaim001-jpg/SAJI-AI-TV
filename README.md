# SAJI AI TV

A remote-first Android TV app that opens Gemini and ChatGPT inside an immersive WebView.

## Features
- Gemini and ChatGPT launcher cards
- Fullscreen immersive browser
- Left-side Back and Microphone controls
- Android TV D-pad focus and selection support
- WebView JavaScript, DOM storage, and audio permission support
- Animated focus and screen transitions
- GitHub Actions debug APK build

## Build
The GitHub Actions workflow builds `app-debug.apk` with JDK 17 and Gradle.

Open **Actions → Build SAJI AI TV APK → Run workflow** or push a commit. Download the APK from the workflow run's Artifacts section.

## Notes
The microphone control attempts to activate the voice/microphone control exposed by the currently loaded website. Website UI changes can affect this behavior. Microphone access is requested only when the user presses the microphone control.

This project is an independent WebView client. Gemini and ChatGPT accounts, login, availability, and website features remain controlled by Google and OpenAI.