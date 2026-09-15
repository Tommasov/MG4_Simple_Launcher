# MG4 Simple Launcher

<p align="center">
  <img src="https://ws2.tommasovietina.it/mg4/MG4_Simple_Launcher.png" alt="MG4 Simple Launcher" width="200" />
</p>

MG4 Simple Launcher is a simple custom home launcher designed for the MG4 head
unit (1920×720, landscape). It reuses the original SAIC launcher artwork so it
fits visually with the system, while providing a minimal, focused home screen.

## Features

- **Swipeable home**: a horizontal carousel (`ViewPager2`) between the launcher
  home and a **charging points** page, with an optional **shortcuts** grid in
  between. A SAIC-style bar indicator at the bottom centre shows the current page,
  and you choose which page the launcher opens on.
- **Three favorite cards** (home): three vertical cards, each launching one app of
  your choice. Tap a card to open its app; **long-press** to assign or change it.
- **Fourth column** (home):
  - **All apps** (top card): every launchable app, in a grid.
  - **Two fixed shortcuts** (bottom card): the Android 9 default **Files** and
    **Settings** apps, side by side as icons.
- **Charging points**: the nearest stations of one network, from Open Charge Map,
  with a full-screen map, a detail panel for the station you pick, and a **send to
  the car's navigator** button. See below.
- **Shortcuts grid** (optional page): eight assignable tiles for the apps that don't
  fit on the three home cards. Tap to launch, long-press to change or clear a tile.
  **Off by default** — turn it on in Settings.
- **Settings**: launch page, optional features, update checks, and the technical
  screens. Reached from the button on the charging points page.
- **System apps**: inside the *All apps* drawer, the header carries a **System apps**
  button (only system apps, `FLAG_SYSTEM`) next to a **back** button.
- **App info shortcut**: **long-press** any app in the *All apps* or *System apps*
  drawer to jump straight to Android's app-details screen (permissions, storage,
  uninstall).
- **Over-the-air updates**: the launcher can check for a new build and install it
  itself, on a stable or a beta channel.
- **Light / dark theme**: follows the system day/night mode automatically, using
  the original SAIC light and dark artwork.
- **Persisted favorites**: the three home cards and the eight grid tiles are saved
  across reboots, in separate slots that never overwrite each other.

## Changing a pinned app

**Long-press** one of the three big cards to open the app picker, then tap the app
you want in that slot. Your choice is saved across reboots.

## Charging points

The charging points page lists the stations nearest to the car, taken from the
[Open Charge Map](https://openchargemap.org) registry, and opens a full screen with
a map when tapped.

On the card itself:

- The **gear** chooses which network the card lists: everything nearby, the
  motorway network, Superchargers open to non-Tesla vehicles, or fast DC. The
  choice is remembered.
- The **refresh** button reads the list again straight away. The card otherwise
  loads once per visit and never on a timer: Open Charge Map bans callers that
  poll it.

Tapping the card opens the full screen **on the network the card was showing**,
with the same choices as tabs, a list on the left and an OpenStreetMap map on the
right (through osmdroid — no Play Services, no Google Maps API key).

Pick a station and it is highlighted on the map, a line is drawn from the car, and
a panel opens over the map with what Open Charge Map knows about it:

- The **address**, and the **connectors broken down by kind and power** — "2 × CCS
  (Type 2) 300 kW · 2 × Type 2 22 kW". A site's headline power often belongs to one
  bay out of several, which the breakdown makes plain.
- Whether the station is **not simply open to anyone** — private, or by arrangement.
  Nothing is said about public stations, or about the membership tariffs some
  networks record, because a padlock that turns out to be wrong costs a usable stop.
- The button that hands the destination to the car's navigator.

Prices are deliberately absent. Open Charge Map carries them as free text, often
missing and sometimes years old, and what you actually pay depends on the app you
start the charge with.

Nothing is restricted to one country: the search follows the car, so it works
abroad and, more usefully, across a border you are about to drive over.

Location comes from Android's own providers, and is followed for as long as the
screen is open — the car marker moves with the vehicle, and points the way it is
travelling once it is moving fast enough for the fix to carry a bearing. Parked, it
shows a plain disc rather than an arrow in an invented direction. The list keeps the
distances from when the search ran: re-querying on every fix is the polling Open
Charge Map asks callers not to do.

The first fix after a cold start can take minutes when the car's own mobile data is
off, because assisted GPS rides on that connection; the screen keeps looking as long
as it is open and says so.

### Sending a destination to the car's navigator

Stations carry a **navigate** action — an arrow on each row, and a button on the
map once a station is selected. What happens next depends on what the vehicle has:

1. **Cars with the factory navigator.** The destination is handed to the SAIC
   adapter service over Binder, exactly as the voice assistant does when asked to
   drive somewhere. The navigator receives the station's name and address and adds
   it either as a **waypoint on the route in progress** or as a **new destination**,
   the same choice it offers for any other point of interest.
2. **Cars without it, but with some map app installed.** The launcher falls back to
   a standard `geo:` intent, and whichever app handles those opens with the station.
3. **Neither.** The navigate action is not shown at all, rather than offered and
   then failing.

**Which cars have the factory navigator**: it ships with the full infotainment
package — in Europe the MG4 trims that carry it are the higher ones (Luxury,
Trophy and above); the Standard and Comfort trims have no navigator, and for them
the fallback above applies. The launcher works this out by asking whether anything
on the vehicle can accept a destination, not by reading the model: `build.prop`
identifies the head unit, not the trim, so it cannot answer the question — and
checking the capability also gets the case of an owner who installed a map app of
their own.

Verified on a Trophy (R71 firmware) with the European Telenav navigator. The iGO
and SAIC navigators shipped in other markets are recognised as well, but have not
been tested.

## Shortcuts page (optional)

A grid of eight tiles laid out as four columns of two, each with the same
proportions as the half cards in the home page's fourth column.

- **Tap** a tile to launch its app. An empty tile opens the app picker.
- **Long-press** a filled tile to *change* the app or *remove* it, leaving the tile
  empty again.

The page is **off unless you turn it on** in *Settings → Features*: eight tiles are
more than most people fill, and every app is one tap away on the *All apps* button
anyway. Installations that already had shortcuts assigned keep the page when they
update.

These eight slots are stored separately from the three home cards, so assigning an
app here never disturbs the home page.

## Settings

Reached from the button on the charging points page:

- **Launch page**: which of the pages the launcher opens on.
- **Features**: the shortcuts page, whether to look for updates at launch, and the
  beta channel.
- **Updates**: the installed version, and a manual check.
- **System**: the technical details, and the diagnostics log.

### Updates

The launcher checks a small manifest on the author's server and, when a newer build
exists, offers to download and install it. Android asks for permission to install
unknown apps the first time.

Joining the **beta channel** brings pre-release builds. Leaving it is not immediate:
Android will not install an older stable build over a newer beta, so a car stays on
the beta until a stable release overtakes it.

### Technical details

A screen of live, permission-free readings: device model, Android version and
uptime, the installed launcher build, memory, storage, and the active network with
its Wi-Fi link speed.

### Diagnostics log

An on-device log, with a crash handler behind it. A head unit cannot be reached
over adb, so when something goes wrong in the car this is the only way to find out
what: it records what the launcher was doing, why a download or a position lookup
failed, and the stack trace of a crash. It can be copied to the clipboard and
cleared.

## Screenshots

<p align="center">
  <img width="320" height="180" alt="MG4 Simple Launcher in use" src="https://github.com/user-attachments/assets/7a3e3bb3-c81e-41d8-ad17-c9b56d28c359" />
</p>

<p align="center">
  <img src="https://ws2.tommasovietina.it/mg4/MG4_Simple_Launcher/home.png" alt="Home: three favourite cards, all apps and the two fixed shortcuts" width="800" />
</p>

<p align="center">
  <img src="https://ws2.tommasovietina.it/mg4/MG4_Simple_Launcher/charging.png" alt="Charging points: the nearest stations of the chosen network" width="800" />
</p>

<p align="center">
  <img src="https://ws2.tommasovietina.it/mg4/MG4_Simple_Launcher/map.png" alt="Charging points map: filters, list and the car position over OpenStreetMap" width="800" />
</p>

<p align="center">
  <img src="https://ws2.tommasovietina.it/mg4/MG4_Simple_Launcher/shortcuts.png" alt="Shortcuts page: eight assignable tiles, shown empty" width="800" />
</p>

<p align="center">
  <img src="https://ws2.tommasovietina.it/mg4/MG4_Simple_Launcher/settings.png" alt="Settings: launch page, features, updates and the system screens" width="800" />
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

**Open Charge Map key**: the charging features need an API key, read from a
git-ignored `apikeys.properties` at the project root:

```
OCM_API_KEY=your-key-here
```

Without it the launcher builds and runs; the charging card simply says the data is
unavailable.

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

### Vehicle interfaces

Sending a destination to the factory navigator, and reading anything the vehicle
knows about itself, go through private SAIC system services that are not a
published API. They were worked out by reading the firmware that is already on the
car. They can stop working at any time, and every failure path in the launcher is
built to fall back quietly rather than break.

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

Charging point data comes from [Open Charge Map](https://openchargemap.org) and is
used under its terms; the attribution stays visible wherever results are shown.
Map tiles come from OpenStreetMap contributors.

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

### Interfacce del veicolo

L'invio di una destinazione al navigatore di serie, e la lettura dei dati che
l'auto conosce di sé, passano da servizi di sistema SAIC privati, che non sono
un'API pubblica: sono stati ricavati leggendo il firmware già presente sul veicolo.
Possono smettere di funzionare in qualsiasi momento, e ogni percorso di errore del
launcher è costruito per ripiegare in silenzio anziché rompersi.

Il tasto di invio al navigatore compare solo sulle vetture che hanno qualcosa in
grado di accettare una destinazione: il navigatore di serie, presente sugli
allestimenti con infotainment completo (Luxury, Trophy e superiori) e non sulle
versioni Standard e Comfort, oppure un'app di mappe installata dal proprietario.

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

I dati dei punti di ricarica provengono da
[Open Charge Map](https://openchargemap.org) e sono usati secondo i suoi termini;
l'attribuzione resta visibile ovunque i risultati vengano mostrati. Le mappe sono
di OpenStreetMap e dei suoi contributori.
