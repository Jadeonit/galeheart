package ru.galeheart.core.chat;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import ru.galeheart.core.GaleHeartCore;
import ru.galeheart.core.util.Msg;

/** Applies the chat guard before the message is broadcast. */
public final class ChatListener implements Listener {

    private final GaleHeartCore plugin;

    public ChatListener(GaleHeartCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onChat(AsyncChatEvent event) {
        String text = PlainTextComponentSerializer.plainText().serialize(event.message());
        ChatManager.Result result = plugin.chat().check(event.getPlayer(), text);
        switch (result.verdict()) {
            case BLOCK -> {
                event.setCancelled(true);
                event.getPlayer().sendMessage(Msg.parse(plugin.prefix() + result.reason()));
            }
            case LOWERCASE -> event.message(Msg.parse(text.toLowerCase()));
            case ALLOW -> {
            }
        }
    }
}
