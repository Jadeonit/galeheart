# Release test — 20 Nov 2026

Paper 1.21.11-132, Java 21, Linux-equivalent runtime on Windows, offline mode,
flat test world, RCON enabled. Bots: Mineflayer 4.39 (protocol 1.21.11).

## What ran

| # | Check | Result |
|---|---|---|
| 1 | All five plugins enable without exceptions | pass |
| 2 | First-join welcome message | pass |
| 3 | New-player protection notice | pass |
| 4 | `/hearts` reports 3/10 | pass |
| 5 | `/safepvp` teleports both players to the arena | pass |
| 6 | Duel start message on both clients | pass |
| 7 | Duel ends on death | pass |
| 8 | Loser restored to the overworld, kit removed | pass |
| 9 | Protected player keeps hearts after death | pass |
| 10 | Protection bypass gives a real heart loss | pass |
| 11 | Quest step: mob kill counted | pass |
| 12 | Quest complete grants the dimension key | pass |
| 13 | `/season finale` drops everyone to one heart | pass |
| 14 | `/season stop` restores hearts | pass |

Additionally verified from the console: arena world auto-created in a void
generator, platform built on first boot, `/arena reload`, `/portalblock reload`,
weather schedule → start → end, `/anomaly list`, `/anomaly status`.

## Bugs found and fixed

1. **Protection bypass did not affect hearts.** The permission
   `galeheart.bypass.protection` cancelled PvP damage but the death handler
   still treated the player as protected, so no heart was lost. Fixed in
   `HeartManager#onDeath`.
2. **Quest collection checks started a minute late.** The repeating task had a
   60-second initial delay, so handing in items right after a kill did nothing
   for up to a minute. Fixed to 5 seconds.

## Not covered

- Portal gating (`PortalBlock`): needs a real Nether portal and a player walking
  into it; unit-level permission logic is in place but was not exercised.
- Donations and Tebex delivery: not implemented yet.
- 20+ player load: the test ran with two bots.

## How to reproduce

```bash
# paper.jar 1.21.11, online-mode=false, enable-rcon=true
# plugins: GaleHeartCore, GaleHeartArena, AnomalousWeather, PortalBlock, FirstJoinClass
node test.js
```
