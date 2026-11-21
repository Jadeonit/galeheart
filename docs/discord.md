# GaleHeart — Discord setup

Current state: the Discord bot `GaleHeart Bridge` is connected, DiscordSRV bridges
`#minecraft-chat`, and the plugins post to `#announcements` and `#events`.

## 1. Bot application

1. <https://discord.com/developers/applications> → **New Application** → `GaleHeart`.
2. **Bot** → **Reset Token** → copy it (shown once).
3. **OAuth2 → URL Generator**:
   - scopes: `bot`, `applications.commands`
   - permissions: View Channels, Send Messages, Embed Links, Attach Files,
     Read Message History, Add Reactions, Use External Emojis, Manage Webhooks,
     Manage Messages, Manage Channels

   Raw integer: `537259088`.

```
https://discord.com/oauth2/authorize?client_id=1573444878065602570&permissions=537259088&integration_type=0&scope=bot+applications.commands
```

4. Make sure the bot role sits above `@everyone` and can see both announcement
   channels.

## 2. Channels

| Channel | Type | Purpose |
|---|---|---|
| `#server-info` | text | rules, IP, version, links |
| `#announcements` | announcement | season news, weather alerts, donations — published |
| `#events` | announcement | bounty windows, finale, event log |
| `#minecraft-chat` | text | DiscordSRV bridge both ways |
| `#main-chat` | text | general |
| `#your-suggests` | text | ideas |
| `#support` | text | donation tickets, reports (create it) |

`#announcements` works with the bot alone. `#events` currently receives posts
through its webhook because the bot lacks channel access there; either grant the
bot `View Channel` + `Send Messages` in `#events` or keep the webhook.

## 3. How the plugins post

Both `GaleHeartCore` and `AnomalousWeather` share the same notifier with three
transports, tried in this order:

1. **Bot token + channel id** — the message is owned by the bot, so the plugin can
   call `/channels/{id}/messages/{id}/crosspost` and publish it. No Manage Messages
   needed. This is what produces the "published" flag.
2. **Channel webhook** — posts as the webhook identity. Looks clean, but the bot
   cannot publish someone else's message, so followers get no push.
3. **Local relay** — `http://127.0.0.1:8099/event`, the original bot process. Only
   used when neither of the above is configured.

Core posts to both the bot channel and the webhook when both are set, which is why
an event shows up in `#announcements` (published) and in `#events` (webhook).

Config keys:

```yaml
discord:
  enabled: true
  bot-token: ""        # bot token, never commit it
  channel-id: ""       # bot destination, announcement channel
  webhook: ""          # optional fallback
  crosspost: true      # publish after posting
  name: "GaleHeart"
  avatar: ""           # public image URL
  endpoint: "http://127.0.0.1:8099/event"
  secret: ""
```

The repo keeps these empty on purpose. `deploy_cfg/` (outside the repo) holds the
real values that get uploaded to the server.

## 4. What gets posted

| Event | Channel | When |
|---|---|---|
| Weather forecast | `#announcements` | one hour before an anomaly |
| Anomaly started | `#announcements` | on start |
| Anomaly ended | `#announcements` | on end |
| Bounty in 10 minutes | both | 10 min before the window |
| Bounty window | both | on start |
| Bounty closed | both | on end |
| Season finale | both | finale start and stop |
| Season reset | both | after a wipe |
| Donation | both | `/grant` |
| Announcement | both | `/season announce <text>` |

## 5. Permissions the bot still wants

| Permission | Why |
|---|---|
| Manage Messages | delete other people's messages, publish webhook posts |
| Manage Channels | DiscordSRV channel topic updater |
| View Channel + Send Messages in `#events` | post directly instead of webhook |

Until then: the DiscordSRV warning about the `#minecraft-chat` topic is cosmetic,
and four old bridge messages in `#minecraft-chat` cannot be deleted by the bot.

## 6. Roles

| Role | Colour | Key permissions | Who |
|---|---|---|---|
| Owner | red | Administrator | you |
| Admin | orange | Manage Server, Ban, Kick | 1–2 people |
| Moderator | blue | Kick, Mute, Manage Messages, View Logs | 2–4 people |
| Builder | green | `#staff` access | builders |
| Content | purple | `#clips` pinning | video people |
| Partner | teal | own colour | allied servers |
| Player | — | default | everyone |
| Muted | grey | no send in `#chat` | punished |

Order top to bottom: Owner → Admin → Moderator → Builder → Content → Partner →
Player → Muted.

## 7. Onboarding

1. `#server-info` — IP, version, the three rules, store link.
2. Verification gate (Wick or AltDentifier) before the Player role: blocks raids.
3. `#announcements` — pinned season date and store page.
4. Nickname = Minecraft name.

## 8. Testing

```
/season announce hello from the console
/anomaly start acid_rain
/grant <player> supporter
```

Each should land in `#announcements` with the published arrow, and the event ones
also in `#events`. If a message appears but is not published, the bot is posting
through the webhook path instead of the bot path — check `channel-id`, `bot-token`
and that the channel is an announcement channel.
