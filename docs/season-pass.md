# Season pass, locker, achievements

Three menu systems, one economy. Shards come from play only; nothing sold or
unlocked here touches combat, hearts or loot.

## Season pass — `/pass`

A chest menu, not a wall of chat lines.

- **Player head** — level, XP bar to the next level, shards, equipped title,
  achievements unlocked.
- **Locker** — opens the cosmetic wardrobe.
- **Achievements** — opens the board.
- **Level rewards** — shows the next four unlocks with their level.

100 levels. XP sources, each with a daily cap so nobody farms one thing:

| Source | XP | Daily cap |
|---|---|---|
| Playtime | 90 per hour | 300 |
| Vanilla XP orbs | 1 per point | 300 |
| Player kills | 40 per kill, once per victim per hour | 200 |
| Quest step | 150 | 300 |

Level cost: `90 + 6 × (level - 1)`. Total to 100 is 38,016 XP, roughly 48 capped
days — a full season for a daily player, level 30–50 for a casual one.

Every level pays 4 shards. Levels 5 / 10 / 20 / 30 / 40 / 50 / 60 / 70 / 85 / 100
unlock a cosmetic outright.

## Locker — `/locker`

Three tabs, each with its own label:

| Tab | What it is | How it shows |
|---|---|---|
| **Titles** | a chat prefix set through LuckPerms | `[Gale]` in front of your name |
| **Trails** | particles that follow you | client-side, no advantage |
| **Join effects** | a one-shot burst when you log in | particles + sound at your spawn point |

Everything is a grid item: icon, type in the lore, cost, the pass level required,
and its state (`Locked` / `Owned` / `Equipped`). Click a locked item to buy it
with shards, click an owned one to equip, click the equipped one to remove it.
Owned items glow. Page arrows appear when a tab has more than 28 entries.

Titles that are equipped replace the current prefix; removing a title clears it.

## Achievements — `/achievements`

Twenty lifetime goals, each paying shards and showing a progress bar:

| Group | Examples |
|---|---|
| PvP | First Blood (1 kill), Hunter (10), Warlord (50) |
| Survival | Welcome to Hell (die once), Survivor (10 h), Veteran (50 h) |
| Mining | Miner (1,000 blocks), Deep Diver (10,000), Diamond Hands (32 diamonds) |
| Combat | Monster Slayer (250 mobs) |
| Hearts | Heart Collector (15 hearts), Heart Lord (20) |
| Bounty | Bounty Hunter (5 contracts) |
| Season | Season Regular / Grinder / Legend (levels 25 / 50 / 100) |
| Economy | Collector (earn 1,000 shards), Big Spender (spend 500) |
| Story | Through the Gate (one quest), Two Gates (both) |

Progress is stored per player and survives restarts. The board is paginated, 28
per page, unlocked entries glow.

## Cosmetic catalogue

22 items: 10 titles, 7 trails, 5 join effects. Costs run 80–540 shards, unlock
levels 1–100. The full list lives in `plugins/galeheart-core/config.yml` under
`pass.cosmetics` — add an entry, restart, it appears in the locker with no code
change.

## Admin

```
/pass add <player> <xp>       grant or remove XP
/pass shards <player> <n>     grant or remove shards
/pass info                    text version of the progress
```

`/pass buy <id>` and `/pass trail off` still work as text fallbacks.
