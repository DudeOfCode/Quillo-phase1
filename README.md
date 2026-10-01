# Quillo — Document Editor for Android

Quillo is a native Android wrapper around a full, on-device rich-text document
editor. It writes real paginated A4 pages, imports/exports Word `.docx`, exports
print-ready PDF, and supports tables, images, headings, lists, section breaks and
per-section page numbering — all rendered inside a redesigned, icon-driven UI.

## What's inside

- `app/src/main/assets/index.html` — the complete editor (redesigned chrome:
  icon toolbar, grouped ribbon, tabbed navigation, light/dark theme toggle).
- `app/src/main/java/com/quillo/app/MainActivity.kt` — the WebView shell:
  - serves the app over the secure origin `https://appassets.androidplatform.net`
    via `WebViewAssetLoader`;
  - a file chooser that honours the editor's `accept` types (`.docx`, images);
  - a JavaScript bridge that captures the editor's `Save .docx` / `Export PDF`
    downloads and writes them to **Downloads/Quillo/**.
- `.github/workflows/android.yml` — CI that builds a debug APK in the cloud.

## Build it

### Option A — one command (needs JDK 17 + Android SDK + internet)

```bash
./gradlew assembleDebug
```

The debug APK lands at `app/build/outputs/apk/debug/app-debug.apk`.
The Gradle wrapper JAR is not committed; `gradlew` downloads the matching
version (Gradle 8.7) automatically on first run.

### Option B — Android Studio

Open the project folder, let it sync, then **Run** or **Build > Build APK(s)**.

### Option C — GitHub Actions (no local toolchain)

Push the project to a GitHub repo. The workflow builds on every push to
`main`/`master` and can be run manually from the **Actions** tab
(*Build APK > Run workflow*). Download the `quillo-debug-apk` artifact when it
finishes. A signed-release job is included, commented out, ready for keystore
secrets.

## About offline use (please read)

The editor's core — typing, formatting, tables, images, pagination, autosave
(via local storage), light/dark theme — runs **100% offline on the device**.

Four features rely on JavaScript libraries loaded from a CDN, so they need an
internet connection the first time they run:

| Feature            | Library            |
|--------------------|--------------------|
| Open `.docx`       | mammoth 1.6.0      |
| Save `.docx`       | docx 8.5.0         |
| Export PDF         | html2canvas 1.4.1 + jsPDF 2.5.1 |

This is why the app requests the `INTERNET` permission. To make these fully
offline as well, download the four scripts into `app/src/main/assets/vendor/`
and change the four `<script src="https://...">` tags at the top of
`index.html` to point at `vendor/…`. (The build sandbox had no network access,
so the libraries could not be bundled automatically.)

## Verification status

The project structure, Kotlin, Gradle config and the HTML/JS were written and
statically checked, but **no APK has actually been compiled** in this
environment (no Android SDK / blocked network). The first `./gradlew
assembleDebug` or the first CI run is the real build test.
