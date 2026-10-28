package dev.nightbeam.donutessentials.dialog;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.menu.Dialogs;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.entity.Player;

/**
 * Native dialogs (Minecraft 1.21.7+): a text box instead of typing in chat, and the rules screen. Compiled against
 * the 1.21.11 API and loaded by {@code DialogSupport} only when the server has Paper's dialog API.
 */
public final class PaperDialogs implements Dialogs {

    private static final ClickCallback.Options ONCE = ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(30)).build();

    private final DonutEssentialsPlugin plugin;

    public PaperDialogs(DonutEssentialsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void text(Player player, Component title, Component label, String initial, int maxLength, Consumer<String> answer, Runnable cancelled) {
        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(title)
                        .canCloseWithEscape(true)
                        .body(List.of(DialogBody.plainMessage(label, 300)))
                        .inputs(List.of(DialogInput.text("value", plugin.messages().component("dialogs.text-field"))
                                .initial(initial == null ? "" : initial)
                                .maxLength(Math.max(1, maxLength))
                                .width(300)
                                .build()))
                        .build())
                .type(DialogType.confirmation(
                        button(plugin.messages().component("dialogs.submit"), DialogAction.customClick((view, audience) -> {
                            String value = view.getText("value");
                            if (value == null || value.isBlank()) {
                                cancelled.run();
                            } else {
                                answer.accept(value);
                            }
                        }, ONCE)),
                        button(plugin.messages().component("dialogs.cancel"), DialogAction.customClick((view, audience) -> cancelled.run(), ONCE)))));
        player.showDialog(dialog);
    }

    @Override
    public void notice(Player player, Component title, List<Component> body, Component yes, Component no, Runnable onYes, Runnable onNo) {
        List<DialogBody> lines = new ArrayList<>();
        for (Component line : body) {
            lines.add(DialogBody.plainMessage(line, 360));
        }
        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(title)
                        .canCloseWithEscape(false)
                        .body(lines)
                        .build())
                .type(DialogType.confirmation(
                        button(yes, DialogAction.customClick((view, audience) -> onYes.run(), ONCE)),
                        button(no, DialogAction.customClick((view, audience) -> onNo.run(), ONCE)))));
        player.showDialog(dialog);
    }

    private ActionButton button(Component label, DialogAction action) {
        return ActionButton.create(label, null, 140, action);
    }
}
