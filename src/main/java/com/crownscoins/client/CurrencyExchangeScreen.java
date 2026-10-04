package com.crownscoins.client;

import com.crownscoins.CrownsCoins;
import com.crownscoins.menu.CurrencyExchangeMenu;
import com.crownscoins.menu.MintHouseMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/** A compact, metal-and-wood counter UI for the public currency exchange. */
public final class CurrencyExchangeScreen extends AbstractContainerScreen<CurrencyExchangeMenu> {
    private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath(
        CrownsCoins.MOD_ID,
        "textures/gui/currency_exchange_bg.png"
    );
    private static final int SCREEN_WIDTH = 256;
    private static final int SCREEN_HEIGHT = 282;
    private static final int EXCHANGE_INPUT_X = 48;
    private static final int EXCHANGE_OUTPUT_X = 159;
    private static final int EXCHANGE_Y = 61;
    private static final int MELT_INPUT_X = 48;
    private static final int MELT_OUTPUT_X = 159;
    private static final int MELT_Y = 124;
    private static final int PLAYER_INVENTORY_X = 47;
    private static final int PLAYER_INVENTORY_Y = 195;

    public CurrencyExchangeScreen(CurrencyExchangeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, SCREEN_WIDTH, SCREEN_HEIGHT);
        this.titleLabelX = -10_000;
        this.inventoryLabelX = -10_000;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int left = this.leftPos;
        int top = this.topPos;
        renderFrame(graphics, left, top);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        graphics.centeredText(this.font, this.title, left + SCREEN_WIDTH / 2, top + 9, 0xFFFFD878);
        graphics.centeredText(this.font, gui("exchange_coins"), left + SCREEN_WIDTH / 2, top + 31, 0xFFE4C67A);
        graphics.centeredText(this.font, gui("exchange_rule_copper"), left + SCREEN_WIDTH / 2, top + 42, 0xFFCED2D4);
        graphics.centeredText(this.font, gui("exchange_rule_iron"), left + SCREEN_WIDTH / 2, top + 52, 0xFFCED2D4);
        graphics.centeredText(this.font, gui("exchange_input"), left + EXCHANGE_INPUT_X + 8, top + 83, 0xFFE4C67A);
        graphics.centeredText(this.font, gui("exchange_output"), left + EXCHANGE_OUTPUT_X + 8, top + 83, 0xFFE4C67A);
        graphics.centeredText(this.font, "+", left + 105, top + 66, 0xFFFFD34F);
        graphics.centeredText(this.font, "→", left + 132, top + 66, 0xFFFFD34F);

        graphics.centeredText(this.font, gui("melt_coins"), left + SCREEN_WIDTH / 2, top + 103, 0xFFE4C67A);
        graphics.centeredText(this.font, gui("melt_rule", MintHouseMenu.COPPER_NUGGETS_PER_COIN, MintHouseMenu.IRON_NUGGETS_PER_COIN, MintHouseMenu.GOLD_NUGGETS_PER_COIN), left + SCREEN_WIDTH / 2, top + 113, 0xFFCED2D4);
        graphics.centeredText(this.font, gui("melt_input"), left + MELT_INPUT_X + 8, top + 146, 0xFFE4C67A);
        graphics.centeredText(this.font, gui("melt_output"), left + MELT_OUTPUT_X + 8, top + 146, 0xFFE4C67A);
        graphics.centeredText(this.font, "→", left + 105, top + 129, 0xFFFFD34F);

        graphics.centeredText(this.font, gui("exchange_identity_note"), left + SCREEN_WIDTH / 2, top + 174, 0xFFE4C67A);
        graphics.centeredText(this.font, gui("player_inventory"), left + SCREEN_WIDTH / 2, top + 184, 0xFFE4C67A);
    }

    private static void renderFrame(GuiGraphicsExtractor graphics, int left, int top) {
        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            BACKGROUND,
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

        slotFrame(graphics, left + EXCHANGE_INPUT_X, top + EXCHANGE_Y);
        slotFrame(graphics, left + EXCHANGE_OUTPUT_X, top + EXCHANGE_Y);
        slotFrame(graphics, left + MELT_INPUT_X, top + MELT_Y);
        slotFrame(graphics, left + MELT_OUTPUT_X, top + MELT_Y);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                slotFrame(graphics, left + PLAYER_INVENTORY_X + column * 18, top + PLAYER_INVENTORY_Y + row * 18);
            }
        }
        for (int column = 0; column < 9; column++) {
            slotFrame(graphics, left + PLAYER_INVENTORY_X + column * 18, top + PLAYER_INVENTORY_Y + 58);
        }
    }

    private static void slotFrame(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 19, y + 19, 0xFF151617);
        graphics.outline(x - 1, y - 1, 20, 20, 0xFF8F8F8A);
        graphics.fill(x + 1, y + 1, x + 18, y + 18, 0xFF2B2D2E);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static Component gui(String key, Object... arguments) {
        return Component.translatable("gui.crownscoins." + key, arguments);
    }
}
