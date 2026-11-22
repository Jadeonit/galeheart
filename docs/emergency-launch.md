# Emergency launch — 10 minutes

Use this when the server has to open today and there is no time for a runway.
It assumes the world is ready and the whitelist is on.

## Minute 0–3: pre-flight

```
/version                 Paper 1.21.11
/tps                     above 19
/list                    who is online
/co lookup t:5m          CoreProtect is logging
/whitelist list          only you and trusted players
```

- Backup first: panel → Backups → Create. Name it `launch-YYYY-MM-DD`.
- Check `plugins/GaleHeartCore/config.yml` has `bounty.enabled: true` (it ships
  paused). If it is false, flip it and restart before opening.
- Check `starter-kit.book: true`, `pass.levels: 100`, `pass.cosmetics` has 23
  entries.

## Minute 3–5: open the gate

```
/whitelist off
/season bounty start        opens a 20 minute window right now
```

Post the announcement (`docs/hype-post.md`, section 5) and pin it. If Discord
events are available, create one starting now.

## Minute 5–10: seed the room

An empty server kills itself in the first ten minutes.

- Get 5–8 people in before you post anywhere else. Friends, your own second
  account, anyone who will play for twenty minutes.
- Put two of them at spawn fighting each other. New players should walk into
  noise, not silence.
- Start the first bounty window so there is a visible objective from minute one.

## Donations from minute one

Manual delivery, works today without a payment processor:

```
/grant <player> supporter        $5  - [Supporter] tag + Discord role
/grant <player> patron           $15 - [Patron] tag + cosmetics
/grant <player> pass             $7  - season pass cosmetic track
/cosmetic give <player> founder  free - [Founder] title, first 20 donors only
```

Order for a founder: `/grant <player> supporter` then `/cosmetic give <player>
founder`, then tell them to run `/locker` and equip the title.

Keep a ledger in `docs/donations.md`: date, player, rank, amount, reference.

## First hour

- Answer every Discord message within a minute. Activity keeps the advert up.
- Watch `latest.log` for exceptions on first joins.
- Watch TPS with 10+ players; if it drops below 18, check `/spark`.
- Screenshot spawn with players in it — that is tomorrow's post.

## What not to do

- Do not open with zero players and hope. Seed first.
- Do not change configs after the first join announcement; restart later.
- Do not sell hearts, kits or claims to cover costs faster. It kills the server
  faster than an empty launch does.
