# SmoothWhirlingSlash

A Windows overlay for Path of Exile 2 that shows a calibrated movement range and optionally moves an out-of-range cursor back into it.

## How it works

Two ellipses define the inner and outer limits. The space between them is the valid cursor range. When snapping is enabled and you hold the trigger key, a cursor outside this range moves along its direction from the calibrated center to halfway between the two ellipse intersections. A cursor already inside the range stays untouched. The exact center has no direction, so it also stays untouched; destinations outside the game client or outside the range after pixel rounding are skipped.

A fresh trigger press checks immediately when detected; holding it checks every 100 ms. Checks never queue up. Focus loss, disabling snapping, or changing settings requires releasing and pressing the trigger again, preventing an old held key from causing unexpected movement. Snapping starts off and requires an available emergency shortcut.

The green range display and dashed midpoint guide are optional. Snapping works with the range display hidden; a highlight around the default E skill slot shows when snapping is enabled. The overlay passes mouse input through and hides while the game is unfocused. Closing or minimizing Settings keeps the current snapping state and leaves the app in the system tray.

Coordinates use physical pixels so the drawn range and cursor movement agree. The game client bounds are captured once at startup: restart after moving or resizing the game, changing monitors/scaling, or restarting PoE2. Use borderless-windowed mode; the skill-slot highlight assumes the standard keyboard/mouse HUD at its default scale.

## Controls

| Default | Action |
| --- | --- |
| Hold E | Snap while enabled |
| Ctrl+Alt+F8 | Toggle range display |
| Ctrl+Alt+F9 | Toggle snapping |
| Ctrl+Alt+F10 | Show/hide Settings |
| Ctrl+Alt+F12 | Disable snapping immediately |

The large snapping switch and two smaller display switches are clickable anywhere. Range calibration starts folded to avoid accidental edits. Valid changes save automatically to `settings.json`; invalid edits keep the previous values. Click a shortcut to replace it. Global shortcuts pause during capture, changes apply after key release, and Esc cancels. Snapping stays off after recording until you enable it again.

## Portable app

Extract the whole ZIP into a writable folder, open PoE2, and double-click `SmoothWhirlingSlash.exe`. Java is included. Keep the `app` and `runtime` folders beside the executable; extract before running rather than launching inside the ZIP.

Settings and `application.log` stay beside the executable, including when started from a shortcut. To keep your existing calibration, copy your `settings.json` there. Otherwise the app starts with its built-in defaults. Move or copy the whole folder to move the app and its settings together.

Build the folder and ZIP on Windows with JDK 26: `./gradlew.bat portableZip`. Outputs are `build/portable/SmoothWhirlingSlash` and `build/distributions/SmoothWhirlingSlash-portable.zip`. These are generated build outputs; use an extracted copy for daily use.

## Run and troubleshoot

For development, use JDK 26 and open PoE2 before launching the included **Whirling Slash** IDEA configuration, or run `./gradlew.bat run`. Development runs store settings and logs in the working directory. Run tests with `./gradlew.bat test`; Swing tests need a desktop session.

Unexpected errors stop the app and append the original exception to `application.log` beside `settings.json`. Send that log when reporting a failure. Corrupt settings remain unchanged.

Cursor manipulation may violate GGG's automation rules; this app does not claim GGG approval. It does not read game memory, inject code, click, or activate skills. The icon is adapted from the [Whirling Slash artwork on PoE2DB](https://poe2db.tw/Whirling_Slash).
