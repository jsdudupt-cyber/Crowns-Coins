package com.crownscoins.client;

import com.crownscoins.kingdom.Kingdom;
import com.crownscoins.kingdom.KingdomCrest;
import com.crownscoins.menu.KingdomCreationMenu;
import com.crownscoins.network.CreateKingdomPayload;
import net.minecraft.client.input.KeyEvent;
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
    private static final int FIELD_WIDTH = 220;
    private static final int FIELD_HEIGHT = 20;
    private static final int SCREEN_HEIGHT = 116;

    private EditBox kingdomName;
    private Button createButton;
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
        this.kingdomName = this.addRenderableWidget(new EditBox(this.font, left, top + 43, FIELD_WIDTH, FIELD_HEIGHT, kingdomNameLabel));
        this.kingdomName.setMaxLength(Kingdom.MAX_KINGDOM_NAME_LENGTH);
        this.kingdomName.setHint(kingdomNameLabel);
        this.kingdomName.setResponder(value -> this.refreshCreateButton());

        this.createButton = this.addRenderableWidget(Button.builder(gui("create_kingdom"), button -> submit())
            .bounds(left + 25, top + 76, 82, 20)
            .build());
        this.addRenderableWidget(Button.builder(gui("cancel"), button -> this.onClose())
            .bounds(left + 113, top + 76, 82, 20)
            .build());
        this.refreshCreateButton();
        this.setInitialFocus(this.kingdomName);
    }

    private void refreshCreateButton() {
        if (this.createButton != null) {
            this.createButton.active = this.isLocallyValid();
        }
    }

    private boolean isLocallyValid() {
        return validLength(this.kingdomName, Kingdom.MIN_KINGDOM_NAME_LENGTH, Kingdom.MAX_KINGDOM_NAME_LENGTH);
    }

    private static boolean validLength(EditBox field, int minimum, int maximum) {
        if (field == null) {
            return false;
        }
        int length = field.getValue().strip().codePointCount(0, field.getValue().strip().length());
        return length >= minimum && length <= maximum;
    }

    private void submit() {
        if (!this.isLocallyValid()) {
            this.status = gui("check_details");
            return;
        }

        KingdomDraft draft = new KingdomDraft(
            this.kingdomName.getValue().strip(),
            defaultCurrencyName(this.kingdomName.getValue()),
            Kingdom.IRON_COIN_VALUE,
            Kingdom.COPPER_COIN_VALUE,
            Kingdom.GOLD_COIN_VALUE
        );
        ClientPacketDistributor.sendToServer(new CreateKingdomPayload(
            this.menu.containerId,
            draft.kingdomName(),
            draft.currencyName(),
            KingdomCrest.ROYAL_CROWN.id(),
            draft.ironValue(),
            draft.copperValue(),
            draft.goldValue()
        ));
        this.createButton.active = false;
        this.status = gui("creation_sent");
    }

    /**
     * Keep game key bindings from running while either text field is focused.
     * In particular, typing E must add the letter rather than close this menu
     * through Minecraft's inventory binding.
     */
    @Override
    public boolean keyPressed(KeyEvent event) {
        EditBox focusedField = focusedTextField();
        if (focusedField != null) {
            if (focusedField.keyPressed(event)) {
                return true;
            }
            if (!event.isEscape()) {
                return true;
            }
        }
        return super.keyPressed(event);
    }

    private EditBox focusedTextField() {
        if (this.kingdomName != null && this.kingdomName.isFocused()) {
            return this.kingdomName;
        }
        return null;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int left = this.leftPos;
        int top = this.topPos;
        graphics.fill(left, top, left + FIELD_WIDTH + 20, top + SCREEN_HEIGHT, 0xE0181A20);
        graphics.outline(left, top, FIELD_WIDTH + 20, SCREEN_HEIGHT, 0xFFB89445);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(this.font, this.title, this.width / 2, top + 8, 0xFFFFD878);
        graphics.centeredText(this.font, gui("first_kingdom_prompt"), this.width / 2, top + 26, 0xFFCED2D4);
        graphics.centeredText(this.font, this.status, this.width / 2, top + 101, 0xFFFFD878);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static Component gui(String key, Object... arguments) {
        return Component.translatable("gui.crownscoins." + key, arguments);
    }

    /** The first visit names only the realm; its starter currency is automatic. */
    private static String defaultCurrencyName(String kingdomName) {
        String prefix = "Moeda de ";
        String name = kingdomName.strip();
        int allowedNameLength = Kingdom.MAX_CURRENCY_NAME_LENGTH - prefix.codePointCount(0, prefix.length());
        if (name.codePointCount(0, name.length()) > allowedNameLength) {
            name = name.substring(0, name.offsetByCodePoints(0, allowedNameLength));
        }
        return prefix + name;
    }

    /** Bounded client draft; the server revalidates every field before persistence. */
    public record KingdomDraft(
        String kingdomName,
        String currencyName,
        int ironValue,
        int copperValue,
        int goldValue
    ) { }
}
