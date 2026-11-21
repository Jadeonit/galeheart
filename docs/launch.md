# Launch checklist

Run top to bottom before posting the advert. Anything unchecked is a reason not
to open yet.

## Server

- [ ] Paper 1.21.11 is the running jar (`/version`)
- [ ] `online-mode=false` is intentional and noted on the advert (offline mode)
- [ ] whitelist off (`/whitelist off`)
- [ ] spawn platform is breakable, first join scatters inside 250 blocks
- [ ] `/hearts`, `/bounty`, `/pass`, `/quest`, `/season` answer in game
- [ ] `/grant <player> supporter` works and posts the embed
- [ ] no teleport commands exist (`/home`, `/tpa`, `/spawn` return unknown)
- [ ] last-heart penalty applies once (Weakness II + Slowness II, 10 minutes)
- [ ] test accounts removed from `/top`
- [ ] keepalive bots stopped

## Weather

- [ ] `/anomaly list` shows all types with sane chances
- [ ] daily cap is 2
- [ ] warning train fires at 60 / 30 / 10 / 5 / 1 minutes
- [ ] protection items craftable and working
- [ ] `/anomaly start acid_rain` damages unprotected players and spares protected ones
- [ ] forecast reaches `#announcements` and is published

## Bounty

- [ ] every online player has a contract (`/bounty`)
- [ ] killing the contract pays a heart and rerolls
- [ ] leaving the server rerolls contracts pointing at the leaver
- [ ] bounty night doubles the payout
- [ ] 10 minute warning fires once per bounty day

## Discord

- [ ] bot online, `#minecraft-chat` bridges both ways
- [ ] `#announcements` messages are published (announcement channel, bot owns them)
- [ ] `#events` receives event and donation posts
- [ ] `#server-info` readable
- [ ] `#store` exists for donation questions
- [ ] store link added to `#announcements` topic and to the poster

Permissions the bot still needs for full coverage: `Manage Messages` (delete other
messages, publish webhook posts) and `Manage Channels` (DiscordSRV topic updater).
Re-invite with:

```
https://discord.com/oauth2/authorize?client_id=1573444878065602570&permissions=537259088&integration_type=0&scope=bot+applications.commands
```

## World

- [ ] pregen finished (`/chunky` reports no tasks)
- [ ] world border at the intended size, not shrinking by accident
- [ ] Nether and End locked behind quests (`galeheart.nether`, `galeheart.end`)
- [ ] spawn region protected, rest of the world open
- [ ] CoreProtect logging, a rollback tested once

## Ops

- [ ] backup taken after the final config change
- [ ] restart tested and the server came back clean
- [ ] no ERROR or Exception lines in `logs/latest.log`
- [ ] panel API key rotated, new key not committed
- [ ] Discord bot token not committed
- [ ] `docs/promo-post.md` Discord invite filled in
- [ ] `store/index.html` invite filled in, prices match `docs/donations.md`

## Post-launch, first hour

- [ ] watch `latest.log` for exceptions during the first joins
- [ ] watch TPS (`/tps`) with 10+ players
- [ ] answer every Discord question fast, activity keeps the advert visible
- [ ] take one screenshot of a full spawn for the next post
