# Deploy

Everything needed to put GaleHeart on a host.

## Build the package

On a machine with JDK 21:

```powershell
cd deploy
.\build.ps1
```

The script compiles nothing — it takes the jars from `plugins/*/target`,
adds configs, `server.properties`, `eula.txt` and writes
`deploy/out/galeheart-deploy.zip`.

Build the jars first if `target/` is empty:

```bash
cd plugins/galeheart-core && javac ... # or mvn package
```

## What is inside the archive

```
galeheart-deploy/
├── eula.txt
├── server.properties
├── README-DEPLOY.md        this file
└── plugins/
    ├── GaleHeartCore.jar
    ├── GaleHeartArena.jar
    ├── AnomalousWeather.jar
    ├── PortalBlock.jar
    ├── FirstJoinClass.jar
    └── <plugin>/config.yml  (only the ones we tuned)
```

The rest (LuckPerms, WorldGuard, TAB, ChatControl, CoreProtect, Grim) is
installed from their own downloads — see `docs/plugins.md`.

## Upload order

1. Upload `paper-1.21.11-*.jar` as `server.jar`.
2. Upload everything from the archive into the server root.
3. Start once, wait for `Done`, stop.
4. Install the P0 plugins from `docs/plugins.md` (LuckPerms first).
5. Apply `docs/luckperms.md` in the console.
6. Paste the Discord webhook into both configs.
7. Start again.

## JVM flags (6 GB)

```
-Xms5G -Xmx5G -XX:+UseG1GC -XX:+ParallelRefProcEnabled
-XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions
-XX:+DisableExplicitGC -XX:+AlwaysPreTouch -XX:G1NewSizePercent=30
-XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M
-XX:G1ReservePercent=20 -XX:G1HeapWastePercent=5
-XX:G1MixedGCCountTarget=4 -XX:InitiatingHeapOccupancyPercent=15
-XX:G1MixedGCLiveThresholdPercent=90 -XX:G1RSetUpdatingPauseTimePercent=5
-XX:SurvivorRatio=32 -XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1
```

## First-run checklist

- [ ] `Done (…)!` with no red plugin errors
- [ ] `/plugins` lists five green GaleHeart plugins
- [ ] arena world created, platform built
- [ ] spawn platform built at the world spawn
- [ ] `/season announce hello` posts to Discord
- [ ] `/hearts` works for a real player
- [ ] world border set (`/worldborder set 12000`)
- [ ] Chunky pregen started and finished
- [ ] hourly backups enabled on the host

## Pregen

```
/chunky radius 3000
/chunky start
```

Run before opening. On 6 GB it takes a while; do it once.

## Backups

Host panel: hourly, 7 days retention. Additionally copy the world folder
off-host once a day — panels lose disks too.
