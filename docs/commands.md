# Commands

Everything a player or an admin can type. No teleports anywhere: `/home`, `/tpa`,
`/spawn`, `/back` do not exist on purpose.

## Player

| Command | What it does |
|---|---|
| `/hearts` | Your hearts out of 20 |
| `/hearts withdraw <n>` | Turn hearts into droppable heart items (floor is 1) |
| `/bounty` | Your current contract - the player you are hunting |
| `/top` | Hearts leaderboard, top 10 |
| `/pass` | Opens the season pass menu: level, shards, cosmetic shop |
| `/pass shop` | Same menu |
| `/pass buy <id>` | Buy without opening the menu |
| `/pass trail off` | Turn your particle trail off |
| `/pass info` | Text version, for screenshots |
| `/quest` | Quest chains and their progress |
| `/season` | Season state (finale on or off) |

Hearts: start at 10, cap 20, kill pays one, death costs one. Below 1 heart the
death penalty applies: Weakness II + Slowness II for 10 minutes.

Bounty: every player gets a personal contract. Kill the target, take a heart, the
contract rerolls. On bounty nights the payout doubles.

## Admin

Permission: `galeheart.admin` (group `admin`).

| Command | What it does |
|---|---|
| `/grant <player> <rank>` | Add a LuckPerms group (`supporter`, `patron`, `pass`) and announce it |
| `/hearts add <player> <n>` | Adjust hearts, negative values subtract |
| `/pass shards <player> <n>` | Adjust a player's shards (support and refunds) |
| `/season finale` | Start the one-heart finale |
| `/season stop` | Stop the finale |
| `/season reset` | Reset the season counters |
| `/season announce <text>` | Post an announcement to `#announcements` and `#events` |
| `/season bounty start` | Open the bounty window now |
| `/season bounty stop` | Close it early |
| `/season bounty` | Window status |

## Weather

Permission: `anomalousweather.admin`.

| Command | What it does |
|---|---|
| `/anomaly status` | Active anomaly or the next scheduled one |
| `/anomaly list` | Every weather type, chance and state |
| `/anomaly start <type>` | Force an anomaly (acid_rain, fire_rain, blizzard, sandstorm, toxic_fog, ...) |
| `/anomaly stop` | End the current anomaly |
| `/anomaly give <player> <type>` | Hand out the protection item for a weather type |
| `/anomaly reload` | Reload config and messages |

Two anomalies per day maximum. The warning train is 60, 30, 10, 5 and 1 minute
before start, in chat and in `#announcements`.

## Other plugins

| Command | Plugin |
|---|---|
| `/lp` | LuckPerms - users, groups, prefixes |
| `/mv` | Multiverse - worlds |
| `/rg` | WorldGuard - regions |
| `/co` | CoreProtect - block log and rollback |
| `/chunky` | Pregen |
| `/tps`, `/spark` | Performance |
| `/tab` | TAB scoreboard and header |
| `/discordsrv` | Discord bridge |

## Console shortcuts

From the panel console the same commands work without a slash prefix. The API
helper in `bot/` wraps that if you want scripted calls.

## What staff should not do

- Do not give hearts for donations.
- Do not teleport players to resolve disputes; walk them or return items.
- Do not op accounts for convenience. Use the `mod` role and `/lp`.
