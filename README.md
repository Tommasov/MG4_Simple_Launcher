# MG4 Simple Launcher

<p align="center">
  <img src="https://ws2.tommasovietina.it/mg4/MG4_Simple_Launcher.png" alt="MG4 Simple Launcher" width="200" />
</p>

MG4 Simple Launcher is a simple custom home launcher designed for the MG4 head
unit (1920×720, landscape). It reuses the original SAIC launcher artwork so it
fits visually with the system, while providing a minimal, focused home screen.

## Features

- **Swipeable three-page home**: a horizontal carousel (`ViewPager2`). Swipe left/right
  between the launcher home (page 1), a **shortcuts** grid (page 2) and a
  **system-info** screen (page 3). A SAIC-style bar indicator at the bottom centre
  shows the current page.
- **Three favorite cards** (page 1): three vertical cards, each launching one app of
  your choice. Tap a card to open its app; **long-press** to assign or change it.
- **Fourth column**:
  - **All apps** (top card): every launchable app, in a grid.
  - **Two fixed shortcuts** (bottom card): the Android 9 default **Files** and
    **Settings** apps, side by side as icons.
- **Shortcuts grid** (page 2): eight assignable tiles for the apps that don't fit on
  the three home cards. Tap to launch, long-press to change or clear a tile.
- **System apps & updates**: inside the *All apps* drawer, the header carries a
  **System apps** button (only system apps, `FLAG_SYSTEM`) next to **Check for
  updates**, plus a **back** button to return home.
- **App info shortcut**: **long-press** any app in the *All apps* or *System apps*
  drawer to jump straight to Android's app-details screen (permissions, storage,
  uninstall).
- **Light / dark theme**: follows the system day/night mode automatically, using
  the original SAIC light and dark artwork.
- **Persisted favorites**: the three home cards and the eight grid tiles are saved
  across reboots, in separate slots that never overwrite each other.

## Changing a pinned app

**Long-press** one of the three big cards to open the app picker, then tap the app
you want in that slot. Your choice is saved across reboots.

## Second screen (shortcuts)

Swipe right from the home to reach the shortcuts grid (`FavoritesGridFragment` /
`res/layout/fragment_favorites.xml`): eight tiles laid out as four columns of two,
each with the same proportions as the half cards in the home page's fourth column.

- **Tap** a tile to launch its app. An empty tile opens the app picker.
- **Long-press** a filled tile to *change* the app or *remove* it, leaving the tile
  empty again.

These eight slots are stored separately from the three home cards, so assigning an
app here never disturbs the home page.

## Third screen (system info)

Swipe right once more to reach the system-info page (`SystemInfoFragment` /
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
  <img width="320" height="180" alt="ezgif-295db3ba8dbf70b5" src="https://github.com/user-attachments/assets/7a3e3bb3-c81e-41d8-ad17-c9b56d28c359" />
</p>

<p align="center">
  <img src="https://ws2.tommasovietina.it/mg4/MG4_Simple_Launcher/Screenshot_1782141845.png" alt="MG4 Simple Launcher — home screen" width="800" />
</p>

<p align="center">
  <img src="https://ws2.tommasovietina.it/mg4/MG4_Simple_Launcher/Screenshot_1782141854.png" alt="MG4 Simple Launcher — system info screen" width="800" />
</p>

## Videoguida in italiano

Una guida video dedicata al pubblico italiano, che mostra il launcher in funzione
sulla MG4:

<p align="center">
  <a href="https://www.youtube.com/watch?v=XDgJUkOuVAg">
    <img src="https://img.youtube.com/vi/XDgJUkOuVAg/maxresdefault.jpg" alt="MG4 Swipe, e le nuove APP compaiono!!! — videoguida in italiano" width="640" />
  </a>
</p>

<p align="center">
  <a href="https://www.youtube.com/watch?v=XDgJUkOuVAg"><strong>MG4 Swipe, e le nuove APP compaiono!!!</strong></a>
</p>

> 🇬🇧 *Italian-language video guide showing the launcher running on the MG4 head unit.*

## Build

Standard Android project (Java, AGP 8.6, Gradle 8.7, `minSdk 28` / `targetSdk 34`).

```
./gradlew assembleDebug
```

The debug APK is produced under `app/build/outputs/apk/debug/`.

**JDK**: Gradle 8.7 runs on JDK 17–21 and fails on newer ones with a bare
`IllegalArgumentException: <version>` from the Kotlin DSL compiler. Recent Android
Studio releases bundle a JBR newer than that, so set *Settings → Build Tools →
Gradle → Gradle JDK* (or `JAVA_HOME` on the command line) to a JDK 17 or 21.

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

### Graphic resources

This launcher deliberately reuses graphic resources taken from the vehicle's own
system software — card backgrounds, tab artwork, switches, map pins — so that it
looks and behaves like part of the native interface rather than a foreign app.
That visual continuity is the point of the project.

Those resources are **not licensed to this project**. They remain the property of
SAIC/MG and their respective owners, and are included here only so that the app
can match the system on a vehicle that already contains them. No ownership is
claimed over them, and their presence implies no permission, endorsement, or
affiliation. Anyone who redistributes this project, or builds on it, does so under
their own responsibility.

The same applies to trademarks and brand names, used here descriptively only.

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

### Risorse grafiche

Questo launcher riusa deliberatamente risorse grafiche prese dal software di
sistema del veicolo — sfondi delle card, grafica dei tab, interruttori, segnaposto
della mappa — perché appaia e si comporti come parte dell'interfaccia nativa e non
come un'app estranea. Questa continuità visiva è lo scopo del progetto.

Tali risorse **non sono concesse in licenza a questo progetto**. Restano di
proprietà di SAIC/MG e dei rispettivi titolari, e sono incluse qui solo affinché
l'app possa uniformarsi al sistema su un veicolo che già le contiene. Non se ne
rivendica la titolarità e la loro presenza non implica alcuna autorizzazione,
approvazione o affiliazione. Chi ridistribuisce questo progetto, o vi costruisce
sopra, lo fa sotto la propria responsabilità.

Lo stesso vale per marchi e nomi commerciali, qui usati a soli fini descrittivi.
