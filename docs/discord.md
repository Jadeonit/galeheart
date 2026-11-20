# GaleHeart — Discord setup

The bot itself is in `bot/`. This file is the server-side plan: channels,
roles and what to do before the first event.

## 1. Bot application

1. <https://discord.com/developers/applications> → **New Application** → name `GaleHeart`.
2. **Bot** → **Reset Token** → copy the token (it is shown once).
3. **OAuth2 → URL Generator**:
   - scopes: `bot`
   - permissions: `View Channels`, `Send Messages`, `Embed Links`, `Read Message History`
   - open the generated URL and pick the server.

   Raw permissions integer for the URL: `84992`.

4. In the server: **Server Settings → Integrations → GaleHeart** and make sure it
   can see the events channel.

## 2. Channels

| Channel | Purpose | Who writes |
|---|---|---|
| `#announcements` | season dates, wipes, downtime | staff |
| `#events` | bot output: forecasts, anomalies, finale, quests | bot |
| `#chat` | general talk | everyone |
| `#clips` | TikTok/YouTube posts, best moments | everyone |
| `#support` | questions, reports | everyone |
| `#rules` | the short version | staff only |

The bot only needs `#events`.

## 3. Roles

| Role | Colour | Key permissions | Who |
|---|---|---|---|
| **Owner** | red | Administrator | you |
| **Admin** | orange | Manage Server, Ban, Kick | 1–2 people |
| **Moderator** | blue | Kick, Mute, Manage Messages, View Logs | 2–4 people |
| **Builder** | green | nothing special, `#staff` access | builders |
| **Content** | purple | `#clips` pinning, `#events` mention ping | video people |
| **Partner** | teal | own colour, `#partners` | allied servers |
| **Player** | — | default role after verification | everyone |
| **Muted** | grey | no send in `#chat` | punished |

Hierarchy top to bottom: Owner → Admin → Moderator → Builder → Content → Partner → Player → Muted.
Discord applies permissions by position, so keep the order.

## 4. Onboarding

1. `#rules` — 5 lines, not 20.
2. Verification gate (Wick or AltDentifier) before `Player` is granted:
   it blocks raid bots and keeps the member count honest.
3. `#announcements` — pin the season start date and the store link.
4. Nickname rule: Minecraft name, so people can be found in game.

## 5. Webhook (no hosting, recommended)

1. Channel → **Edit Channel → Integrations → Webhooks → New Webhook**.
2. **Copy Webhook URL**.
3. In `plugins/GaleHeartCore/config.yml` and `plugins/AnomalousWeather/config.yml`:

```yaml
discord:
  enabled: true
  webhook: "https://discord.com/api/webhooks/..."
  name: "GaleHeart"          # shown as the author
  avatar: ""                 # public image URL, or leave empty
```

4. Restart the server and run `/season announce test` in game — the message
   should appear in the channel within a second.

No bot, no token, no process. The webhook posts as its own identity, not as
your account.

## 6. Run the bot (optional)

Only needed for commands like `/forecast`. Skip it if the webhook is enough.

## 6. What the bot posts

| Event | When |
|---|---|
| Weather forecast | one hour before an anomaly |
| Anomaly started | on start |
| Anomaly ended | on end |
| Season finale | when the finale starts |
| Finale over | when hearts are restored |
| Season reset | after a wipe |
| Quest complete | when a player unlocks a dimension |

Events come from the plugins over `http://127.0.0.1:8099/event`; the bot does
not need to touch Minecraft directly.
