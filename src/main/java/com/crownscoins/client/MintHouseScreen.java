package com.crownscoins.client;

import com.crownscoins.CrownsCoins;
import com.crownscoins.kingdom.Kingdom;
import com.crownscoins.kingdom.KingdomCrest;
import com.crownscoins.kingdom.Symbol;
import com.crownscoins.menu.MintHouseMenu;
import com.crownscoins.network.MintCoinPayload;
import com.crownscoins.network.UpdateCurrencyNamePayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The client view for the Mint House.
 *
 * <p>The layout deliberately keeps the physical slots owned by {@link MintHouseMenu}
 * and only arranges the visual controls around them. This makes the screen feel like
 * a real Minecraft workstation while leaving every material, chest and minting check
 * on the server.</p>
 */
public final class MintHouseScreen extends AbstractContainerScreen<MintHouseMenu> {
    private static final int SCREEN_WIDTH = 720;
    private static final int SCREEN_HEIGHT = 540;

    // Panel bounds mirror the compact, three-column mint-table interface.
    private static final int HEADER_X = 56;
    private static final int HEADER_Y = 26;
    private static final int HEADER_WIDTH = 610;
    private static final int HEADER_HEIGHT = 45;
    private static final int METAL_PANEL_X = 0;
    private static final int METAL_PANEL_Y = 77;
    private static final int METAL_PANEL_WIDTH = 183;
    private static final int PREVIEW_PANEL_X = 190;
    private static final int PREVIEW_PANEL_Y = 77;
    private static final int PREVIEW_PANEL_WIDTH = 313;
    private static final int SYMBOL_PANEL_X = 509;
    private static final int SYMBOL_PANEL_Y = 77;
    private static final int SYMBOL_PANEL_WIDTH = 211;
    private static final int TOP_PANEL_HEIGHT = 307;
    private static final int BOTTOM_PANEL_Y = 393;
    private static final int BOTTOM_PANEL_HEIGHT = 134;
    private static final int PLAYER_PANEL_X = 0;
    private static final int PLAYER_PANEL_WIDTH = 240;
    private static final int CHEST_PANEL_X = 248;
    private static final int CHEST_PANEL_WIDTH = 250;
    private static final int ACTION_PANEL_X = 504;
    private static final int ACTION_PANEL_WIDTH = 216;

    // These coordinates are also used by MintHouseMenu for the real slots.
    private static final int MATERIAL_SLOT_X = 45;
    private static final int MATERIAL_SLOT_Y = 334;
    private static final int PLAYER_INVENTORY_X = 24;
    private static final int PLAYER_INVENTORY_Y = 422;
    private static final int HOTBAR_Y = 482;
    private static final int COIN_CHEST_X = 268;
    private static final int COIN_CHEST_Y = 422;

    private static final int METAL_CARD_X = 9;
    private static final int METAL_CARD_Y = 89;
    private static final int METAL_CARD_WIDTH = 177;
    private static final int METAL_CARD_HEIGHT = 64;
    private static final int METAL_CARD_GAP = 8;
    private static final int PREVIEW_CENTER_X = 350;
    private static final int PREVIEW_CENTER_Y = 180;
    private static final int PREVIEW_SIZE = 154;
    private static final int CONFIRM_X = 528;
    private static final int CONFIRM_Y = 453;
    private static final int CONFIRM_WIDTH = 168;
    private static final int CONFIRM_HEIGHT = 30;
    private static final int BACK_X = 550;
    private static final int BACK_Y = 496;
    private static final int BACK_WIDTH = 124;
    private static final int BACK_HEIGHT = 20;

    private static final int PANEL_INNER = 0xE9101115;
    private static final int BORDER_DARK = 0xFF2B251D;
    private static final int BORDER = 0xFF745735;
    private static final int GOLD_DARK = 0xFF8E641B;
    private static final int GOLD = 0xFFFFD34F;
    private static final int TEXT = 0xFFD7D9D9;
    private static final int SUBTLE_TEXT = 0xFFA8A49B;
    private static final Identifier MENU_TEXTURE = Identifier.fromNamespaceAndPath(
        CrownsCoins.MOD_ID,
        "textures/gui/mint_house_workbench.png"
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
            int cardY = this.topPos + METAL_CARD_Y + index * (METAL_CARD_HEIGHT + METAL_CARD_GAP);
            Button card = this.addRenderableWidget(invisible(Button.builder(Component.empty(), ignored -> selectMetal(metal))
                .bounds(this.leftPos + METAL_CARD_X, cardY, METAL_CARD_WIDTH, METAL_CARD_HEIGHT)
                .tooltip(Tooltip.create(metalName(metal)))
                .build()));
            this.metalButtons.add(new MetalButton(card, metal));
        }

        this.confirmButton = this.addRenderableWidget(invisible(Button.builder(Component.empty(), ignored -> mint())
            .bounds(this.leftPos + CONFIRM_X, this.topPos + CONFIRM_Y, CONFIRM_WIDTH, CONFIRM_HEIGHT)
            .build()));
        this.backButton = this.addRenderableWidget(invisible(Button.builder(Component.empty(), ignored -> {
                if (this.currencyTabOpen) {
                    this.setCurrencyTab(false);
                } else {
                    this.onClose();
                }
            })
            .bounds(this.leftPos + BACK_X, this.topPos + BACK_Y, BACK_WIDTH, BACK_HEIGHT)
            .build()));

        if (this.display.canEditCurrency()) {
            this.mintTabButton = this.addRenderableWidget(invisible(Button.builder(Component.empty(), ignored -> this.setCurrencyTab(false))
                .bounds(this.leftPos + 520, this.topPos + 49, 53, 16)
                .tooltip(Tooltip.create(gui("tab_mint")))
                .build()));
            this.currencyTabButton = this.addRenderableWidget(invisible(Button.builder(Component.empty(), ignored -> this.setCurrencyTab(true))
                .bounds(this.leftPos + 577, this.topPos + 49, 84, 16)
                .tooltip(Tooltip.create(gui("tab_currency")))
                .build()));
        }

        Component currencyNameLabel = gui("currency_name");
        this.currencyNameField = this.addRenderableWidget(new EditBox(
            this.font,
            this.leftPos + 232,
            this.topPos + 202,
            230,
            20,
            currencyNameLabel
        ));
        this.currencyNameField.setMaxLength(Kingdom.MAX_CURRENCY_NAME_LENGTH);
        this.currencyNameField.setHint(currencyNameLabel);
        this.currencyNameField.setValue(this.display.currencyName());
        this.currencyNameField.setResponder(ignored -> this.refreshCurrencySaveState());
        this.saveCurrencyButton = this.addRenderableWidget(invisible(Button.builder(Component.empty(), ignored -> saveCurrencyName())
            .bounds(this.leftPos + 288, this.topPos + 235, 118, 20)
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
        if (this.confirmButton != null) {
            int quantity = this.menu.mintableCoinCountFor(this.selectedMetal);
            this.confirmButton.active = KingdomCrest.isSupported(this.display.crest())
                && quantity > 0;
            if (quantity > 0) {
                this.confirmButton.setMessage(gui("mint_stack", quantity));
                this.confirmButton.setTooltip(Tooltip.create(gui("mint_stack_tooltip", quantity)));
            } else {
                this.confirmButton.setMessage(gui("confirm"));
                this.confirmButton.setTooltip(Tooltip.create(gui("mint_stack_empty")));
            }
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
        ClientPacketDistributor.sendToServer(new MintCoinPayload(
            this.menu.containerId,
            metalId(this.selectedMetal)
        ));
        this.status = gui("mint_sent", quantity);
    }

    /**
     * An EditBox does not claim ordinary printable keys itself. Claiming the
     * event here prevents the inventory key (normally E) from interrupting a
     * currency name while the field is focused.
     */
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

        renderMetalCards(graphics, left, top);
        renderCleanCoinPanel(graphics, left, top);
        renderPreview(graphics, left, top);
        renderActionPanel(graphics, left, top);
        renderBottomLabels(graphics, left, top);
        graphics.centeredText(this.font, this.status, left + PREVIEW_CENTER_X, top + 365, GOLD);
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
        renderHeaderCrest(graphics, left + 71, top + 33, this.display.crest());
        graphics.text(this.font, gui("kingdom", this.display.kingdomName()), left + 110, top + 44, TEXT);
        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            coinTexture(this.selectedMetal),
            left + 419,
            top + 35,
            0.0F,
            0.0F,
            27,
            27,
            32,
            32,
            32,
            32
        );
        graphics.text(this.font, Component.literal(this.display.currencyName()), left + 451, top + 44, GOLD);
        if (this.display.canEditCurrency()) {
            renderSmallHeaderButton(graphics, left + 520, top + 49, 53, 16, gui("tab_mint"), !this.currencyTabOpen);
            renderSmallHeaderButton(graphics, left + 577, top + 49, 84, 16, gui("tab_currency"), this.currencyTabOpen);
        }
    }

    private void renderHeaderCrest(GuiGraphicsExtractor graphics, int x, int y, Symbol crest) {
        graphics.fill(x - 2, y - 2, x + 31, y + 31, PANEL_INNER);
        graphics.outline(x - 2, y - 2, 33, 33, GOLD_DARK);
        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            crestCenterTexture(crest),
            x,
            y,
            0.0F,
            0.0F,
            27,
            27,
            32,
            32,
            32,
            32
        );
    }

    private void renderMetalCards(GuiGraphicsExtractor graphics, int left, int top) {
        graphics.centeredText(this.font, gui("metal_and_value"), left + 91, top + 91, GOLD);
        Kingdom.Metal[] metals = {Kingdom.Metal.COPPER, Kingdom.Metal.IRON, Kingdom.Metal.GOLD};
        for (int index = 0; index < metals.length; index++) {
            Kingdom.Metal metal = metals[index];
            int x = left + METAL_CARD_X;
            int y = top + METAL_CARD_Y + index * (METAL_CARD_HEIGHT + METAL_CARD_GAP);
            boolean selected = metal == this.selectedMetal;
            int border = selected ? metalColor(metal) : BORDER;
            if (selected) {
                graphics.fill(x + 2, y + 2, x + METAL_CARD_WIDTH - 2, y + METAL_CARD_HEIGHT - 2, 0x402F2112);
                graphics.outline(x + 2, y + 2, METAL_CARD_WIDTH - 4, METAL_CARD_HEIGHT - 4, border);
            }
            graphics.item(ingotStack(metal), x + 13, y + 24);
            graphics.text(this.font, metalName(metal), x + 42, y + 16, metalColor(metal));
            graphics.text(this.font, gui("mint_ratio"), x + 42, y + 37, SUBTLE_TEXT);
        }
        graphics.centeredText(this.font, gui("material_slot"), left + MATERIAL_SLOT_X + 8, top + 306, GOLD);
        graphics.centeredText(this.font, gui("mint_ratio"), left + MATERIAL_SLOT_X + 8, top + 359, SUBTLE_TEXT);
    }

    private void renderCleanCoinPanel(GuiGraphicsExtractor graphics, int left, int top) {
        int centerX = left + SYMBOL_PANEL_X + SYMBOL_PANEL_WIDTH / 2;
        graphics.centeredText(this.font, gui("clean_coin_heading"), centerX, top + 91, GOLD);
        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            coinTexture(this.selectedMetal),
            centerX - 56,
            top + 124,
            0.0F,
            0.0F,
            112,
            112,
            32,
            32,
            32,
            32
        );
        graphics.centeredText(this.font, gui("clean_coin_description"), centerX, top + 266, TEXT);
        graphics.centeredText(this.font, gui("clean_coin_note"), centerX, top + 284, SUBTLE_TEXT);
    }

    private void renderPreview(GuiGraphicsExtractor graphics, int left, int top) {
        graphics.centeredText(this.font, gui("preview"), left + PREVIEW_CENTER_X, top + 91, GOLD);
        int previewX = left + PREVIEW_CENTER_X - PREVIEW_SIZE / 2;
        int previewY = top + PREVIEW_CENTER_Y - PREVIEW_SIZE / 2;
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
        graphics.centeredText(this.font, gui("coin_formula"), left + PREVIEW_CENTER_X, top + 348, TEXT);
    }

    private void renderActionPanel(GuiGraphicsExtractor graphics, int left, int top) {
        int quantity = this.menu.mintableCoinCountFor(this.selectedMetal);
        int shownQuantity = Math.max(0, quantity);
        graphics.centeredText(this.font, gui("mint_cost"), left + ACTION_PANEL_X + ACTION_PANEL_WIDTH / 2,
            top + 404, GOLD);
        graphics.item(ingotStack(this.selectedMetal), left + 529, top + 419);
        graphics.text(this.font, Component.literal("x" + shownQuantity), left + 549, top + 425, TEXT);
        graphics.centeredText(this.font, "→", left + 604, top + 422, GOLD);
        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            coinTexture(this.selectedMetal),
            left + 625,
            top + 416,
            0.0F,
            0.0F,
            28,
            28,
            32,
            32,
            32,
            32
        );
        graphics.text(this.font, Component.literal("x" + shownQuantity), left + 655, top + 425, TEXT);

        Component mintLabel = quantity > 0 ? gui("mint_stack", quantity) : gui("confirm");
        renderActionButton(graphics, left + CONFIRM_X, top + CONFIRM_Y, CONFIRM_WIDTH, CONFIRM_HEIGHT,
            mintLabel, this.confirmButton != null && this.confirmButton.active, true);
        renderActionButton(graphics, left + BACK_X, top + BACK_Y, BACK_WIDTH, BACK_HEIGHT, gui("back"), true, false);
    }

    private void renderBottomLabels(GuiGraphicsExtractor graphics, int left, int top) {
        graphics.centeredText(this.font, gui("player_inventory"), left + PLAYER_PANEL_X + PLAYER_PANEL_WIDTH / 2, top + 403, GOLD);
        graphics.centeredText(this.font, gui("coin_chest"), left + CHEST_PANEL_X + CHEST_PANEL_WIDTH / 2, top + 403, GOLD);
    }

    private void renderCurrencyTabBackground(GuiGraphicsExtractor graphics, int left, int top) {
        // This tab replaces the selection workflow without changing any real
        // inventory slot or stored coin. It is only the founder's name editor.
        graphics.fill(left + METAL_PANEL_X + 7, top + METAL_PANEL_Y + 7,
            left + SCREEN_WIDTH - 7, top + TOP_PANEL_HEIGHT + METAL_PANEL_Y - 7, 0xF114161A);
        graphics.outline(left + METAL_PANEL_X + 7, top + METAL_PANEL_Y + 7,
            SCREEN_WIDTH - 14, TOP_PANEL_HEIGHT - 14, BORDER);
    }

    private void renderCurrencyTabText(GuiGraphicsExtractor graphics, int left, int top) {
        // Hide the ordinary material slot after its item has been extracted.
        graphics.fill(left + MATERIAL_SLOT_X - 2, top + MATERIAL_SLOT_Y - 2,
            left + MATERIAL_SLOT_X + 20, top + MATERIAL_SLOT_Y + 20, 0xFF14161A);
        graphics.centeredText(this.font, gui("currency_tab_title"), left + SCREEN_WIDTH / 2, top + 121, GOLD);
        graphics.centeredText(this.font, gui("currency_name"), left + SCREEN_WIDTH / 2, top + 185, TEXT);
        graphics.centeredText(this.font, gui("economy_fixed"), left + SCREEN_WIDTH / 2, top + 269, GOLD);
        graphics.centeredText(this.font, gui("economy_example"), left + SCREEN_WIDTH / 2, top + 287, TEXT);
        graphics.centeredText(this.font, gui("economy_total"), left + SCREEN_WIDTH / 2, top + 305, TEXT);
        renderActionButton(graphics, left + 288, top + 235, 118, 20, gui("save_currency"),
            this.saveCurrencyButton != null && this.saveCurrencyButton.active, false);
        renderActionButton(graphics, left + BACK_X, top + BACK_Y, BACK_WIDTH, BACK_HEIGHT, gui("tab_mint"), true, false);
        graphics.centeredText(this.font, this.status, left + SCREEN_WIDTH / 2, top + 350, GOLD);
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
        graphics.centeredText(this.font, label, x + width / 2, y + 4, selected ? GOLD : TEXT);
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

    /** Buttons retain their click, focus and tooltip behaviour; the artwork draws their face. */
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
