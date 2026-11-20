# Install day

Two hours, from an empty panel to a server you can walk around in.

## 0. What you need open

- Panel of the host (Pterodactyl-like).
- `deploy/out/galeheart-deploy.zip`
- `deploy/out/paper.jar` (Paper 1.21.11)
- Discord channel where the webhook lives.

## 1. Create the server — 5 min

- Game: **Minecraft Java**, software **Paper**, version **1.21.11**.
- Plan: **6 GB** (GAME-3), location **Germany**.
- Note the **port** and the **subdomain** the panel gives you.
- Apply promo `WELCOME` on the first top-up if the panel has a promo field.

## 2. Upload — 10 min

Via the panel file manager or SFTP:

```
/                 paper.jar  (rename to server.jar if the panel expects that)
/eula.txt
/server.properties
/plugins/         all five jars from the archive
```

Start once, wait for `Done`, stop. This generates config folders.

## 3. Put our configs in place — 5 min

The archive already contains tuned configs. Overwrite the generated ones:

```
plugins/GaleHeartCore/config.yml
plugins/GaleHeartArena/config.yml
plugins/AnomalousWeather/config.yml
plugins/PortalBlock/config.yml
plugins/FirstJoinClass/config.yml
```

Restart. In the log you should see:

```
[GaleHeartCore] Built the spawn platform at ...
[GaleHeartArena] Built the arena platform.
[AnomalousWeather] Anomalies loaded: 7
```

## 4. P0 plugins — 40 min

Download and drop into `plugins/`, one restart at the end:

| Plugin | Where |
|---|---|
| LuckPerms | luckperms.net |
| WorldGuard + WorldEdit | enginehub.org |
| Multiverse-Core | modrinth |
| CoreProtect | modrinth |
| GrimAC | modrinth |
| TAB | modrinth |
| ChatControl Red | spigotmc (paid) |

Then apply `docs/luckperms.md` in the console.

## 5. Webhook — 5 min

Channel → Integrations → Webhooks → New → copy URL.

In both configs:

```yaml
discord:
  enabled: true
  webhook: "https://discord.com/api/webhooks/..."
```

Restart, run `/season announce hello`.

## 6. World — 20 min

```
/worldborder set 12000
/chunky radius 3000
/chunky start
```

Pregen runs in the background. Do not open the server to strangers until it
finishes — new chunks cause lag spikes.

## 7. First walk — 15 min

Join yourself and check, in this order:

1. You spawn 100–250 blocks from the world spawn, not on it.
2. `/hearts` shows `3/10`.
3. Die once (`/kill <you>`): heart is lost, a Heart item drops.
4. `/top` lists you.
5. `/safepvp <friend>` → accept → both teleported to the arena → die → back.
6. `/quest` shows the Nether Key with progress.
7. Weather: `/anomaly start ACID_RAIN 30` — particles, bossbar, damage.
8. `/season bounty start` — compass given, bossbar on the target.
9. `/season announce test` — message in Discord.

## 8. Backups — 5 min

Panel: hourly, keep 7 days. Additionally download the `world` folder once a day
to your own disk. Panels lose disks too.

## Common failures

| Symptom | Cause |
|---|---|
| `Unknown command /hearts` | plugin not loaded, check `/plugins` |
| Hearts not changing | player still under 24h protection |
| Portals open | PortalBlock config missing, or permission granted by LuckPerms |
| No Discord messages | webhook empty, `enabled: false`, or outbound HTTPS blocked by host |
| Arena missing | world failed to create; check log for `arena` |
| Lag spikes | pregen not finished |
