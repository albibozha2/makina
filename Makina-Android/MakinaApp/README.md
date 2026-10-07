# MAKINA: Albanian voice assistant for Android

A fullscreen HUD in the style of Person of Interest and Jarvis. It listens and speaks in Albanian.

## Getting the APK (free, works from your phone)

1. Make a free account at github.com.
2. Tap **+ → New repository**, name it `makina`, set it to **Public** or **Private**, then tap **Create**.
3. Tap **uploading an existing file** and upload *every file and folder* from this zip.
   Keep the `.github` folder, because it holds the build script.
   (On a phone, the GitHub website in desktop mode or the free "GitHub" app both work.)
4. Open the **Actions** tab. A run named "Build Makina APK" starts on its own and takes about 3 minutes.
   If it doesn't start, tap "Build Makina APK", then "Run workflow".
5. When it shows a green tick, open **Releases** on the main repo page and download `Makina.apk`.
6. Open the APK on your phone. Allow "Install unknown apps" for your browser or file manager when asked.

## Making it your phone's assistant (like Jarvis)
Go to Settings → Apps → Default apps → **Digital assistant app** and choose **Makina**.
Now holding the Home button (or swiping up from a bottom corner) opens Makina, already listening.

## How to use it
- **Tap the core** to talk.
- **Long-press** to type a command, change settings, or turn on always-listening.
- Speech recognition uses Google's engine. Install the "Google" app if recognition is missing.
- Voice: if your phone has an Albanian text-to-speech voice, Makina uses it offline.
  Otherwise it uses Google's online Albanian voice.
- Optional: add an Anthropic API key in Settings so it can answer any question in Albanian.

## Commands
sa është ora · çfarë date është sot · bateria · skano sistemin · si është moti në Durrës ·
kush është Skënderbeu · ndiz/fik dritën · shkruaj shënim … · lexo shënimet · fshi shënimet ·
më kujto pas 10 minutash të … · zgjomë në orën 7 e 30 · telefono Artanin · hap YouTube ·
luaj Dua Lipa · kërko … · hidh monedhën · më quaj Arben · qyteti im është Shkodër ·
dëgjo vazhdimisht · ndalo dëgjimin · mirupafshim · ndihmë
