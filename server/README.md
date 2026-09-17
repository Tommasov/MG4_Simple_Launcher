# probe.php

Where the launcher's diagnostics log goes when a driver taps **Send**.

It exists because there is no other way out of the car. The MG4's head unit cannot be reached
over adb, has no browser worth the name, and — this was checked in the firmware, not assumed —
not one system app that accepts a share: `Bluetooth_nfore_eh32` ships with
`profile_supported_opp` set to `false`, which disables the only activity declaring
`ACTION_SEND`. The clipboard is no better, since there is no text field on board to paste
into. Before this endpoint, a report could only be photographed off the screen.

## What it does

Accepts a report. Nothing else.

```
POST /mg4/probe.php
    k     write key, in the body only
    app   which app sent it: launcher, swipe, …
    text  the report
    note  one line of context, optional

→ 200  OK launcher/20260917-093236-95602d.txt
→ 400  ERR Il referto era vuoto.
→ 403  (wrong or missing key)
→ 429  ERR Troppi invii da questo indirizzo.
```

Reports land in `probe-data/<app>/<timestamp>-<random>.txt` and are collected over FTP. The
directory gets a `Deny from all` on creation and the files are `0600`.

## Why there is nothing to read

The key ships inside every APK. A hundred people have it, and anyone who unzips one can read
it — so the endpoint is built on the assumption that it is public. It cannot read a report
back, list what is there or delete anything, by anyone, with any key. The worst an attacker
can do is fill the directory, and that is bounded: twelve posts an hour per address, 256 KB
per report, 200 reports per app, thirty days.

That matters because the logs carry GPS fixes. A single key that could both write and read
would have put every tester's movements behind a string sitting in a public APK.

The key is only accepted from the POST body, never from the query string, so it does not end
up in Apache's access log or in a `Referer`.

## Deploying

`$KEY` is empty here on purpose, the same way `apikeys.properties` and `keystore.properties`
are kept out of the repository. Fill it in on the server and put the same value in
`apikeys.properties` as `probe.key`; the app hides the Send button when it has none.

Edit this copy and upload it, rather than editing the live file — otherwise the two drift and
the repository stops being the record of what is actually running.
