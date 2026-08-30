package com.crownscoins.client;

import com.crownscoins.coin.CoinData;
import com.crownscoins.kingdom.Kingdom;
import com.crownscoins.kingdom.Symbol;
import com.crownscoins.menu.MintHouseMenu;
import com.crownscoins.network.MintCoinPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Client-side Mint House catalogue. Every selection is sent only as bounded
 * catalog IDs; the server validates the open menu, kingdom, metal and ingot.
 */
public final class MintHouseScreen extends AbstractContainerScreen<MintHouseMenu> {
    private static final int SCREEN_WIDTH = 300;
    private static final int SCREEN_HEIGHT = 320;

    private final MintHouseMenu.ClientMintData display;
    private final List<Symbol> selectedSymbols = new ArrayList<>();
    private final List<Button> symbolButtons = new ArrayList<>();
    private Kingdom.Metal selectedMetal = Kingdom.Metal.IRON;
    private int selectedStyleId = 1;
    private Button ironButton;
    private Button copperButton;
    private Button goldButton;
    private Button styleButton;
    private Component status = Component.empty();

    public MintHouseScreen(MintHouseMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, SCREEN_WIDTH, SCREEN_HEIGHT);
        this.display = menu.clientData();
    }

    @Override
    protected void init() {
        super.init();
        int left = this.leftPos + 10;
        int top = this.topPos;

        this.ironButton = this.addRenderableWidget(Button.builder(Component.empty(), ignored -> selectMetal(Kingdom.Metal.IRON))
            .bounds(left, top + 82, 88, 20)
            .build());
        this.copperButton = this.addRenderableWidget(Button.builder(Component.empty(), ignored -> selectMetal(Kingdom.Metal.COPPER))
            .bounds(left + 96, top + 82, 88, 20)
            .build());
        this.goldButton = this.addRenderableWidget(Button.builder(Component.empty(), ignored -> selectMetal(Kingdom.Metal.GOLD))
            .bounds(left + 192, top + 82, 88, 20)
            .build());

        this.addRenderableWidget(Button.builder(gui("previous_style"), ignored -> changeStyle(-1))
            .bounds(left, top + 108, 70, 20)
            .build());
        this.styleButton = this.addRenderableWidget(Button.builder(Component.empty(), ignored -> { })
            .bounds(left + 74, top + 108, 132, 20)
            .build());
        this.styleButton.active = false;
        this.addRenderableWidget(Button.builder(gui("next_style"), ignored -> changeStyle(1))
            .bounds(left + 210, top + 108, 70, 20)
            .build());

        this.symbolButtons.clear();
        Symbol[] symbols = Symbol.values();
        int symbolTop = top + 150;
        for (int index = 0; index < symbols.length; index++) {
            Symbol symbol = symbols[index];
            int x = left + (index % 5) * 57;
            int y = symbolTop + (index / 5) * 21;
            Button button = this.addRenderableWidget(Button.builder(Component.empty(), ignored -> toggleSymbol(symbol))
                .bounds(x, y, 52, 20)
                .build());
            this.symbolButtons.add(button);
        }

        this.addRenderableWidget(Button.builder(gui("clear_symbols"), ignored -> clearSymbols())
            .bounds(left, top + 264, 84, 20)
            .build());
        this.addRenderableWidget(Button.builder(gui("mint_one"), ignored -> mint())
            .bounds(left + 92, top + 264, 110, 20)
            .build());
        this.addRenderableWidget(Button.builder(gui("close"), ignored -> this.onClose())
            .bounds(left + 210, top + 264, 70, 20)
            .build());
        this.refreshSelectionButtons();
    }

    private void selectMetal(Kingdom.Metal metal) {
        this.selectedMetal = metal;
        this.status = Component.empty();
        this.refreshSelectionButtons();
    }

    private void changeStyle(int delta) {
        this.selectedStyleId = Math.floorMod(this.selectedStyleId - 1 + delta, CoinData.MAX_STYLE_ID) + 1;
        this.status = Component.empty();
        this.refreshSelectionButtons();
    }

    private void toggleSymbol(Symbol symbol) {
        if (this.selectedSymbols.remove(symbol)) {
            this.status = Component.empty();
        } else if (this.selectedSymbols.size() < CoinData.MAX_SYMBOLS) {
            this.selectedSymbols.add(symbol);
            this.status = Component.empty();
        } else {
            this.status = gui("symbol_limit");
        }
        this.refreshSelectionButtons();
    }

    private void clearSymbols() {
        this.selectedSymbols.clear();
        this.status = Component.empty();
        this.refreshSelectionButtons();
    }

    private void refreshSelectionButtons() {
        if (this.ironButton == null) {
            return;
        }
        this.ironButton.setMessage(metalLabel(Kingdom.Metal.IRON));
        this.copperButton.setMessage(metalLabel(Kingdom.Metal.COPPER));
        this.goldButton.setMessage(metalLabel(Kingdom.Metal.GOLD));
        this.styleButton.setMessage(gui("style_count", this.selectedStyleId, CoinData.MAX_STYLE_ID));

        Symbol[] symbols = Symbol.values();
        for (int index = 0; index < this.symbolButtons.size(); index++) {
            Symbol symbol = symbols[index];
            String marker = this.selectedSymbols.contains(symbol) ? ">" : "";
            this.symbolButtons.get(index).setMessage(Component.literal(marker + shortLabel(symbol)));
        }
    }

    private Component metalLabel(Kingdom.Metal metal) {
        String marker = this.selectedMetal == metal ? "> " : "";
        return Component.literal(marker).append(Component.translatable("tooltip.crownscoins.metal." + metal.name().toLowerCase(java.util.Locale.ROOT)));
    }

    private void mint() {
        int metalId = switch (this.selectedMetal) {
            case IRON -> MintHouseMenu.IRON_METAL_ID;
            case COPPER -> MintHouseMenu.COPPER_METAL_ID;
            case GOLD -> MintHouseMenu.GOLD_METAL_ID;
        };
        ClientPacketDistributor.sendToServer(new MintCoinPayload(
            this.menu.containerId,
            metalId,
            this.selectedStyleId,
            this.selectedSymbols.stream().map(Symbol::id).toList()
        ));
        this.status = gui("mint_sent");
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int left = this.leftPos;
        int top = this.topPos;
        graphics.fill(left, top, left + SCREEN_WIDTH, top + SCREEN_HEIGHT, 0xD0181A20);
        graphics.outline(left, top, SCREEN_WIDTH, SCREEN_HEIGHT, 0xFFB89445);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(this.font, this.title, this.width / 2, top + 10, 0xFFFFD878);
        graphics.centeredText(this.font, gui("kingdom", this.display.kingdomName()), this.width / 2, top + 30, 0xFFFFD878);
        graphics.centeredText(this.font, gui("crest", symbolName(this.display.crest())), this.width / 2, top + 45, 0xFFB8B8B8);
        graphics.centeredText(this.font, gui("values", this.display.ironValue(), this.display.copperValue(), this.display.goldValue()), this.width / 2, top + 60, 0xFFB8B8B8);
        graphics.centeredText(this.font, gui("choose_symbols"), this.width / 2, top + 136, 0xFFB8B8B8);
        graphics.centeredText(this.font, selectedSymbolsText(), this.width / 2, top + 292, 0xFFB8B8B8);
        graphics.centeredText(this.font, this.status, this.width / 2, top + 306, 0xFFFFD878);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private Component selectedSymbolsText() {
        if (this.selectedSymbols.isEmpty()) {
            return gui("symbols_none");
        }
        String names = this.selectedSymbols.stream().map(MintHouseScreen::symbolName).map(Component::getString).reduce((first, second) -> first + ", " + second).orElse("");
        return gui("symbols", names);
    }

    private static Component gui(String key, Object... arguments) {
        return Component.translatable("gui.crownscoins." + key, arguments);
    }

    private static Component symbolName(Symbol symbol) {
        return Component.translatable("symbol.crownscoins." + symbol.name().toLowerCase(java.util.Locale.ROOT));
    }

    private static String shortLabel(Symbol symbol) {
        String name = symbolName(symbol).getString();
        return name.substring(0, Math.min(4, name.length()));
    }
}
