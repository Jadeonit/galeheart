# Donations

Cosmetics only. Nothing sold here changes damage, health, reach, loot or access.
Hearts are earned in game, never bought.

## Ranks

| Rank | Price | LuckPerms group | What it gives |
|---|---|---|---|
| Supporter | $5 | `supporter` | `[Supporter]` chat prefix, Supporter role in Discord, name on the wall of thanks |
| Patron | $15 | `patron` | `[Patron]` prefix, everything from Supporter, three cosmetic titles, particle trail on join |
| Season Pass | $7 | `pass` | `[Pass]` prefix, the paid cosmetic track for one season (levels are earned, not granted) |

Prices are set in `store/index.html`. Change them there and in this table together.

## What is not for sale

- hearts, max hearts, regeneration, resistance
- kits, spawners, gear, enchantments
- claim protection, teleports, /home, /back
- whitelist bypass, staff rank, ban appeal

If a perk would touch combat or progression, it does not exist.

## Delivery

Payments are manual for now. There is no automated checkout, so the flow is:

1. Player asks in `#store` on Discord.
2. Player states the rank and their in-game name.
3. You confirm payment (bank transfer, crypto, PayPal - whatever you accept).
4. You run one command in game or in the panel console:

```
/grant <player> <rank>
```

Examples:

```
/grant Jadeonit supporter
/grant Jadeonit patron
/grant Jadeonit pass
```

`/grant` adds the LuckPerms group, prints a confirmation and posts a `Donation`
embed to `#announcements` (published) and `#events`.

To revoke:

```
/lp user <player> parent remove supporter
```

## Groups that exist

Created on the server already:

```
supporter   weight 5   prefix &6[Supporter]
patron      weight 6   prefix &b[Patron]
pass        weight 4   prefix &d[Pass]
```

Staff groups are `mod` and `admin`. Do not hand out `admin` for money.

## Refunds

State one policy and keep it: refund within 14 days if the rank was never used,
no refund after the cosmetic was applied. Chargebacks get the rank removed and a
ban on further purchases, not a server ban.

## EULA notes

- Donations are voluntary support, not a purchase of the server.
- Nothing sold affects gameplay outcomes, so the Mojang commercial guidelines are
  satisfied: no paying to win, no paid access to the base game.
- The server is not affiliated with Mojang or Microsoft. Say so on the store page.
- Keep the plugin list, world and backups owned by you; a donation is not equity.

## Automated payments later

Three routes, in order of effort:

### Tebex

Hosted checkout, handles tax, gives a plugin that runs commands on payment.
`/grant` becomes the fallback for manual cases.

```
store.tebex.io -> create package -> command: grant {username} supporter
```

### DonationAlerts / StreamElements

Good for streams and one-off tips. Their webhook can call a small relay that runs
`grant <nick> supporter` through the panel API. The relay is the same shape as
`bot/` in this repo: verify the webhook signature, map amount to rank, call the
console endpoint.

### Stripe / crypto direct

Most work, most control. You need an entity or a payment processor account, a
webhook endpoint and idempotency keys so a retried webhook does not double-grant.
Store `payment_id -> player` so a refund can be traced.

## Housekeeping

- The panel API key in `deploy/README.md` can run any console command. Rotate it
  after setup and never commit a live one.
- The Discord bot token in the server config can post as the bot. Treat it the
  same way.
- Keep a plain text ledger: date, player, rank, amount, payment reference.
