package com.crownscoins.client;

import com.crownscoins.kingdom.Kingdom;
import com.crownscoins.kingdom.KingdomCrest;
import com.crownscoins.kingdom.Symbol;
import com.crownscoins.menu.KingdomCreationMenu;
import com.crownscoins.network.CreateKingdomPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Client-only draft form for a new kingdom.
 *
 * <p>The client submits only a bounded draft through the active menu. The server
 * independently validates and persists all kingdom data.</p>
 */
public final class KingdomCreationScreen extends AbstractContainerScreen<KingdomCreationMenu> {
    private static final int FIELD_WIDTH = 190;
    private static final int FIELD_HEIGHT = 20;
    private static final int SCREEN_HEIGHT = 380;

    private EditBox kingdomName;
    private EditBox currencyName;
    private EditBox ironValue;
    private EditBox copperValue;
    private EditBox goldValue;
    private final List<Button> crestButtons = new ArrayList<>();
    private Button createButton;
    private KingdomCrest selectedCrest = KingdomCrest.CROWNED_LION;
    private Component status = Component.empty();

    public KingdomCreationScreen(KingdomCreationMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, FIELD_WIDTH + 20, SCREEN_HEIGHT);
    }

    @Override
    protected void init() {
        super.init();
        int left = this.leftPos + 10;
        int top = this.topPos;

        Component kingdomNameLabel = gui("kingdom_name");
        this.kingdomName = this.addRenderableWidget(new EditBox(this.font, left, top + 18, FIELD_WIDTH, FIELD_HEIGHT, kingdomNameLabel));
        this.kingdomName.setMaxLength(Kingdom.MAX_KINGDOM_NAME_LENGTH);
        this.kingdomName.setHint(kingdomNameLabel);
        this.kingdomName.setResponder(value -> this.refreshCreateButton());

        Component currencyNameLabel = gui("currency_name");
        this.currencyName = this.addRenderableWidget(new EditBox(this.font, left, top + 58, FIELD_WIDTH, FIELD_HEIGHT, currencyNameLabel));
        this.currencyName.setMaxLength(Kingdom.MAX_CURRENCY_NAME_LENGTH);
        this.currencyName.setHint(currencyNameLabel);
        this.currencyName.setResponder(value -> this.refreshCreateButton());

        this.ironValue = this.addRenderableWidget(numberField(left, top + 98, "iron_value", "1"));
        this.copperValue = this.addRenderableWidget(numberField(left, top + 138, "copper_value", "1"));
        this.goldValue = this.addRenderableWidget(numberField(left, top + 178, "gold_value", "1"));

        this.crestButtons.clear();
        KingdomCrest[] crests = KingdomCrest.values();
        int crestTop = top + 218;
        for (int index = 0; index < crests.length; index++) {
            KingdomCrest crest = crests[index];
            int x = left + (index % 5) * 38;
            int y = crestTop + (index / 5) * 21;
            Button button = this.addRenderableWidget(Button.builder(Component.empty(), ignored -> selectCrest(crest))
                .bounds(x, y, 36, 20)
                .build());
            this.crestButtons.add(button);
        }
        this.refreshCrestButtons();

        this.createButton = this.addRenderableWidget(Button.builder(gui("create_kingdom"), button -> submit())
            .bounds(left, top + 332, 92, 20)
            .build());
        this.addRenderableWidget(Button.builder(gui("cancel"), button -> this.onClose())
            .bounds(left + 98, top + 332, 92, 20)
            .build());
        this.refreshCreateButton();
        this.setInitialFocus(this.kingdomName);
    }

    private EditBox numberField(int x, int y, String translationKey, String initialValue) {
        Component label = gui(translationKey);
        EditBox field = new EditBox(this.font, x, y, FIELD_WIDTH, FIELD_HEIGHT, label);
        field.setMaxLength(7);
        field.setValue(initialValue);
        field.setHint(label);
        field.setResponder(value -> this.refreshCreateButton());
        return field;
    }

    private void selectCrest(KingdomCrest crest) {
        this.selectedCrest = crest;
        this.status = Component.empty();
        this.refreshCrestButtons();
    }

    private void refreshCrestButtons() {
        KingdomCrest[] crests = KingdomCrest.values();
        for (int index = 0; index < this.crestButtons.size(); index++) {
            KingdomCrest crest = crests[index];
            String marker = crest == this.selectedCrest ? ">" : "";
            this.crestButtons.get(index).setMessage(Component.literal(marker + shortLabel(crest)));
        }
    }

    private void refreshCreateButton() {
        if (this.createButton != null) {
            this.createButton.active = this.isLocallyValid();
        }
    }

    private boolean isLocallyValid() {
        return validLength(this.kingdomName, Kingdom.MIN_KINGDOM_NAME_LENGTH, Kingdom.MAX_KINGDOM_NAME_LENGTH)
            && validLength(this.currencyName, Kingdom.MIN_CURRENCY_NAME_LENGTH, Kingdom.MAX_CURRENCY_NAME_LENGTH)
            && validCoinValue(this.ironValue)
            && validCoinValue(this.copperValue)
            && validCoinValue(this.goldValue);
    }

    private static boolean validLength(EditBox field, int minimum, int maximum) {
        if (field == null) {
            return false;
        }
        int length = field.getValue().strip().codePointCount(0, field.getValue().strip().length());
        return length >= minimum && length <= maximum;
    }

    private static boolean validCoinValue(EditBox field) {
        if (field == null || field.getValue().isEmpty()) {
            return false;
        }
        try {
            int value = Integer.parseInt(field.getValue());
            return value >= Kingdom.MIN_COIN_VALUE && value <= Kingdom.MAX_COIN_VALUE;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private void submit() {
        if (!this.isLocallyValid()) {
            this.status = gui("check_details");
            return;
        }

        KingdomDraft draft = new KingdomDraft(
            this.kingdomName.getValue().strip(),
            this.currencyName.getValue().strip(),
            this.selectedCrest,
            Integer.parseInt(this.ironValue.getValue()),
            Integer.parseInt(this.copperValue.getValue()),
            Integer.parseInt(this.goldValue.getValue())
        );
        ClientPacketDistributor.sendToServer(new CreateKingdomPayload(
            this.menu.containerId,
            draft.kingdomName(),
            draft.currencyName(),
            draft.crest().id(),
            draft.ironValue(),
            draft.copperValue(),
            draft.goldValue()
        ));
        this.createButton.active = false;
        this.status = gui("creation_sent");
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int left = this.leftPos;
        int top = this.topPos;
        graphics.fill(left, top, left + FIELD_WIDTH + 20, top + SCREEN_HEIGHT, 0xD0181A20);
        graphics.outline(left, top, FIELD_WIDTH + 20, SCREEN_HEIGHT, 0xFFB89445);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(this.font, this.title, this.width / 2, top + 8, 0xFFFFD878);
        graphics.centeredText(this.font, gui("choose_crest"), this.width / 2, top + 202, 0xFFB8B8B8);
        graphics.centeredText(this.font, this.status, this.width / 2, top + 360, 0xFFFFD878);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static Component gui(String key, Object... arguments) {
        return Component.translatable("gui.crownscoins." + key, arguments);
    }

    private static String shortLabel(KingdomCrest crest) {
        String name = Component.translatable(crest.translationKey()).getString();
        return name.substring(0, Math.min(4, name.length()));
    }

    /** Bounded client draft; the server revalidates every field before persistence. */
    public record KingdomDraft(
        String kingdomName,
        String currencyName,
        KingdomCrest crest,
        int ironValue,
        int copperValue,
        int goldValue
    ) { }
}
