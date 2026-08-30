package com.crownscoins.client;

import com.crownscoins.CrownsCoins;
import com.crownscoins.coin.CoinData;
import com.crownscoins.kingdom.Kingdom;
import com.crownscoins.kingdom.KingdomCrest;
import com.crownscoins.kingdom.Symbol;
import com.crownscoins.menu.MintHouseMenu;
import com.crownscoins.network.MintCoinPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Full-screen visual catalogue for the Mint House. The client can only choose
 * bounded catalog values; the server remains the authority for the kingdom,
 * required crest, metal material and inventory consumption.
 */
public final class MintHouseScreen extends AbstractContainerScreen<MintHouseMenu> {
    private static final int SCREEN_WIDTH = 720;
    private static final int SCREEN_HEIGHT = 540;
    private static final int TILE_WIDTH = 34;
    private static final int TILE_HEIGHT = 28;
    private static final int TILE_GAP_X = 2;
    private static final int TILE_GAP_Y = 3;
    private static final int GRID_TOP = 112;
    private static final int[] GRID_LEFTS = {70, 266, 462};
    private static final int CREST_TOP = 398;
    private static final int CREST_LEFT = 211;
    private static final int CREST_WIDTH = 26;
    private static final int CREST_GAP = 3;
    private static final UUID PREVIEW_KINGDOM_ID = new UUID(0L, 0L);
    private static final Identifier MENU_TEXTURE = Identifier.fromNamespaceAndPath(
        CrownsCoins.MOD_ID,
        "textures/gui/mint_house_menu.png"
    );

    private final MintHouseMenu.ClientMintData display;
    private final List<Symbol> selectedSymbols = new ArrayList<>(CoinData.REQUIRED_SECONDARY_SYMBOLS);
    private final List<CatalogTile> catalogTiles = new ArrayList<>();
    private final List<CrestTile> crestTiles = new ArrayList<>();
    private Kingdom.Metal selectedMetal = Kingdom.Metal.COPPER;
    private Button confirmButton;
    private Component status = Component.empty();

    public MintHouseScreen(MintHouseMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, SCREEN_WIDTH, SCREEN_HEIGHT);
        this.display = menu.clientData();
    }

    @Override
    protected void init() {
        super.init();
        this.catalogTiles.clear();
        this.crestTiles.clear();

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
                    .build());
                this.catalogTiles.add(new CatalogTile(button, metal, symbol, x, y));
            }
        }

        KingdomCrest[] crests = KingdomCrest.values();
        for (int index = 0; index < crests.length; index++) {
            KingdomCrest crest = crests[index];
            int x = this.leftPos + CREST_LEFT + index * (CREST_WIDTH + CREST_GAP);
            int y = this.topPos + CREST_TOP;
            Button button = this.addRenderableWidget(Button.builder(Component.empty(), ignored -> { })
                .bounds(x, y, CREST_WIDTH, CREST_WIDTH)
                .build());
            button.active = false;
            this.crestTiles.add(new CrestTile(button, crest, x, y));
        }

        this.confirmButton = this.addRenderableWidget(Button.builder(gui("confirm"), ignored -> mint())
            .bounds(this.leftPos + 514, this.topPos + 408, 60, 36)
            .build());
        this.addRenderableWidget(Button.builder(gui("back"), ignored -> this.onClose())
            .bounds(this.leftPos + 596, this.topPos + 408, 60, 36)
            .build());
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
            this.confirmButton.active = this.selectedSymbols.size() == CoinData.REQUIRED_SECONDARY_SYMBOLS
                && KingdomCrest.isSupported(this.display.crest());
        }
    }

    private void mint() {
        if (this.selectedSymbols.size() != CoinData.REQUIRED_SECONDARY_SYMBOLS) {
            this.status = gui("select_two_symbols");
            return;
        }
        ClientPacketDistributor.sendToServer(new MintCoinPayload(
            this.menu.containerId,
            metalId(this.selectedMetal),
            previewStyleId(),
            this.selectedSymbols.stream().map(Symbol::id).toList()
        ));
        this.status = gui("mint_sent");
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int left = this.leftPos;
        int top = this.topPos;
        graphics.blit(RenderPipelines.GUI_TEXTURED, MENU_TEXTURE, left, top, 0.0F, 0.0F, SCREEN_WIDTH, SCREEN_HEIGHT, SCREEN_WIDTH, SCREEN_HEIGHT);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        renderCatalogTiles(graphics);
        renderCrestTiles(graphics);
        renderSelectionSlots(graphics, left, top);
        renderPreview(graphics, left, top);

        graphics.centeredText(this.font, this.title, left + SCREEN_WIDTH / 2, top + 12, 0xFFFFD878);
        graphics.centeredText(this.font, gui("kingdom", this.display.kingdomName()), left + SCREEN_WIDTH / 2, top + 27, 0xFFE4C67A);
        graphics.centeredText(this.font, gui("required_crest", crestName(this.display.crest())), left + SCREEN_WIDTH / 2, top + 42, 0xFFBABDC0);

        graphics.centeredText(this.font, gui("panel_bronze"), left + 160, top + 82, metalColor(Kingdom.Metal.COPPER));
        graphics.centeredText(this.font, gui("panel_iron"), left + 356, top + 82, metalColor(Kingdom.Metal.IRON));
        graphics.centeredText(this.font, gui("panel_gold"), left + 552, top + 82, metalColor(Kingdom.Metal.GOLD));
        graphics.centeredText(this.font, gui("select_symbols", CoinData.REQUIRED_SECONDARY_SYMBOLS), left + SCREEN_WIDTH / 2, top + 334, 0xFFD7D9D9);
        graphics.centeredText(this.font, gui("central_crest", KingdomCrest.values().length), left + SCREEN_WIDTH / 2, top + 382, 0xFFE4C67A);
        graphics.centeredText(this.font, gui("preview"), left + 117, top + 486, 0xFFE4C67A);
        graphics.centeredText(this.font, this.status, left + SCREEN_WIDTH / 2, top + 516, 0xFFFFD878);
    }

    private void renderCatalogTiles(GuiGraphicsExtractor graphics) {
        for (CatalogTile tile : this.catalogTiles) {
            graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                symbolTexture(tile.metal(), tile.symbol()),
                tile.x() + 9,
                tile.y() + 5,
                0.0F,
                0.0F,
                16,
                16,
                32,
                32,
                32,
                32
            );
            int border = tile.metal() == this.selectedMetal ? metalColor(tile.metal()) : 0x80626968;
            if (this.selectedMetal == tile.metal() && this.selectedSymbols.contains(tile.symbol())) {
                border = 0xFFFFFFFF;
            }
            graphics.outline(tile.x(), tile.y(), TILE_WIDTH, TILE_HEIGHT, border);
        }
    }

    private void renderCrestTiles(GuiGraphicsExtractor graphics) {
        for (CrestTile tile : this.crestTiles) {
            graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                crestTexture(tile.crest()),
                tile.x() + 5,
                tile.y() + 5,
                0.0F,
                0.0F,
                16,
                16,
                32,
                32,
                32,
                32
            );
            boolean kingdomCrest = tile.crest().symbol() == this.display.crest();
            graphics.outline(tile.x(), tile.y(), CREST_WIDTH, CREST_WIDTH, kingdomCrest ? 0xFFFFD878 : 0x805E6264);
        }
    }

    private void renderSelectionSlots(GuiGraphicsExtractor graphics, int left, int top) {
        renderSelectionSlot(graphics, left + 236, top + 344, gui("left_symbol"), symbolAt(0));
        renderSelectionSlot(graphics, left + 386, top + 344, gui("right_symbol"), symbolAt(1));
    }

    private void renderSelectionSlot(GuiGraphicsExtractor graphics, int x, int y, Component label, Component value) {
        graphics.fill(x, y, x + 134, y + 24, 0xC0101113);
        graphics.outline(x, y, 134, 24, 0xFF856539);
        graphics.centeredText(this.font, label.copy().append(Component.literal(": ")).append(value), x + 67, y + 8, 0xFFD7D9D9);
    }

    private void renderPreview(GuiGraphicsExtractor graphics, int left, int top) {
        ItemStack preview = createPreviewStack();
        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(left + 77, top + 388);
        pose.scale(5.0F);
        graphics.item(preview, 0, 0);
        pose.popMatrix();
    }

    private ItemStack createPreviewStack() {
        Kingdom.Metal metal = this.selectedMetal;
        ItemStack preview = new ItemStack(switch (metal) {
            case IRON -> CrownsCoins.IRON_COIN.get();
            case COPPER -> CrownsCoins.COPPER_COIN.get();
            case GOLD -> CrownsCoins.GOLD_COIN.get();
        });
        CoinData.Material material = switch (metal) {
            case IRON -> CoinData.Material.IRON;
            case COPPER -> CoinData.Material.COPPER;
            case GOLD -> CoinData.Material.GOLD;
        };
        String kingdomName = this.display.kingdomName().isBlank() ? "Reino" : this.display.kingdomName();
        preview.set(CrownsCoins.COIN_DATA.get(), new CoinData(
            PREVIEW_KINGDOM_ID,
            kingdomName,
            "Prévia",
            this.display.crest(),
            material,
            valueFor(metal),
            previewStyleId(),
            this.selectedSymbols
        ));
        return preview;
    }

    private int previewStyleId() {
        return this.selectedSymbols.isEmpty() ? 1 : this.selectedSymbols.getFirst().id();
    }

    private int valueFor(Kingdom.Metal metal) {
        return switch (metal) {
            case IRON -> this.display.ironValue();
            case COPPER -> this.display.copperValue();
            case GOLD -> this.display.goldValue();
        };
    }

    private Component symbolAt(int index) {
        return this.selectedSymbols.size() > index ? symbolName(this.selectedSymbols.get(index)) : gui("empty_slot");
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

    private static Identifier symbolTexture(Kingdom.Metal metal, Symbol symbol) {
        return Identifier.fromNamespaceAndPath(
            CrownsCoins.MOD_ID,
            "textures/gui/catalog/%s_%02d_%s.png".formatted(catalogMetalName(metal), symbol.id(), symbol.name().toLowerCase(Locale.ROOT))
        );
    }

    private static String catalogMetalName(Kingdom.Metal metal) {
        return switch (metal) {
            case COPPER -> "bronze";
            case IRON -> "iron";
            case GOLD -> "gold";
        };
    }

    private static Identifier crestTexture(KingdomCrest crest) {
        Symbol symbol = crest.symbol();
        return Identifier.fromNamespaceAndPath(
            CrownsCoins.MOD_ID,
            "textures/item/overlay/crest/%02d_%s.png".formatted(symbol.id(), symbol.name().toLowerCase(Locale.ROOT))
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

    private record CrestTile(Button button, KingdomCrest crest, int x, int y) {
    }
}
