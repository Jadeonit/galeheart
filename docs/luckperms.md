# LuckPerms setup

Paste into the server console after LuckPerms is installed. One block, in order.

## Groups

```
lp creategroup mod
lp creategroup admin

lp group mod parent add default
lp group admin parent add mod

lp group default permission set galeheart.arena.use true
lp group default permission set anomalousweather.status true

lp group mod permission set minecraft.command.kick true
lp group mod permission set minecraft.command.ban true
lp group mod permission set minecraft.command.pardon true
lp group mod permission set bukkit.command.tps true

lp group admin permission set galeheart.admin true
lp group admin permission set galeheart.arena.admin true
lp group admin permission set anomalousweather.admin true
lp group admin permission set portalblock.admin true
lp group admin permission set galeheart.bypass.protection true
lp group admin permission set worldedit.* true
lp group admin permission set worldguard.* true
lp group admin permission set coreprotect.* true
lp group admin permission set luckperms.* true
lp group admin permission set minecraft.command.gamemode true
lp group admin permission set minecraft.command.tp true
lp group admin permission set minecraft.command.give true
lp group admin permission set minecraft.command.time true
lp group admin permission set minecraft.command.weather true
lp group admin permission set minecraft.command.difficulty true
```

## Prefixes for TAB / chat

```
lp group mod meta setprefix 90 "&9[Mod] "
lp group admin meta setprefix 100 "&c[Admin] "
lp group default meta setprefix 10 "&7"
```

## Assign yourself

```
lp user <your_name> parent add admin
```

## Do not grant these

`galeheart.nether` and `galeheart.end` are **not** LuckPerms permissions you
hand out. GaleHeartCore attaches them to a player after they finish the quest.
If you grant them to `default`, the whole quest system becomes pointless and
the dimensions open for everyone.

## If a vanilla command does not work

Paper sometimes registers a vanilla command under `bukkit.command.<name>`
instead of `minecraft.command.<name>`. Check with:

```
lp verbose on
```

run the command, read the permission node in chat, grant that exact node.

## Owner

Keep the `*` wildcard for yourself only:

```
lp user <your_name> permission set * true
```
