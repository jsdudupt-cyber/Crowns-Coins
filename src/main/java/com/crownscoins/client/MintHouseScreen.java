package com.crownscoins.client;

import com.crownscoins.CrownsCoins;
import com.crownscoins.kingdom.Kingdom;
import com.crownscoins.kingdom.KingdomCrest;
import com.crownscoins.kingdom.Symbol;
import com.crownscoins.menu.MintHouseLayout;
import com.crownscoins.menu.MintHouseMenu;
import com.crownscoins.network.MintCoinPayload;
import com.crownscoins.network.UpdateCurrencyNamePayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Client view for the Mint House Coin Forge.
 *
 * <p>Its background deliberately contains only fixed decoration. All real
 * slots still belong to {@link MintHouseMenu}, so material handling, the coin
 * chest and the player's inventory remain ordinary Minecraft interactions.</p>
 */
public final class MintHouseScreen extends AbstractContainerScreen<MintHouseMenu> {
    private static final int SCREEN_WIDTH = MintHouseLayout.SCREEN_WIDTH;
    private static final int SCREEN_HEIGHT = MintHouseLayout.SCREEN_HEIGHT;
    private static final int PREVIEW_SIZE = 48;

    private static final int PANEL_INNER = 0xE9101115;
    private static final int BORDER_DARK = 0xFF2B251D;
    private static final int BORDER = 0xFF745735;
    private static final int GOLD_DARK = 0xFF8E641B;
    private static final int GOLD = 0xFFFFD34F;
    private static final int TEXT = 0xFFD7D9D9;
    private static final int SUBTLE_TEXT = 0xFFA8A49B;
    private static final Identifier MENU_TEXTURE = Identifier.fromNamespaceAndPath(
        CrownsCoins.MOD_ID,
        "textures/gui/compact_coin_forge_workbench.png"
    );

    private final MintHouseMenu.ClientMintData display;
    private final List<MetalButton> metalButtons = new ArrayList<>();
    private Kingdom.Metal selectedMetal = Kingdom.Metal.COPPER;
    private Button confirmButton;
    private Button backButton;
    private Button mintTabButton;
    private Button currencyTabButton;
    private Button saveCurrencyButton;
    private EditBox currencyNameField;
    private boolean currencyTabOpen;
    private Component status = Component.empty();

    public MintHouseScreen(MintHouseMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, SCREEN_WIDTH, SCREEN_HEIGHT);
        this.display = menu.clientData();
        this.titleLabelX = -10_000;
        this.inventoryLabelX = -10_000;
    }

    @Override
    protected void init() {
        super.init();
        this.metalButtons.clear();

        Kingdom.Metal[] metals = {Kingdom.Metal.COPPER, Kingdom.Metal.IRON, Kingdom.Metal.GOLD};
        for (int index = 0; index < metals.length; index++) {
            Kingdom.Metal metal = metals[index];
            Button card = this.addRenderableWidget(invisible(Button.builder(Component.empty(), ignored -> selectMetal(metal))
                .bounds(
                    this.leftPos + MintHouseLayout.metalCardX(index),
                    this.topPos + MintHouseLayout.METAL_CARD_Y,
                    MintHouseLayout.METAL_CARD_WIDTH,
                    MintHouseLayout.METAL_CARD_HEIGHT
                )
                .tooltip(Tooltip.create(metalName(metal)))
                .build()));
            this.metalButtons.add(new MetalButton(card, metal));
        }

        this.confirmButton = this.addRenderableWidget(invisible(Button.builder(Component.empty(), ignored -> mint())
            .bounds(
                this.leftPos + MintHouseLayout.CONFIRM_X,
                this.topPos + MintHouseLayout.CONFIRM_Y,
                MintHouseLayout.CONFIRM_WIDTH,
                MintHouseLayout.CONFIRM_HEIGHT
            )
            .build()));
        this.backButton = this.addRenderableWidget(invisible(Button.builder(Component.empty(), ignored -> {
                if (this.currencyTabOpen) {
                    this.setCurrencyTab(false);
                } else {
                    this.onClose();
                }
            })
            .bounds(
                this.leftPos + MintHouseLayout.BACK_X,
                this.topPos + MintHouseLayout.BACK_Y,
                MintHouseLayout.BACK_WIDTH,
                MintHouseLayout.BACK_HEIGHT
            )
            .build()));

        if (this.display.canEditCurrency()) {
            this.mintTabButton = this.addRenderableWidget(invisible(Button.builder(Component.empty(), ignored -> this.setCurrencyTab(false))
                .bounds(this.leftPos + 379, this.topPos + 34, 40, 12)
                .tooltip(Tooltip.create(gui("tab_mint")))
                .build()));
            this.currencyTabButton = this.addRenderableWidget(invisible(Button.builder(Component.empty(), ignored -> this.setCurrencyTab(true))
                .bounds(this.leftPos + 422, this.topPos + 34, 38, 12)
                .tooltip(Tooltip.create(gui("tab_currency")))
                .build()));
        }

        Component currencyNameLabel = gui("currency_name");
        this.currencyNameField = this.addRenderableWidget(new EditBox(
            this.font,
            this.leftPos + 125,
            this.topPos + 104,
            230,
            18,
            currencyNameLabel
        ));
        this.currencyNameField.setMaxLength(Kingdom.MAX_CURRENCY_NAME_LENGTH);
        this.currencyNameField.setHint(currencyNameLabel);
        this.currencyNameField.setValue(this.display.currencyName());
        this.currencyNameField.setResponder(ignored -> this.refreshCurrencySaveState());
        this.saveCurrencyButton = this.addRenderableWidget(invisible(Button.builder(Component.empty(), ignored -> saveCurrencyName())
            .bounds(this.leftPos + 181, this.topPos + 128, 118, 18)
            .build()));

        this.setCurrencyTab(false);
        this.refreshSelectionState();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        this.refreshSelectionState();
    }

    private void selectMetal(Kingdom.Metal metal) {
        if (this.selectedMetal != metal) {
            this.selectedMetal = metal;
            this.status = gui("metal_changed", metalName(metal));
        }
        this.refreshSelectionState();
    }

    private void refreshSelectionState() {
        if (this.confirmButton == null) {
            return;
        }
        int quantity = this.menu.mintableCoinCountFor(this.selectedMetal);
        this.confirmButton.active = KingdomCrest.isSupported(this.display.crest()) && quantity > 0;
        if (quantity > 0) {
            this.confirmButton.setMessage(gui("mint_stack", quantity));
            this.confirmButton.setTooltip(Tooltip.create(gui("mint_stack_tooltip", quantity)));
        } else {
            this.confirmButton.setMessage(gui("confirm"));
            this.confirmButton.setTooltip(Tooltip.create(gui("mint_stack_empty")));
        }
    }

    private void refreshCurrencySaveState() {
        if (this.saveCurrencyButton != null) {
            this.saveCurrencyButton.active = this.display.canEditCurrency()
                && validCurrencyName(this.currencyNameField == null ? "" : this.currencyNameField.getValue());
        }
    }

    private void setCurrencyTab(boolean open) {
        if (open && !this.display.canEditCurrency()) {
            return;
        }
        this.currencyTabOpen = open;
        for (MetalButton button : this.metalButtons) {
            button.button().visible = !open;
        }
        if (this.confirmButton != null) {
            this.confirmButton.visible = !open;
        }
        if (this.backButton != null) {
            this.backButton.setTooltip(Tooltip.create(gui(open ? "tab_mint" : "back")));
        }
        if (this.mintTabButton != null) {
            this.mintTabButton.active = open;
        }
        if (this.currencyTabButton != null) {
            this.currencyTabButton.active = !open;
        }
        if (this.currencyNameField != null) {
            this.currencyNameField.visible = open;
            this.setFocused(open ? this.currencyNameField : null);
        }
        if (this.saveCurrencyButton != null) {
            this.saveCurrencyButton.visible = open;
        }
        this.refreshCurrencySaveState();
    }

    private void saveCurrencyName() {
        if (!this.display.canEditCurrency() || this.currencyNameField == null) {
            return;
        }
        String currencyName = this.currencyNameField.getValue().strip();
        if (!validCurrencyName(currencyName)) {
            this.status = gui("currency_name_invalid");
            return;
        }
        ClientPacketDistributor.sendToServer(new UpdateCurrencyNamePayload(this.menu.containerId, currencyName));
        this.status = gui("currency_name_sent");
    }

    private static boolean validCurrencyName(String value) {
        int length = value.strip().codePointCount(0, value.strip().length());
        return length >= Kingdom.MIN_CURRENCY_NAME_LENGTH && length <= Kingdom.MAX_CURRENCY_NAME_LENGTH;
    }

    private void mint() {
        int quantity = this.menu.mintableCoinCountFor(this.selectedMetal);
        if (quantity <= 0) {
            this.status = gui("insert_matching_ingot", metalName(this.selectedMetal));
            return;
        }
        ClientPacketDistributor.sendToServer(new MintCoinPayload(this.menu.containerId, metalId(this.selectedMetal)));
        this.status = gui("mint_sent", quantity);
    }

    /** Prevent the inventory shortcut from interrupting an active name edit. */
    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.currencyTabOpen
            && this.currencyNameField != null
            && this.currencyNameField.isFocused()) {
            if (this.currencyNameField.keyPressed(event)) {
                return true;
            }
            if (!event.isEscape()) {
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int left = this.leftPos;
        int top = this.topPos;
        renderChrome(graphics, left, top);
        if (this.currencyTabOpen) {
            renderCurrencyTabBackground(graphics, left, top);
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        renderHeaderText(graphics, left, top);
        if (this.currencyTabOpen) {
            renderCurrencyTabText(graphics, left, top);
            return;
        }

        renderInputPanel(graphics, left, top);
        renderPreview(graphics, left, top);
        renderCoinChestPanel(graphics, left, top);
        renderMetalCards(graphics, left, top);
        renderInventoryPanel(graphics, left, top);
        renderActionPanel(graphics, left, top);
        graphics.centeredText(this.font, this.status, left + SCREEN_WIDTH / 2, top + 210, GOLD);
    }

    private void renderChrome(GuiGraphicsExtractor graphics, int left, int top) {
        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            MENU_TEXTURE,
            left,
            top,
            0.0F,
            0.0F,
            SCREEN_WIDTH,
            SCREEN_HEIGHT,
            SCREEN_WIDTH,
            SCREEN_HEIGHT,
            SCREEN_WIDTH,
            SCREEN_HEIGHT
        );
    }

    private void renderHeaderText(GuiGraphicsExtractor graphics, int left, int top) {
        graphics.centeredText(this.font, gui("mint_table"), left + SCREEN_WIDTH / 2, top + 10, GOLD);
        renderHeaderCrest(graphics, left + 18, top + 26, this.display.crest());
        graphics.text(this.font, gui("kingdom", shortName(this.display.kingdomName(), 16)), left + 43, top + 29, TEXT);
        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            coinTexture(this.selectedMetal),
            left + 267,
            top + 25,
            0.0F,
            0.0F,
            18,
            18,
            32,
            32,
            32,
            32
        );
        graphics.text(this.font, Component.literal(shortName(this.display.currencyName(), 10)), left + 289, top + 29, GOLD);
        if (this.display.canEditCurrency()) {
            renderSmallHeaderButton(graphics, left + 379, top + 34, 40, 12, gui("tab_mint"), !this.currencyTabOpen);
            renderSmallHeaderButton(graphics, left + 422, top + 34, 38, 12, gui("tab_currency_short"), this.currencyTabOpen);
        }
    }

    private void renderHeaderCrest(GuiGraphicsExtractor graphics, int x, int y, Symbol crest) {
        graphics.fill(x - 1, y - 1, x + 20, y + 20, PANEL_INNER);
        graphics.outline(x - 1, y - 1, 21, 21, GOLD_DARK);
        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            crestCenterTexture(crest),
            x,
            y,
            0.0F,
            0.0F,
            18,
            18,
            32,
            32,
            32,
            32
        );
    }

    private void renderInputPanel(GuiGraphicsExtractor graphics, int left, int top) {
        int centerX = left + MintHouseLayout.MATERIAL_PANEL_X + MintHouseLayout.MATERIAL_PANEL_WIDTH / 2;
        graphics.centeredText(this.font, gui("material_slot"), centerX, top + 64, GOLD);
        graphics.centeredText(this.font, gui("mint_ratio"), centerX, top + 116, SUBTLE_TEXT);
    }

    private void renderPreview(GuiGraphicsExtractor graphics, int left, int top) {
        graphics.centeredText(this.font, gui("preview"), left + MintHouseLayout.PREVIEW_CENTER_X, top + 64, GOLD);
        int previewX = left + MintHouseLayout.PREVIEW_CENTER_X - PREVIEW_SIZE / 2;
        int previewY = top + MintHouseLayout.PREVIEW_CENTER_Y - PREVIEW_SIZE / 2;
        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            coinTexture(this.selectedMetal),
            previewX,
            previewY,
            0.0F,
            0.0F,
            PREVIEW_SIZE,
            PREVIEW_SIZE,
            32,
            32,
            32,
            32
        );
    }

    private void renderCoinChestPanel(GuiGraphicsExtractor graphics, int left, int top) {
        int centerX = left + MintHouseLayout.COIN_CHEST_PANEL_X + MintHouseLayout.COIN_CHEST_PANEL_WIDTH / 2;
        graphics.centeredText(this.font, gui("coin_chest"), centerX, top + 64, GOLD);
    }

    private void renderMetalCards(GuiGraphicsExtractor graphics, int left, int top) {
        Kingdom.Metal[] metals = {Kingdom.Metal.COPPER, Kingdom.Metal.IRON, Kingdom.Metal.GOLD};
        for (int index = 0; index < metals.length; index++) {
            Kingdom.Metal metal = metals[index];
            int x = left + MintHouseLayout.metalCardX(index);
            int y = top + MintHouseLayout.METAL_CARD_Y;
            boolean selected = metal == this.selectedMetal;
            int border = selected ? metalColor(metal) : BORDER;
            if (selected) {
                graphics.fill(x + 3, y + 3, x + MintHouseLayout.METAL_CARD_WIDTH - 3,
                    y + MintHouseLayout.METAL_CARD_HEIGHT - 3, 0x663D2A13);
                graphics.outline(x + 2, y + 2, MintHouseLayout.METAL_CARD_WIDTH - 4,
                    MintHouseLayout.METAL_CARD_HEIGHT - 4, border);
            }
            graphics.item(ingotStack(metal), x + 11, y + 33);
            graphics.text(this.font, metalName(metal), x + 34, y + 13, metalColor(metal));
            graphics.text(this.font, gui("mint_ratio"), x + 34, y + 31, SUBTLE_TEXT);
            graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                coinTexture(metal),
                x + 94,
                y + 12,
                0.0F,
                0.0F,
                36,
                36,
                32,
                32,
                32,
                32
            );
        }
    }

    private void renderInventoryPanel(GuiGraphicsExtractor graphics, int left, int top) {
        int centerX = left + MintHouseLayout.INVENTORY_PANEL_X + MintHouseLayout.INVENTORY_PANEL_WIDTH / 2;
        graphics.centeredText(this.font, gui("player_inventory"), centerX, top + 225, GOLD);
    }

    private void renderActionPanel(GuiGraphicsExtractor graphics, int left, int top) {
        int quantity = Math.max(0, this.menu.mintableCoinCountFor(this.selectedMetal));
        int centerX = left + MintHouseLayout.ACTION_PANEL_X + MintHouseLayout.ACTION_PANEL_WIDTH / 2;
        graphics.centeredText(this.font, gui("mint_cost"), centerX, top + 225, GOLD);
        graphics.item(ingotStack(this.selectedMetal), left + 291, top + 250);
        graphics.text(this.font, Component.literal("x" + quantity), left + 312, top + 256, TEXT);
        graphics.centeredText(this.font, "→", left + 337, top + 254, GOLD);
        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            coinTexture(this.selectedMetal),
            left + 346,
            top + 246,
            0.0F,
            0.0F,
            30,
            30,
            32,
            32,
            32,
            32
        );
        graphics.text(this.font, Component.literal("x" + quantity), left + 379, top + 256, TEXT);
        graphics.centeredText(this.font, gui("mint_ratio"), centerX, top + 281, SUBTLE_TEXT);

        Component mintLabel = quantity > 0 ? gui("mint_stack", quantity) : gui("confirm");
        renderActionButton(
            graphics,
            left + MintHouseLayout.CONFIRM_X,
            top + MintHouseLayout.CONFIRM_Y,
            MintHouseLayout.CONFIRM_WIDTH,
            MintHouseLayout.CONFIRM_HEIGHT,
            mintLabel,
            this.confirmButton != null && this.confirmButton.active,
            true
        );
        renderActionButton(
            graphics,
            left + MintHouseLayout.BACK_X,
            top + MintHouseLayout.BACK_Y,
            MintHouseLayout.BACK_WIDTH,
            MintHouseLayout.BACK_HEIGHT,
            gui("back"),
            true,
            false
        );
    }

    private void renderCurrencyTabBackground(GuiGraphicsExtractor graphics, int left, int top) {
        graphics.fill(left + 12, top + 56, left + 468, top + 202, 0xF114161A);
        graphics.outline(left + 12, top + 56, 456, 146, BORDER);
    }

    private void renderCurrencyTabText(GuiGraphicsExtractor graphics, int left, int top) {
        graphics.centeredText(this.font, gui("currency_tab_title"), left + SCREEN_WIDTH / 2, top + 67, GOLD);
        graphics.centeredText(this.font, gui("currency_name"), left + SCREEN_WIDTH / 2, top + 89, TEXT);
        graphics.centeredText(this.font, gui("economy_fixed"), left + SCREEN_WIDTH / 2, top + 154, GOLD);
        graphics.centeredText(this.font, gui("economy_example"), left + SCREEN_WIDTH / 2, top + 170, TEXT);
        graphics.centeredText(this.font, gui("economy_total"), left + SCREEN_WIDTH / 2, top + 186, TEXT);
        renderActionButton(graphics, left + 181, top + 128, 118, 18, gui("save_currency"),
            this.saveCurrencyButton != null && this.saveCurrencyButton.active, false);
        renderActionButton(
            graphics,
            left + MintHouseLayout.BACK_X,
            top + MintHouseLayout.BACK_Y,
            MintHouseLayout.BACK_WIDTH,
            MintHouseLayout.BACK_HEIGHT,
            gui("tab_mint"),
            true,
            false
        );
        graphics.centeredText(this.font, this.status, left + SCREEN_WIDTH / 2, top + 210, GOLD);
    }

    private void renderSmallHeaderButton(
        GuiGraphicsExtractor graphics,
        int x,
        int y,
        int width,
        int height,
        Component label,
        boolean selected
    ) {
        graphics.fill(x, y, x + width, y + height, selected ? 0xFF382816 : PANEL_INNER);
        graphics.outline(x, y, width, height, selected ? GOLD : BORDER);
        graphics.centeredText(this.font, label, x + width / 2, y + 3, selected ? GOLD : TEXT);
    }

    private void renderActionButton(
        GuiGraphicsExtractor graphics,
        int x,
        int y,
        int width,
        int height,
        Component label,
        boolean active,
        boolean primary
    ) {
        int fill = !active ? 0xFF29292B : primary ? 0xFF8F6115 : 0xFF3A3938;
        int border = !active ? BORDER_DARK : primary ? GOLD : BORDER;
        int textColor = !active ? SUBTLE_TEXT : primary ? 0xFFFFE49A : TEXT;
        graphics.fill(x, y, x + width, y + height, fill);
        graphics.outline(x, y, width, height, border);
        if (active && primary) {
            graphics.outline(x + 2, y + 2, width - 4, height - 4, GOLD_DARK);
        }
        graphics.centeredText(this.font, label, x + width / 2, y + (height - 8) / 2, textColor);
    }

    /** Buttons retain normal click, focus and tooltip behaviour; the UI draws their faces. */
    private static Button invisible(Button button) {
        button.setAlpha(0.0F);
        return button;
    }

    private static int metalId(Kingdom.Metal metal) {
        return switch (metal) {
            case IRON -> MintHouseMenu.IRON_METAL_ID;
            case COPPER -> MintHouseMenu.COPPER_METAL_ID;
            case GOLD -> MintHouseMenu.GOLD_METAL_ID;
        };
    }

    private static int metalColor(Kingdom.Metal metal) {
        return switch (metal) {
            case IRON -> 0xFFE0E4E8;
            case COPPER -> 0xFFE09A48;
            case GOLD -> GOLD;
        };
    }

    private static Component metalName(Kingdom.Metal metal) {
        return Component.translatable("gui.crownscoins.metal." + metal.name().toLowerCase(Locale.ROOT));
    }

    private static ItemStack ingotStack(Kingdom.Metal metal) {
        return new ItemStack(switch (metal) {
            case COPPER -> Items.COPPER_INGOT;
            case IRON -> Items.IRON_INGOT;
            case GOLD -> Items.GOLD_INGOT;
        });
    }

    private static Identifier coinTexture(Kingdom.Metal metal) {
        return Identifier.fromNamespaceAndPath(
            CrownsCoins.MOD_ID,
            "textures/item/coin/%s_04_crown.png".formatted(coinMetalName(metal))
        );
    }

    private static String coinMetalName(Kingdom.Metal metal) {
        return switch (metal) {
            case COPPER -> "copper";
            case IRON -> "iron";
            case GOLD -> "gold";
        };
    }

    private static Identifier crestCenterTexture(Symbol crest) {
        return Identifier.fromNamespaceAndPath(
            CrownsCoins.MOD_ID,
            "textures/item/overlay/crest_center/%02d_%s.png".formatted(crest.id(), crest.name().toLowerCase(Locale.ROOT))
        );
    }

    private static String shortName(String value, int maximumCodePoints) {
        if (value.codePointCount(0, value.length()) <= maximumCodePoints) {
            return value;
        }
        int end = value.offsetByCodePoints(0, maximumCodePoints - 1);
        return value.substring(0, end) + "…";
    }

    private static Component gui(String key, Object... arguments) {
        return Component.translatable("gui.crownscoins." + key, arguments);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record MetalButton(Button button, Kingdom.Metal metal) {
    }
}
