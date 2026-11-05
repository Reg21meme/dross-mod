# Release check tools

What was used for the 0.3.0 check (November 5, 2026; the results are in `WORLDGEN_REPORT.md`, stage 7). None of this is
part of the mod or its jar.

## A real Forge server: `server_test.py`

Runs the built jar on a real Forge 1.20.1 dedicated server (not the dev setup), makes a new world and drives it through
its console: the cathedral's, the trader's and the hub's land is forceloaded and checked with commands (the frame's
blocks, exactly one trader, the hub's two survivors and its portal), six areas of 256 Dross chunks and one of the
Overworld are generated, `/forge tps` is read after each, and the log is searched for errors, exceptions and crash
reports. It writes `<out dir>/report.txt` (its last line `RESULT: ALL GOOD` or the problems) and keeps the logs.

1. Install a server once, in a folder outside the project:
   `java -jar forge-1.20.1-47.4.10-installer.jar --installServer <server dir>` (the installer from
   maven.minecraftforge.net).
2. Put GeckoLib (`geckolib-forge-1.20.1-4.8.4.jar`) in one folder and the built Dross jar (`build/libs/`) in another
   (`fetch_mods.py <folder> geckolib` downloads GeckoLib from Modrinth and checks its sha1).
3. `python -I server_test.py <server dir> <out dir> <seed> <geckolib folder> <dross folder>` (about 5 minutes). Add
   more folders for more mods (a modpack test), `--config test-config.toml` to start with a changed settings file, and
   `--keep-world` to open the world of the run before (checking it keeps its own settings).

The server's world, logs, mods and config are wiped at the start of each run (unless `--keep-world`).

## The real game: `WorldProbe.java.template`

A temporary client class (copy it to `src/main/java/com/reg21meme/dross/perf/client/WorldProbe.java`; delete it again
before building a jar or committing). With `DROSS_PROBE` set, the game makes a new default world (seed
`DROSS_PROBE_SEED`), visits every Dross place with `/dross goto`, and at each stop waits for it to load, measures the
frame times and the integrated server's tick times, counts the mobs round you (within 128 blocks, within 32 on your
level) and the bosses and keepers loaded anywhere, takes a screenshot (`run/screenshots/probe_*`) and writes
`run/perf/probe_report_<seed>.txt`; then it makes a second world in the same game (its own cathedral, trader and hub)
and quits. `DROSS_PROBE_ONLY=hub` visits only the hub and the Gravecrags, for a profile. Set `pauseOnLostFocus:false`
in `run/options.txt` first (and put it back after):

```
$env:DROSS_PROBE="1"; $env:DROSS_PROBE_SEED="20261105"; .\gradlew.bat runClient --console=plain
```

About 20 minutes at render distance 24.

## Profiles: `jfrstat.py`

While the probe stands at a place, record the client with JFR (`jcmd <pid> JFR.start duration=55s filename=x.jfr`,
the default settings: `settings=profile` slows the game), then
`jfr print --events jdk.ExecutionSample,jdk.NativeMethodSample --stack-depth 60 x.jfr | python -I jfrstat.py "Render thread"`:
the render thread's top frames, how much of it is in Minecraft's chunk drawing, entities, the swap, and every mod frame.
