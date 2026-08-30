package com.crownscoins.client;

import com.crownscoins.CrownsCoins;
import com.crownscoins.coin.CoinData;
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
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The Mint House works like a crafting table: the player puts one ingot in the
 * visible socket, selects the two side symbols, then mints into their inventory.
 */
public final class MintHouseScreen extends AbstractContainerScreen<MintHouseMenu> {
    private static final int SCREEN_WIDTH = 720;
    private static final int SCREEN_HEIGHT = 540;
    private static final int TILE_WIDTH = 24;
    private static final int TILE_HEIGHT = 20;
    private static final int TILE_GAP_X = 1;
    private static final int TILE_GAP_Y = 1;
    private static final int GRID_TOP = 60;
    private static final int[] GRID_LEFTS = {232, 390, 547};
    private static final int[] GRID_CENTERS = {294, 452, 609};
    private static final int PREVIEW_CENTER_X = 134;
    private static final int PREVIEW_CENTER_Y = 236;
    private static final int MATERIAL_CENTER_X = 320;
    private static final int LEFT_SYMBOL_CENTER_X = 452;
    private static final int RIGHT_SYMBOL_CENTER_X = 590;
    private static final int ASSEMBLY_CENTER_Y = 236;
    private static final int PLAYER_INVENTORY_X = 271;
    private static final int PLAYER_INVENTORY_Y = 348;
    private static final int HOTBAR_Y = PLAYER_INVENTORY_Y + 58;
    private static final Identifier MENU_TEXTURE = Identifier.fromNamespaceAndPath(
        CrownsCoins.MOD_ID,
        "textures/gui/mint_house_workbench.png"
    );
    private static final Identifier CURRENCY_TAB_TEXTURE = Identifier.fromNamespaceAndPath(
        CrownsCoins.MOD_ID,
        "textures/gui/mint_currency_tab.png"
    );

    private final MintHouseMenu.ClientMintData display;
    private final List<Symbol> selectedSymbols = new ArrayList<>(CoinData.REQUIRED_SECONDARY_SYMBOLS);
    private final List<CatalogTile> catalogTiles = new ArrayList<>();
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
        // The bespoke texture owns all labels; hide AbstractContainerScreen's defaults.
        this.titleLabelX = -10_000;
        this.inventoryLabelX = -10_000;
    }

    @Override
    protected void init() {
        super.init();
        this.catalogTiles.clear();

        Kingdom.Metal[] metals = {Kingdom.Metal.COPPER, Kingdom.Metal.IRON, Kingdom.Metal.GOLD};
        Symbol[] symbols = Symbol.values();
        for (int panel = 0; panel < metals.length; panel++) {
            Kingdom.Metal metal = metals[panel];
            for (int index = 0; index < symbols.length; index++) {
                Symbol symbol = symbols[index];
                int x = this.leftPos + GRID_LEFTS[panel] + (index % 5) * (TILE_WIDTH + TILE_GAP_X);
                int y = this.topPos + GRID_TOP + (index / 5) * (TILE_HEIGHT + TILE_GAP_Y);
                Button button = this.addRenderableWidget(Button.builder(Component.empty(), ignored -> chooseSymbol(metal, symbol))
                    .bounds(x, y, TILE_WIDTH, TILE_HEIGHT)
                    .tooltip(Tooltip.create(symbolName(symbol)))
                    .build());
                this.catalogTiles.add(new CatalogTile(button, metal, symbol, x, y));
            }
        }

        this.confirmButton = this.addRenderableWidget(Button.builder(gui("confirm"), ignored -> mint())
            .bounds(this.leftPos + 540, this.topPos + 271, 76, 20)
            .build());
        this.backButton = this.addRenderableWidget(Button.builder(gui("back"), ignored -> {
                if (this.currencyTabOpen) {
                    this.setCurrencyTab(false);
                } else {
                    this.onClose();
                }
            })
            .bounds(this.leftPos + 456, this.topPos + 271, 76, 20)
            .build());
        if (this.display.canEditCurrency()) {
            this.mintTabButton = this.addRenderableWidget(Button.builder(gui("tab_mint"), ignored -> this.setCurrencyTab(false))
                .bounds(this.leftPos + 500, this.topPos + 8, 62, 16)
                .build());
            this.currencyTabButton = this.addRenderableWidget(Button.builder(gui("tab_currency"), ignored -> this.setCurrencyTab(true))
                .bounds(this.leftPos + 566, this.topPos + 8, 116, 16)
                .build());
        }

        Component currencyNameLabel = gui("currency_name");
        this.currencyNameField = this.addRenderableWidget(new EditBox(
            this.font,
            this.leftPos + 335,
            this.topPos + 178,
            190,
            20,
            currencyNameLabel
        ));
        this.currencyNameField.setMaxLength(Kingdom.MAX_CURRENCY_NAME_LENGTH);
        this.currencyNameField.setHint(currencyNameLabel);
        this.currencyNameField.setValue(this.display.currencyName());
        this.currencyNameField.setResponder(ignored -> this.refreshCurrencySaveState());
        this.saveCurrencyButton = this.addRenderableWidget(Button.builder(gui("save_currency"), ignored -> saveCurrencyName())
            .bounds(this.leftPos + 531, this.topPos + 212, 96, 20)
            .build());

        this.setCurrencyTab(false);
        this.refreshSelectionState();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        this.refreshSelectionState();
    }

    private void chooseSymbol(Kingdom.Metal metal, Symbol symbol) {
        if (this.selectedMetal != metal) {
            this.selectedMetal = metal;
            this.selectedSymbols.clear();
            this.selectedSymbols.add(symbol);
            this.status = gui("metal_changed", metalName(metal));
        } else if (this.selectedSymbols.remove(symbol)) {
            this.status = Component.empty();
        } else if (this.selectedSymbols.size() < CoinData.REQUIRED_SECONDARY_SYMBOLS) {
            this.selectedSymbols.add(symbol);
            this.status = Component.empty();
        } else {
            this.status = gui("symbol_limit", CoinData.REQUIRED_SECONDARY_SYMBOLS);
        }
        this.refreshSelectionState();
    }

    private void refreshSelectionState() {
        if (this.confirmButton != null) {
            int quantity = this.menu.materialCountFor(this.selectedMetal);
            this.confirmButton.active = this.selectedSymbols.size() == CoinData.REQUIRED_SECONDARY_SYMBOLS
                && KingdomCrest.isSupported(this.display.crest())
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
        for (CatalogTile tile : this.catalogTiles) {
            tile.button().visible = !open;
        }
        if (this.confirmButton != null) {
            this.confirmButton.visible = !open;
        }
        if (this.backButton != null) {
            this.backButton.setMessage(gui(open ? "tab_mint" : "back"));
        }
        if (this.mintTabButton != null) {
            this.mintTabButton.active = open;
        }
        if (this.currencyTabButton != null) {
            this.currencyTabButton.active = !open;
        }
        if (this.currencyNameField != null) {
            this.currencyNameField.visible = open;
            // When the tab opens, immediately capture keyboard input for the
            // name field. This prevents game bindings such as E (inventory)
            // from acting while a player is typing a currency name.
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
        if (this.selectedSymbols.size() != CoinData.REQUIRED_SECONDARY_SYMBOLS) {
            this.status = gui("select_two_symbols");
            return;
        }
        int quantity = this.menu.materialCountFor(this.selectedMetal);
        if (quantity <= 0) {
            this.status = gui("insert_matching_ingot", metalName(this.selectedMetal));
            return;
        }
        ClientPacketDistributor.sendToServer(new MintCoinPayload(
            this.menu.containerId,
            metalId(this.selectedMetal),
            previewStyleId(),
            this.selectedSymbols.stream().map(Symbol::id).toList()
        ));
        this.status = gui("mint_sent", quantity);
    }

    /**
     * AbstractContainerScreen handles the inventory key after its widgets. An
     * EditBox returns false for ordinary printable keys, so without this guard
     * the inventory binding (normally E) closes the currency form before the
     * corresponding character reaches charTyped().
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
        graphics.blit(RenderPipelines.GUI_TEXTURED, MENU_TEXTURE, left, top, 0.0F, 0.0F, SCREEN_WIDTH, SCREEN_HEIGHT, SCREEN_WIDTH, SCREEN_HEIGHT);
        renderInventorySurface(graphics, left, top);
        if (!this.currencyTabOpen) {
            renderMaterialSlotFrame(graphics, left, top);
        } else {
            renderCurrencyTabBackground(graphics, left, top);
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        graphics.centeredText(this.font, this.title, left + SCREEN_WIDTH / 2, top + 12, 0xFFFFD878);
        graphics.centeredText(this.font, gui("kingdom", this.display.kingdomName()), left + SCREEN_WIDTH / 2, top + 26, 0xFFE4C67A);
        if (this.currencyTabOpen) {
            renderCurrencyTabText(graphics, left, top);
            return;
        }

        renderCatalogTiles(graphics);
        renderAssembly(graphics, left, top);
        renderPreview(graphics, left, top);
        graphics.centeredText(this.font, this.status, left + SCREEN_WIDTH / 2, top + 296, 0xFFFFD878);

        graphics.centeredText(this.font, gui("panel_bronze"), left + GRID_CENTERS[0], top + 50, metalColor(Kingdom.Metal.COPPER));
        graphics.centeredText(this.font, gui("panel_iron"), left + GRID_CENTERS[1], top + 50, metalColor(Kingdom.Metal.IRON));
        graphics.centeredText(this.font, gui("panel_gold"), left + GRID_CENTERS[2], top + 50, metalColor(Kingdom.Metal.GOLD));
        graphics.centeredText(this.font, gui("coin_formula"), left + 449, top + 174, 0xFFD7D9D9);
        graphics.centeredText(this.font, gui("preview"), left + PREVIEW_CENTER_X, top + 296, 0xFFE4C67A);
        graphics.centeredText(this.font, gui("player_inventory"), left + SCREEN_WIDTH / 2, top + 328, 0xFFE4C67A);
    }

    private void renderCurrencyTabBackground(GuiGraphicsExtractor graphics, int left, int top) {
        graphics.fill(left + 32, top + 146, left + 688, top + 266, 0xEF111214);
        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            CURRENCY_TAB_TEXTURE,
            left + 40,
            top + 160,
            0.0F,
            0.0F,
            640,
            88,
            640,
            88,
            640,
            88
        );
    }

    private void renderCurrencyTabText(GuiGraphicsExtractor graphics, int left, int top) {
        // Cover the otherwise interactive ingot socket only while the settings panel is open.
        graphics.fill(left + MATERIAL_CENTER_X - 11, top + ASSEMBLY_CENTER_Y - 11,
            left + MATERIAL_CENTER_X + 11, top + ASSEMBLY_CENTER_Y + 11, 0xFF1B1711);
        graphics.centeredText(this.font, gui("currency_tab_title"), left + SCREEN_WIDTH / 2, top + 150, 0xFFFFD878);
        graphics.text(this.font, gui("currency_name"), left + 184, top + 183, 0xFFE4C67A);
        graphics.text(this.font, gui("economy_fixed"), left + 184, top + 207, 0xFFFFD878);
        graphics.text(this.font, gui("economy_example"), left + 184, top + 222, 0xFFCED2D4);
        graphics.text(this.font, gui("economy_total"), left + 184, top + 237, 0xFFCED2D4);
        graphics.centeredText(this.font, this.status, left + SCREEN_WIDTH / 2, top + 257, 0xFFFFD878);
    }

    private void renderCatalogTiles(GuiGraphicsExtractor graphics) {
        for (CatalogTile tile : this.catalogTiles) {
            // The earlier miniature coins attempted to show a crown plus a
            // symbol in a 20px tile.  That made the actual choice impossible
            // to recognise.  The catalogue is a chooser, so give its symbol
            // the whole tile; the completed crown-and-two-symbol coin remains
            // visible in the dedicated preview below.
            graphics.fill(tile.x(), tile.y(), tile.x() + TILE_WIDTH, tile.y() + TILE_HEIGHT, 0xFF171515);
            graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                symbolTexture(tile.symbol()),
                tile.x() + 2,
                tile.y() + 1,
                0.0F,
                0.0F,
                20,
                20,
                32,
                32,
                32,
                32
            );
            int selectionIndex = this.selectedMetal == tile.metal() ? this.selectedSymbols.indexOf(tile.symbol()) : -1;
            int border = this.selectedMetal == tile.metal() ? metalColor(tile.metal()) : 0x80626968;
            if (selectionIndex >= 0) {
                border = 0xFFFFFFFF;
            }
            graphics.outline(tile.x(), tile.y(), TILE_WIDTH, TILE_HEIGHT, border);
            if (selectionIndex >= 0) {
                graphics.fill(tile.x() + 1, tile.y() + 1, tile.x() + 8, tile.y() + 8, 0xFFFFFFFF);
                graphics.text(this.font, Component.literal(Integer.toString(selectionIndex + 1)), tile.x() + 2, tile.y() + 1, 0xFF1B1510);
            }
        }
    }

    private void renderAssembly(GuiGraphicsExtractor graphics, int left, int top) {
        graphics.centeredText(this.font, gui("material_slot"), left + MATERIAL_CENTER_X, top + 200, 0xFFE4C67A);
        renderChosenSymbol(graphics, left + LEFT_SYMBOL_CENTER_X, top + ASSEMBLY_CENTER_Y, gui("left_symbol"), symbolAt(0));
        renderChosenSymbol(graphics, left + RIGHT_SYMBOL_CENTER_X, top + ASSEMBLY_CENTER_Y, gui("right_symbol"), symbolAt(1));
    }

    private void renderChosenSymbol(GuiGraphicsExtractor graphics, int centerX, int centerY, Component label, Symbol symbol) {
        graphics.centeredText(this.font, label, centerX, centerY - 36, 0xFFE4C67A);
        if (symbol != null) {
            graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                symbolTexture(symbol),
                centerX - 11,
                centerY - 11,
                0.0F,
                0.0F,
                22,
                22,
                32,
                32,
                32,
                32
            );
            graphics.centeredText(this.font, symbolName(symbol), centerX, centerY + 28, 0xFFD7D9D9);
        } else {
            graphics.centeredText(this.font, gui("empty_slot"), centerX, centerY - 3, 0xFF9C9C9C);
        }
    }

    private void renderPreview(GuiGraphicsExtractor graphics, int left, int top) {
        int previewX = left + PREVIEW_CENTER_X - 50;
        int previewY = top + PREVIEW_CENTER_Y - 50;
        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            coinTexture(this.selectedMetal, previewStyleId()),
            previewX,
            previewY,
            0.0F,
            0.0F,
            100,
            100,
            32,
            32,
            32,
            32
        );
        // New coins always carry a small Crown as their fixed central mark.
        // The two player selections sit in matched spaces to its left and right.
        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            crestCenterTexture(Symbol.CROWN),
            previewX,
            previewY,
            0.0F,
            0.0F,
            100,
            100,
            32,
            32,
            32,
            32
        );
        renderPreviewSideSymbol(graphics, previewX, previewY, symbolAt(0), true);
        renderPreviewSideSymbol(graphics, previewX, previewY, symbolAt(1), false);
    }

    private void renderPreviewSideSymbol(GuiGraphicsExtractor graphics, int x, int y, Symbol symbol, boolean leftSide) {
        if (symbol == null) {
            return;
        }
        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            leftSide ? symbolLeftTexture(symbol) : symbolRightTexture(symbol),
            x,
            y,
            0.0F,
            0.0F,
            100,
            100,
            32,
            32,
            32,
            32
        );
    }

    private void renderInventorySurface(GuiGraphicsExtractor graphics, int left, int top) {
        int panelX = left + 250;
        int panelY = top + 320;
        graphics.fill(panelX, panelY, panelX + 220, panelY + 118, 0xE3111214);
        graphics.outline(panelX, panelY, 220, 118, 0xFF8C673C);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                renderSlotFrame(graphics, left + PLAYER_INVENTORY_X + column * 18, top + PLAYER_INVENTORY_Y + row * 18);
            }
        }
        for (int column = 0; column < 9; column++) {
            renderSlotFrame(graphics, left + PLAYER_INVENTORY_X + column * 18, top + HOTBAR_Y);
        }
    }

    private void renderMaterialSlotFrame(GuiGraphicsExtractor graphics, int left, int top) {
        renderSlotFrame(graphics, left + MATERIAL_CENTER_X - 9, top + ASSEMBLY_CENTER_Y - 9);
    }

    private static void renderSlotFrame(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 19, y + 19, 0xFF151719);
        graphics.outline(x - 1, y - 1, 20, 20, 0xFF856539);
    }

    private int previewStyleId() {
        // New currency always uses the crown die as its neutral coin base.
        // The realm crest and exactly two selections provide its identity.
        return Symbol.CROWN.id();
    }

    private Symbol symbolAt(int index) {
        return this.selectedSymbols.size() > index ? this.selectedSymbols.get(index) : null;
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
            case GOLD -> 0xFFFFD34F;
        };
    }

    private static Component metalName(Kingdom.Metal metal) {
        return Component.translatable("gui.crownscoins.metal." + metal.name().toLowerCase(Locale.ROOT));
    }

    private static Identifier catalogCoinTexture(Kingdom.Metal metal, Symbol symbol) {
        return Identifier.fromNamespaceAndPath(
            CrownsCoins.MOD_ID,
            "textures/gui/catalog_coin/%s_%02d_%s.png".formatted(catalogMetalName(metal), symbol.id(), symbol.name().toLowerCase(Locale.ROOT))
        );
    }

    private static Identifier coinTexture(Kingdom.Metal metal, int styleId) {
        Symbol style = Symbol.byId(styleId);
        return Identifier.fromNamespaceAndPath(
            CrownsCoins.MOD_ID,
            "textures/item/coin/%s_%02d_%s.png".formatted(coinMetalName(metal), style.id(), style.name().toLowerCase(Locale.ROOT))
        );
    }

    private static String catalogMetalName(Kingdom.Metal metal) {
        return switch (metal) {
            case COPPER -> "bronze";
            case IRON -> "iron";
            case GOLD -> "gold";
        };
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

    private static Identifier symbolTexture(Symbol symbol) {
        return Identifier.fromNamespaceAndPath(
            CrownsCoins.MOD_ID,
            "textures/item/overlay/symbol/%02d_%s.png".formatted(symbol.id(), symbol.name().toLowerCase(Locale.ROOT))
        );
    }

    private static Identifier symbolLeftTexture(Symbol symbol) {
        return Identifier.fromNamespaceAndPath(
            CrownsCoins.MOD_ID,
            "textures/item/overlay/symbol_left/%02d_%s.png".formatted(symbol.id(), symbol.name().toLowerCase(Locale.ROOT))
        );
    }

    private static Identifier symbolRightTexture(Symbol symbol) {
        return Identifier.fromNamespaceAndPath(
            CrownsCoins.MOD_ID,
            "textures/item/overlay/symbol_right/%02d_%s.png".formatted(symbol.id(), symbol.name().toLowerCase(Locale.ROOT))
        );
    }

    private static Component crestName(Symbol crest) {
        return KingdomCrest.fromSymbol(crest)
            .<Component>map(value -> Component.translatable(value.translationKey()))
            .orElseGet(() -> symbolName(crest));
    }

    private static Component gui(String key, Object... arguments) {
        return Component.translatable("gui.crownscoins." + key, arguments);
    }

    private static Component symbolName(Symbol symbol) {
        return Component.translatable("symbol.crownscoins." + symbol.name().toLowerCase(Locale.ROOT));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record CatalogTile(Button button, Kingdom.Metal metal, Symbol symbol, int x, int y) {
    }
}
