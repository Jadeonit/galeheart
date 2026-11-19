# GaleHeart event bot

Posts server events (weather anomalies, season finale, quest completions)
to a Discord channel. The Minecraft plugins send JSON to a local HTTP
endpoint; the bot forwards it to Discord. Stdlib only — no `pip install`.

## 1. Create the bot

1. <https://discord.com/developers/applications> → **New Application**.
2. **Bot** → **Reset Token** → copy the token.
3. **OAuth2 → URL Generator**: scopes `bot`, permissions `Send Messages`,
   `Embed Links`. Open the generated URL and invite the bot to your server.

## 2. Get the channel ID

Discord → **User Settings → Advanced → Developer Mode** on.
Right-click the channel → **Copy Channel ID**.

## 3. Run

```bash
cp config.example.json config.json
# paste token and channel_id into config.json
python bot.py
```

The bot listens on `http://127.0.0.1:8099/event`.

## 4. Point the plugins at it

In `plugins/GaleHeartCore/config.yml` and `plugins/AnomalousWeather/config.yml`:

```yaml
discord:
  enabled: true
  endpoint: "http://127.0.0.1:8099/event"
  secret: ""
```

Restart the server. Weather forecasts, anomaly starts, finale and quest
completions will appear in the channel.

## Test it

```bash
curl -X POST http://127.0.0.1:8099/event \
  -H "Content-Type: application/json" \
  -d "{\"title\":\"Test\",\"description\":\"Bot is alive\",\"color\":3447003}"
```

## Run as a service

Windows: `pythonw bot.py` in a scheduled task at logon.
Linux: a `systemd` unit with `Restart=always`.
