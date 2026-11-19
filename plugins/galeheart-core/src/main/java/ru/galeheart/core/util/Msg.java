package ru.galeheart.core.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/** MiniMessage with a legacy {@code &} fallback. */
public final class Msg {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private Msg() {
    }

    public static Component parse(String raw) {
        if (raw == null || raw.isEmpty()) {
            return Component.empty();
        }
        if (raw.indexOf('<') < 0 && raw.indexOf('&') >= 0) {
            return LegacyComponentSerializer.legacyAmpersand().deserialize(raw);
        }
        return MINI.deserialize(raw);
    }
}
