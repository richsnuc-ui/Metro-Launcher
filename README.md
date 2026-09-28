# Metro Launcher

A fast, Windows Phone (Metro) style home screen for Android phones, foldables and tablets.
Tiles are drawn directly to the screen with no third-party libraries, so it stays light and quick.

## What's in it

- **Start screen** that scrolls vertically, built from tiles in four sizes: small, medium, wide, large
- **Sections** you can name or leave blank. On a phone they stack; on a Galaxy Z Fold's inner screen,
  a tablet or in landscape they sit side by side with gaps between them
- **Guided first run**: take the defaults, or pick layout (one, two or three sections), tile size
  (compact / normal / large), tile shape (square, rounded, squircle, circle), colors and dock
- **Color modes**: colorful, one accent color, or monochrome; five palettes (Windows Phone, pastel,
  earth, neon, ocean); optional white monochrome icons (uses Android 13 themed icons where available)
- **Live tiles** that flip: Clock (time, date, next alarm), Weather, Calendar (next event),
  Music (now playing with album art; tap to play/pause), Notifications (a folder of your notifications)
- **Real Android widgets** as tiles, mixed in with Metro tiles
- **Sticky dock** (up to 6 apps) and a **compact app list** option
- **Bing image of the day** behind semi-transparent tiles, rotating through the last 8 days
- **App list**: swipe left or tap the arrow; letter headers (tap one to jump), search at the top

Weather comes from Open-Meteo and backgrounds from Bing; neither needs an account.
Requires Android 8.0 or newer.

**Not included:** lock-screen widgets (Android doesn't allow third-party apps to add them on phones)
and on-device AI summaries (Gemini Nano access is limited to approved apps).

## Get the APK

### Option A: free cloud build on GitHub (nothing to install)

1. Sign in at github.com and create a new **empty** repository (for example `metro-launcher`).
2. On a computer, on the repository page choose **uploading an existing file**, then drag in
   *everything inside* this folder, including the hidden `.github` folder. Commit.
   (If `.github` doesn't upload, use **Add file → Create new file**, name it
   `.github/workflows/build.yml`, and paste in that file's contents.)
3. Open the **Actions** tab. "Build APK" runs automatically and takes about 3–5 minutes.
4. When it finishes, open **Releases** on your phone and tap **MetroLauncher.apk**.

### Option B: Android Studio

1. Install Android Studio, then **File → Open** this folder and let it sync.
2. **Build → Build App Bundle(s) / APK(s) → Build APK(s)**. The file is saved to
   `app/build/outputs/apk/debug/app-debug.apk`. Or connect your phone with USB debugging on and press **Run**.

## Install and set up

1. Open the APK on your phone and allow installing from that app when Android asks.
2. Open **Metro Launcher**, choose **Use defaults** or **Customize**.
3. Allow location (weather) and calendar (Calendar tile) if you want those tiles.
4. Choose Metro Launcher as your **home app** when asked
   (later: Settings → Apps → Default apps → Home app).
5. For the **Music** and **Notifications** tiles, tap either tile and turn on Notification access.
   On Android 13 and later the switch may be greyed out for apps installed from a file. If so:
   Settings → Apps → Metro Launcher → **⋮** (top right) → **Allow restricted settings**, then try again.

## Using it

- **Press and hold a tile, then drag**: move it anywhere, including into another section
- **Press and hold a tile and let go**: resize, recolor, or unpin
- **Press and hold a section name** (or the thin strip above an unnamed section): rename, move, add to it, delete
- **+ button** at the bottom of Start: add an app tile, Android widget, live tile, or new section
- **••• button**: all settings
- **App list**: press and hold an app to pin it to Start or add it to the dock
- Press **Home** any time to jump back to the top of Start
