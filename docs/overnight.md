# Overnight report

Night of 2026-11-20 → 21. Everything below was done without you. Anything blocked
is listed with the exact thing that unblocks it.

## Shipped

### Bounty is now a personal contract

Every player carries their own target. Killing it pays a heart and rerolls the
contract to a random online player. Leaving players are rerolled out of other
people's contracts. Three random days a week the contracted payout doubles.

- `BountyManager.java` rewritten: `ensureTarget`, `assign`, `handleDeath`,
  `onQuit`, window with multiplier and bossbar.
- Contracts are assigned on join, re-checked every 10 seconds while online, and
  stored in `data.yml` (`bounty-target`, `bounty-target-name`).
- `/bounty` shows your current contract.
- Verified in game: `TestA completed the contract on TestB. +1 heart`, new
  contract message followed.

### Announcement posts are published automatically

The bot-owned message route works without `Manage Messages`, so announcement
followers now get the push.

- Notifier rewritten in both plugins: bot token first, then webhook, then the old
  relay. `crosspost` publishes the message after posting.
- Verified in Discord: `flags=1 published=True` on `#announcements` for weather and
  core events.
- Weather alerts now go to `#announcements`. Event posts go to `#announcements`
  (published) and `#events` (webhook, because the bot has no access there).
- Test messages wiped from both channels. A launch post is pinned in
  `#announcements`.

### Donations

- LuckPerms groups created: `supporter` (weight 5, `&6[Supporter]`), `patron`
  (weight 6, `&b[Patron]`), `pass` (weight 4, `&d[Pass]`).
- `/grant <player> <rank>` verified end to end: group applied, `Donation` embed
  posted and published.
- Store page rewritten: three cards, "how it works", "never for sale", EULA note,
  refund policy. Ranks and prices match `docs/donations.md`.
- `docs/donations.md` covers manual delivery, refunds, EULA position and the three
  automated routes (Tebex, DonationAlerts, Stripe/crypto).

### Promo

| File | What it is |
|---|---|
| `promo/poster.png` | 1920×1080 poster |
| `promo/square.png` | 1080×1080 crop for feeds |
| `promo/story.png` | 1080×1920 story frame |
| `promo/teaser.gif` | 3 s logo loop |
| `promo/vertical.mp4` | 12 s 9:16 opener — hook, features, address |
| `promo/alert.mp4` | 8 s 9:16 weather alert card |
| `promo/bounty.mp4` | 6 s 9:16 bounty explainer |
| `promo/square.mp4` | 6 s 1:1 poster push |
| `promo/build_*.py` | generators, edit text and re-run |

All rendered at 24 fps, H.264, silent. Vertical ones are TikTok/Reels/Shorts
ready; add music in the app.

`docs/promo-post.md` has the three post lengths and posting guidance.

### Docs

- `docs/donations.md` — ranks, prices, groups, delivery, refunds, EULA, automation
- `docs/commands.md` — every player and admin command
- `docs/launch.md` — pre-open checklist
- `docs/discord.md` — rewritten for the current bot-first transport
- `docs/promo-post.md` — copy-paste ad text

### Cleanup

- Player store cleaned: stale premium-UUID `Jadeonit`, `TestA`, `TestB`, `CineBot`
  removed. Leaderboard is now `Jadeonit 10`, `Boar 1`.
- Keepalive bots stopped. `gh-bots/` still holds the scripts if you want them back.
- Secrets removed from repo configs. `bot-token` and `webhook` are empty in git and
  filled only in the deploy copies outside the repo.

## Verified in game

- contract payout and reroll
- weather alert posted to `#announcements` and published
- `/season announce` reaches both channels
- `/grant` applies the group and posts the donation embed
- `latest.log` has no ERROR or Exception lines after the last restart
- server online, 60 slots, Paper 1.21.11

## Blocked on you

1. **Bot access to `#events` and `#server-info`.** Both return 403 Missing
   Permissions. Grant the bot role View Channel + Send Messages there, or accept
   webhook posts in `#events`. Invite with the full set:
   `https://discord.com/oauth2/authorize?client_id=1573444878065602570&permissions=537259088&integration_type=0&scope=bot+applications.commands`
2. **Payment processing.** No processor, no automated checkout. Manual `/grant` is
   the working path today.
3. **Discord invite links.** `store/index.html` and `docs/promo-post.md` still say
   `https://discord.gg/your-invite`. Replace both before posting.
4. **Rank prices.** Defaults are Supporter $5, Patron $15, Season Pass $7. Change
   them in the store page and in `docs/donations.md`.
5. **Gameplay footage.** No capture path without a client. The clips above are
   generated; if you drop raw OBS clips into `clips/`, they can be cut into a real
   trailer.
6. **Panel API key.** Rotate it; it can run any console command.

## Morning test list

```
/hearts
/bounty
/hearts withdraw 1
/top
/pass
/quest
/season
/anomaly status
/anomaly list
/season announce morning test
/anomaly start acid_rain
/anomaly stop
/grant <your nick> supporter
```

Then open the server to players and watch `latest.log` for the first hour.

## Known cosmetic issues

- DiscordSRV warns that it cannot set the `#minecraft-chat` topic: needs Manage
  Channels.
- Four old bridge messages in `#minecraft-chat` were posted by the previous bot
  identity and cannot be deleted without Manage Messages.
- `#events` posts are not published (webhook path). They are visible, just no ping.
