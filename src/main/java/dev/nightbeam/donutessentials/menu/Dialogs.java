package dev.nightbeam.donutessentials.menu;

import java.util.List;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/** Native dialogs (Minecraft 1.21.7+). Present only on servers whose API has them; see {@code DialogSupport}. */
public interface Dialogs {

    /** One line of text. {@code answer} gets the trimmed text; {@code cancelled} runs on cancel or an empty answer. */
    void text(Player player, Component title, Component label, String initial, int maxLength, Consumer<String> answer, Runnable cancelled);

    /** A notice the player must answer: the rules screen. Escape does not close it. */
    void notice(Player player, Component title, List<Component> body, Component yes, Component no, Runnable onYes, Runnable onNo);
}
