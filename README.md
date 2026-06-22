# MG4 Simple Launcher

<p align="center">
  <img src="https://ws2.tommasovietina.it/mg4/MG4_Simple_Launcher.png" alt="MG4 Simple Launcher" width="200" />
</p>

MG4 Simple Launcher is a simple custom home launcher designed for the MG4 head
unit (1920×720, landscape). It reuses the original SAIC launcher artwork so it
fits visually with the system, while providing a minimal, focused home screen.

## Features

- **Swipeable two-page home**: a horizontal carousel (`ViewPager2`). Swipe left/right
  between the launcher home (page 1) and a **system-info** screen (page 2). A
  SAIC-style bar indicator at the bottom centre shows the current page.
- **Three favorite cards** (page 1): three vertical cards, each launching one app of
  your choice. Tap a card to open its app; **long-press** to assign or change it.
- **Fourth column**:
  - **All apps** (top card): every launchable app, in a grid.
  - **Two fixed shortcuts** (bottom card): the Android 9 default **Files** and
    **Settings** apps, side by side as icons.
- **System apps & updates**: inside the *All apps* drawer, the header carries a
  **System apps** button (only system apps, `FLAG_SYSTEM`) next to **Check for
  updates**, plus a **back** button to return home.
- **Light / dark theme**: follows the system day/night mode automatically, using
  the original SAIC light and dark artwork.
- **Persisted favorites**: the three chosen apps are saved across reboots.

## Changing a pinned app

**Long-press** one of the three big cards to open the app picker, then tap the app
you want in that slot. Your choice is saved across reboots.

## Second screen (system info)

Swipe right from the home to reach the system-info page (`SystemInfoFragment` /
`res/layout/fragment_system.xml`). It shows live, permission-free stats that refresh
while the page is visible:

- **Device**: manufacturer + model, Android version (release · API), uptime, and the
  installed launcher version.
- **Memory**: used / total RAM.
- **Storage**: free / total internal storage.
- **Network**: active connection type (Wi-Fi / mobile / Ethernet / offline) and, on
  Wi-Fi, the negotiated link speed.

## Screenshots

<p align="center">
  <img src="https://ws2.tommasovietina.it/mg4/MG4_Simple_Launcher/ezgif-273580ea3cfcbd0f.gif" alt="MG4 Simple Launcher — demo" width="900" />
</p>

<p align="center">
  <img src="https://ws2.tommasovietina.it/mg4/MG4_Simple_Launcher/Screenshot_1782141845.png" alt="MG4 Simple Launcher — home screen" width="800" />
</p>

<p align="center">
  <img src="https://ws2.tommasovietina.it/mg4/MG4_Simple_Launcher/Screenshot_1782141854.png" alt="MG4 Simple Launcher — system info screen" width="800" />
</p>

## Build

Standard Android project (Java, AGP 8.6, Gradle 8.7, `minSdk 28` / `targetSdk 34`).

```
./gradlew assembleDebug
```

The debug APK is produced under `app/build/outputs/apk/debug/`.

## Disclaimer (English)

This project is provided **for study and educational purposes only**. It is an
experimental, non-commercial project and is not affiliated with, endorsed by, or
supported by SAIC, MG, or any vehicle manufacturer.

The software is provided "as is", without warranty of any kind, express or
implied. The author accepts **no liability** for any direct, indirect, incidental,
or consequential damage of any kind — including but not limited to damage to the
vehicle, its infotainment system, software, or data, loss of functionality, or
safety-related consequences — arising from the installation or use of this app.
You use it entirely **at your own risk**. Do not interact with the app while
driving.

All graphic resources, trademarks, and brand names belong to their respective
owners and are used here for study purposes only.

## Avvertenze (Italiano)

Questo progetto è fornito **esclusivamente a scopo di studio ed educativo**. È un
progetto sperimentale, non commerciale, non affiliato né approvato o supportato da
SAIC, MG o da alcun costruttore di veicoli.

Il software è fornito "così com'è", senza garanzie di alcun tipo, esplicite o
implicite. L'autore non si assume **alcuna responsabilità** per qualsiasi danno
diretto, indiretto, incidentale o consequenziale — incluso, a titolo
esemplificativo, danni al veicolo, al sistema di infotainment, al software o ai
dati, perdita di funzionalità o conseguenze relative alla sicurezza — derivante
dall'installazione o dall'uso di questa app. L'utilizzo avviene interamente **a
proprio rischio**. Non interagire con l'app durante la guida.

Tutte le risorse grafiche, i marchi e i nomi commerciali appartengono ai
rispettivi proprietari e sono utilizzati qui solo a scopo di studio.