# The photo booth (in-game screenshot check)

`PhotoBooth.java.template` is a **temporary** client-side helper for checking a new boss in the real game without
anyone at the keyboard (BOSS_GUIDE.md, step 6). It isn't compiled from here; copy it into the boss's client package
when you need it, and delete it again afterwards.

What it does, when its environment variable is set (otherwise nothing):

1. On the title screen it makes a creative superflat world (cheats on, Peaceful) and opens it, in a 1600 x 900 window.
2. It runs a script of commands, one per tick it's due: game rules, teleports to viewpoints (the camera is held
   still every tick, so a stray mouse can't turn it), the showcase commands and their controls.
3. It takes screenshots at chosen moments (`run/screenshots/<prefix>_<name>.png`), with the GUI hidden.
4. It leaves the showcase rows standing, puts the player in front of them, and quits the game.

The world stays in `run/saves/`, ready for the user to open.

## How to use it

1. Copy it to `src/main/java/com/reg21meme/dross/boss/<boss>/client/<Boss>PhotoBooth.java`. Rename the class, the
   environment variable (`DROSS_<BOSS>_PHOTOS`), the world name and the screenshot prefix. Rewrite `buildScript()` for
   your showcase: where the rows stand, which controls to play, and when to shoot.
2. In `run/options.txt` set `pauseOnLostFocus:false`, or the game pauses when its window isn't focused. Back the file
   up first and restore it afterwards.
3. Build, then from the project folder:
   `$env:DROSS_<BOSS>_PHOTOS="1"; .\gradlew.bat runClient --console=plain`
   The game opens, works through the script by itself, and closes.
4. Look at every screenshot and compare it with the previews (orientation, UVs, glow layers, particles, hidden parts).
5. Delete the class and `run/screenshots/`, restore `options.txt`, and build again.

Tips: give each run's world a fresh name (or delete the old one first). Keep the rows away from 0,0 (a world without
structures puts the trader's emergency chapel near spawn). Superflat ground is at y = -61, so feet stand at y = -60.
