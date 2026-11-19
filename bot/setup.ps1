# Requires: Python 3.10+, PowerShell 5+
# Asks for the bot token and channel ID, validates both, writes config.json,
# then starts the bot.

param(
    [string]$Token,
    [string]$ChannelId
)

$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$configPath = Join-Path $here 'config.json'

Write-Host 'GaleHeart bot setup' -ForegroundColor Cyan
Write-Host 'The bot must already be invited to the server (see docs/discord.md).' -ForegroundColor DarkGray

if (-not $Token) {
    $Token = Read-Host 'Bot token'
}
if (-not $ChannelId) {
    $ChannelId = Read-Host 'Events channel ID (right-click channel -> Copy Channel ID)'
}

$headers = @{ Authorization = "Bot $Token" }

Write-Host 'Checking the token...'
try {
    $me = Invoke-RestMethod -Uri 'https://discord.com/api/v10/users/@me' -Headers $headers -TimeoutSec 15
    Write-Host ("  token OK: {0}#{1}" -f $me.username, $me.discriminator) -ForegroundColor Green
} catch {
    Write-Host '  token is invalid or Discord is unreachable' -ForegroundColor Red
    exit 1
}

Write-Host 'Checking the channel...'
try {
    $channel = Invoke-RestMethod -Uri "https://discord.com/api/v10/channels/$ChannelId" -Headers $headers -TimeoutSec 15
    Write-Host ("  channel OK: #{0}" -f $channel.name) -ForegroundColor Green
} catch {
    Write-Host '  channel not found, or the bot cannot see it' -ForegroundColor Red
    Write-Host '  invite the bot and give it View Channel + Send Messages' -ForegroundColor DarkGray
    exit 1
}

$config = @{
    token      = $Token
    channel_id = "$ChannelId"
    host       = '127.0.0.1'
    port       = 8099
    secret     = ''
} | ConvertTo-Json

[System.IO.File]::WriteAllText($configPath, $config, (New-Object System.Text.UTF8Encoding($false)))
Write-Host '  config.json written' -ForegroundColor Green

Write-Host ''
Write-Host 'Sending a test message...'
$body = @{
    embeds = @(@{
        title       = 'GaleHeart is online'
        description = 'Event notifications are wired up.'
        color       = 3447003
    })
} | ConvertTo-Json -Depth 5

try {
    Invoke-RestMethod -Uri "https://discord.com/api/v10/channels/$ChannelId/messages" `
        -Method Post -Headers $headers -ContentType 'application/json' `
        -Body ([System.Text.Encoding]::UTF8.GetBytes($body)) -TimeoutSec 15 | Out-Null
    Write-Host '  test message sent' -ForegroundColor Green
} catch {
    Write-Host '  could not post: check Send Messages / Embed Links permissions' -ForegroundColor Red
}

Write-Host ''
Write-Host 'Starting the bot...' -ForegroundColor Cyan
python (Join-Path $here 'bot.py')
