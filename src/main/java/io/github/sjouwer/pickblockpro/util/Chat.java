package io.github.sjouwer.pickblockpro.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.BaseText;
import net.minecraft.util.Formatting;

import static net.minecraft.text.Style.EMPTY;

public final class Chat {
    private static final MinecraftClient client = MinecraftClient.getInstance();

    private Chat() {
    }

    public static void sendMessage(BaseText message) {
        message.setStyle(EMPTY.withColor(Formatting.GREEN));
        client.player.sendMessage(message, false);
    }

    public static void sendError(BaseText message) {
        message.setStyle(EMPTY.withColor(Formatting.DARK_RED));
        client.player.sendMessage(message, false);
    }
}
