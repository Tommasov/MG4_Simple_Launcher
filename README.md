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
- **Six-tile home** (optional): the same three columns counted as halves, for six
  smaller favourites instead of three large cards. The first three keep their slots,
  so switching arrangements leaves your apps where you left them. **Off by default**
  — turn it on in Settings.
- **Fourth column** (home):
  - **All apps** (top card): every launchable app, in a grid.
  - **Dock** (bottom card): two small slots side by side, set to Android's **Files**
    and **Settings** to begin with and changeable like any other slot. Drawn flatter
    than the cards above it, so it reads as a shelf rather than as a fourth favourite.
- **More than apps on a tile**: besides an installed app, a slot can hold an Android
  settings screen (Wi-Fi, Bluetooth, mobile data, display, apps), one of the
  launcher's own screens, or one of the **vehicle's own screens** found on the car
  itself — charge management, the cameras, the climate panels. The emergency-call
  screens are deliberately never offered: opened outside a real call they show a
  call in progress while nobody has been dialled.
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
- **Downloads**: the author's other apps for this car, listed with what is installed and what
  is not, and installed from here — the only way onto a head unit that has no store and no
  browser. See below.
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
- The **battery** figure on each row is the charge you would arrive with. It comes
  from the remaining range the car reports, the distance to the station and a
  consumption model, and it is coloured once it gets thin: amber at or below 15%,
  red at or below 5%. It is an estimate, and the card says so rather than hiding
  behind a ≈ sign.
- The card **follows the car**. The rows are redrawn every 200 m so the distances
  and the arrival figures stay honest while you drive, and the search itself runs
  again only after 10 km — Open Charge Map bans callers that poll it, and a station
  list does not change over a few hundred metres. The **refresh** button reads the
  list again straight away whatever the distance.

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
- The **charge on arrival**, corrected for the route when the factory navigator is
  guiding. Straight-line distance understates a drive, so the card multiplies it by
  a road factor; once the navigator is running, the launcher asks it for the real
  remaining distance and time instead of guessing, and the panel says when the
  figure is route-corrected. The correction is only ever applied when it makes the
  estimate more pessimistic.
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

### Journey panel

When the factory navigator is guiding, the charging page shows what it knows: the
distance and the time still to go, and the charge you are expected to arrive with.
The figures come from the vehicle's own adapter service rather than from a
calculation of ours, so they agree with what the navigator is showing on its own
screen. Without an active route the panel is not shown at all.

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

## Downloads

The other apps written for this car, reached from the button beside *Settings* on the
charging points page. A head unit has no Play Store, no browser worth downloading from, and
otherwise no way in at all short of carrying a USB stick out to the car — which is the whole
reason this screen exists.

It is not a shop. The catalogue is one JSON file on the author's server with the binaries
beside it. The screen lists what exists, says which of them this car already has and at what
version, and offers to install or update the rest. Every download is checked against the hash
published in the catalogue before Android is asked to install it — the same path the
launcher's own updates take, and for the same reason: there is no store here to vouch for
anything.

## Settings

Reached from the button on the charging points page:

- **Launch page**: which of the pages the launcher opens on.
- **Features**: the shortcuts page, the six-tile home, whether to look for updates
  at launch, and the beta channel.
- **Vehicle**: which battery this car has — 51, 64 or 77 kWh, by the figure on the
  spec sheet. The arrival estimate cannot be worked out without it. Cars sold as
  49 kWh are the 51 pack quoted as usable rather than gross, so they belong to the
  51 entry; internally the launcher uses the usable energy of each pack, which is
  what the car can actually spend.
- **Updates**: the installed version, with a **BETA** mark beside it while you are
  on the pre-release channel, and a manual check.
- **System**: the technical details, and the diagnostics log.

### Updates

The launcher checks a small manifest on the author's server and, when a newer build
exists, offers to download and install it. Android asks for permission to install
unknown apps the first time.

Joining the **beta channel** brings pre-release builds. Leaving it is not immediate:
Android will not install an older stable build over a newer beta, so a car stays on
the beta until a stable release overtakes it.

### Technical details

A screen of live, permission-free readings laid out in three columns: device model,
Android version, build fingerprint and uptime, the installed launcher build and the
WebView engine behind it, memory and storage, and the network — what is carrying the
connection, the Wi-Fi link speed, and the mobile operator when the head unit's own
SIM is the one online.

### Diagnostics log

An on-device log, with a crash handler behind it. A head unit cannot be reached
over adb, so when something goes wrong in the car this is the only way to find out
what: it records what the launcher was doing, why a download or a position lookup
failed, and the stack trace of a crash.

From here the log can be **sent to the author** as a report. It is worth saying why
this exists rather than a copy button: there is nowhere on this head unit for copied
text to go. No app of the car's own accepts a share — the firmware ships the
Bluetooth stack with object push disabled — and there is no text field to paste
into. Before sending, a dialogue says exactly what leaves the car (the log, the
build, the model of the head unit, and that the log can contain position fixes) and
asks for one line about what you were doing; nothing is sent until you confirm. The
log can also simply be cleared.

## Screenshots

<p align="center">
  <img src="https://ws2.tommasovietina.it/mg4/MG4_Simple_Launcher/17/home.png" alt="Home: three favourite slots waiting to be filled, All apps, and the dock" width="800" />
</p>

<p align="center">
  <img src="https://ws2.tommasovietina.it/mg4/MG4_Simple_Launcher/17/charging.png" alt="Charging points card: the four nearest stations of the chosen network, and the journey panel" width="800" />
</p>

<p align="center">
  <img src="https://ws2.tommasovietina.it/mg4/MG4_Simple_Launcher/17/map.png" alt="Charging points in full screen: filters, list, and the car over OpenStreetMap" width="800" />
</p>

<p align="center">
  <img src="https://ws2.tommasovietina.it/mg4/MG4_Simple_Launcher/17/map-selected.png" alt="A station picked: the line from the car, the address, the connectors by kind and power, and the button that hands it to the navigator" width="800" />
</p>

<p align="center">
  <img src="https://ws2.tommasovietina.it/mg4/MG4_Simple_Launcher/shortcuts.png" alt="Shortcuts page: eight assignable tiles, shown empty" width="800" />
</p>

<p align="center">
  <img src="https://ws2.tommasovietina.it/mg4/MG4_Simple_Launcher/17/settings.png" alt="Settings: launch page, features, vehicle and the launcher’s own screens" width="800" />
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

## License

MG4 Simple Launcher is free software: you can redistribute it and modify it under
the terms of the **GNU General Public License, version 3 or later**, as published
by the Free Software Foundation. The full text is in [`LICENSE`](LICENSE).

    Copyright (C) 2026 Tommaso Vietina

    This program is distributed in the hope that it will be useful, but WITHOUT ANY
    WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
    PARTICULAR PURPOSE. See the GNU General Public License for more details.

In practice this means anyone may use this, change it and publish their own version
— and a published version has to carry its source under the same licence. That is
the whole intent: the work stays open for the people driving these cars.

**What this licence does not cover**: the graphic resources taken from the vehicle's
own system software. They are not mine to license — see *Graphic resources* below.
The licence applies to the code written for this project.

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
