# Season 1 — Acid Sky

Eight weeks. Weather is the constant, the bounty is the twist, the finale is
the payoff.

## Constants

| Rule | Value |
|---|---|
| Start | 3 hearts |
| Cap | 10 hearts |
| Death cost | 1 heart, dropped as an item |
| New player protection | 24 hours, full PvP immunity |
| Death cooldown | 3 minutes without heart loss |
| Combat tag | 15 seconds |
| Heart cooling | above 8 hearts, −1 per 48 hours without a kill |
| Weather | at most 2 anomalies per day, announced 1 hour ahead |
| Bounty | Friday, Saturday, Sunday at 19:00 UTC, 20 minutes |
| Nether / End | sealed until the matching quest is finished |
| Teleports | none. Beds only, world spawn as fallback |
| Season pass | 30 levels, XP from play and events, cosmetic shards only |

## Why this shape

The season has one job: make the last two weeks feel different from the first
two. Early on people are spread out and careful, hearts are low, weather is a
real threat. By week six the map is mapped, alliances are set, and the only
thing left is conflict. The bounty and the finale exist to convert that stored
tension into events.

## Calendar

| Week | Focus | What happens |
|---|---|---|
| **0** | Prep | pregen, closed test with 5–10 people, fix what breaks, announce the date |
| **1** | Launch | onboarding, first hearts, first bounty on Friday |
| **2** | Expansion | most players finish the Nether Key, netherite enters the economy |
| **3** | First check | look at retention, tune weather weights, nothing else |
| **4** | The End | End Key completed by the first groups, elytra appears |
| **5** | Arena night | duel tournament at the arena, cosmetic prize |
| **6** | Pressure | cooling bites, hearts concentrate, first big raids |
| **7** | Storm week | anomalies at the top of the daily cap, bounty every night |
| **8** | Finale | announcement on Monday, finale on Saturday, reset on Sunday |

Week 3 is deliberately empty. If something is broken, that is the week to fix
it without a content deadline on top.

## Weekly rhythm

- **Mon–Thu** — quiet. Weather once a day, no events. This is when people
  build and farm.
- **Fri–Sun** — bounty at 19:00 UTC, weather at the daily cap. This is when
  people fight.
- **Sunday 21:00** — weekly `/top` post in Discord: top 5 hearts, biggest
  heart loss, most bounties survived.

The gap between weekday calm and weekend noise is what makes weekends feel
like weekends.

## Finale

Starts Saturday of week 8, 19:00 UTC.

1. `/season announce The finale begins in one hour.`
2. `/season finale` — everyone drops to one heart, border shrinks to 500.
3. 30 minutes of open war.
4. `/season stop`, then the results post in Discord.

Winner: most hearts at the end. Ties broken by bounty kills, then by total
kills. Prizes: a Discord role, a title in the next season, and a cosmetic
item — nothing that gives stats.

## Reset checklist

1. Announce the reset 48 hours ahead, twice.
2. Run the finale.
3. Stop the server.
4. Back up the whole world folder off-host.
5. Delete `world`, `world_nether`, `world_the_end`, `arena` (or keep arena).
6. Delete `plugins/GaleHeartCore/data.yml` and `plugins/GaleHeartCore/snapshots`.
7. Swap the weather preset (see below).
8. Start, check `/top` is empty, check the spawn platform rebuilt.
9. Post the season 2 announcement.

## Weather presets

Season 1 ships as the default config. Future seasons: copy the config, change
these four values, keep the file as `presets/season-N.yml`.

```yaml
check-interval-seconds: 14400
anomaly-chance-percent: 45
max-per-day: 2
warning.lead-seconds: 3600
```

Ideas for later seasons, one at a time:

| Season | Weather twist | Event |
|---|---|---|
| 2 | acid rain disabled, blizzard twice as long | shrinking border |
| 3 | only toxic fog and void storm | one life |
| 4 | calm weather, longer nights | blood moon |

## Metrics to watch

| Metric | Target | Where |
|---|---|---|
| Peak concurrent | 10 by week 2, 20 by week 5 | Plan |
| D1 retention | above 25% | Plan |
| D7 retention | above 10% | Plan |
| Average session | above 20 minutes | Plan |
| Discord members | 3× peak CCU | Discord |
| Donations | 1–2% of monthly players | Tebex |

If D1 is under 15% in week 2, the problem is the first ten minutes, not the
marketing. Fix onboarding before making more clips.

## Announcement templates

**Launch**

> Season 1: Acid Sky is live.
> 3 hearts, no teleports, the weather is hunting you.
> play.galeheart.com

**Bounty**

> Bounty on Steve. 20 minutes. Your compass knows the way.

**Finale**

> Saturday, 19:00 UTC. One heart each. Last one standing takes the season.

## What can go wrong

| Risk | Mitigation |
|---|---|
| Player dies far from base, quits | watch chat; if it repeats, add `/spawn` with a 10 min cooldown |
| Bounty target hunted every night | 24h cooldown per player, min 8 hearts |
| Weather feels like a tax | keep the cap at 2/day, never raise it mid-season |
| Hearts concentrate too fast | cooling threshold 8, raise to 7 if needed |
| Nobody uses the arena | arena night in week 5, cosmetic prize |
| Server dies at week 3 | do not add content. Fix retention first |
