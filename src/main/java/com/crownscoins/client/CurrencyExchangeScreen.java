package com.crownscoins.client;

import com.crownscoins.CrownsCoins;
import com.crownscoins.coin.CoinWallet;
import com.crownscoins.menu.CurrencyExchangeMenu;
import com.crownscoins.menu.MintHouseMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * A compact, metal-and-wood counter UI for the currency exchange. Two tabs share the
 * window: "Trocar" converts and melts coins, "Loja" lists what a neighbouring chest sells.
 */
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

    /** The two tabs sit in the wooden bar on either side of the title plaque. */
    private static final int TAB_Y = 5;
    private static final int TAB_WIDTH = 54;
    private static final int TAB_HEIGHT = 15;
    private static final int TAB_EXCHANGE_X = 14;
    private static final int TAB_SHOP_X = 188;

    /** The shop list covers the two upper painted panels. */
    private static final int SHOP_PANEL_LEFT = 12;
    private static final int SHOP_PANEL_TOP = 24;
    private static final int SHOP_PANEL_RIGHT = 244;
    private static final int SHOP_PANEL_BOTTOM = 164;
    private static final int ROW_X = 14;
    private static final int ROW_WIDTH = 208;
    private static final int ROW_HEIGHT = 18;
    private static final int ROW_TOP = 41;
    private static final int ROW_BUY_X = 176;
    private static final int ROW_BUY_WIDTH = 44;
    private static final int PAGE_X = 225;
    private static final int PAGE_SIZE = 14;
    private static final int EDITOR_TEXT_Y = 139;
    private static final int EDITOR_BUTTONS_Y = 150;
    private static final int EDITOR_BUTTON_HEIGHT = 12;
    private static final int FOOTER_TEXT_Y = 175;
    private static final int FOOTER_BUTTON_Y = 171;

    private static final int GOLD = 0xFFFFD878;
    private static final int GOLD_SOFT = 0xFFE4C67A;
    private static final int GREY = 0xFFCED2D4;
    private static final int GREY_DIM = 0xFF8A8C8E;

    /** Every clickable area is an invisible vanilla button; its face is painted by {@link #paintFaces}. */
    private record Face(Button button, Component label) {
    }

    private final List<Face> faces = new ArrayList<>();
    private Button tabExchange;
    private Button tabShop;
    private final Button[] buy = new Button[CurrencyExchangeMenu.SHOP_ROWS];
    private final Button[] select = new Button[CurrencyExchangeMenu.SHOP_ROWS];
    private final List<Button> priceButtons = new ArrayList<>();
    private Button pageUp;
    private Button pageDown;
    private Button withdraw;

    public CurrencyExchangeScreen(CurrencyExchangeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, SCREEN_WIDTH, SCREEN_HEIGHT);
        this.titleLabelX = -10_000;
        this.inventoryLabelX = -10_000;
    }

    @Override
    protected void init() {
        super.init();
        this.faces.clear();
        this.priceButtons.clear();

        this.tabExchange = this.hit(TAB_EXCHANGE_X, TAB_Y, TAB_WIDTH, TAB_HEIGHT, gui("tab_exchange"), () -> this.menu.setShopTab(false));
        this.tabShop = this.hit(TAB_SHOP_X, TAB_Y, TAB_WIDTH, TAB_HEIGHT, gui("tab_shop"), () -> this.menu.setShopTab(true));

        for (int row = 0; row < CurrencyExchangeMenu.SHOP_ROWS; row++) {
            int top = rowTop(row);
            int id = row;
            this.select[row] = this.hitSilent(ROW_X, top, ROW_BUY_X - ROW_X - 2, ROW_HEIGHT, () -> this.press(CurrencyExchangeMenu.BUTTON_SELECT + id));
            this.buy[row] = this.hit(ROW_BUY_X, top + 3, ROW_BUY_WIDTH, 12, gui("shop_buy"), () -> this.press(CurrencyExchangeMenu.BUTTON_BUY + id));
        }

        int[] widths = {28, 26, 22, 22, 26, 28, 48};
        int[] ids = {
            CurrencyExchangeMenu.BUTTON_PRICE_MINUS_500, CurrencyExchangeMenu.BUTTON_PRICE_MINUS_20, CurrencyExchangeMenu.BUTTON_PRICE_MINUS_1,
            CurrencyExchangeMenu.BUTTON_PRICE_PLUS_1, CurrencyExchangeMenu.BUTTON_PRICE_PLUS_20, CurrencyExchangeMenu.BUTTON_PRICE_PLUS_500,
            CurrencyExchangeMenu.BUTTON_PRICE_CLEAR
        };
        Component[] labels = {
            Component.literal("-500"), Component.literal("-20"), Component.literal("-1"),
            Component.literal("+1"), Component.literal("+20"), Component.literal("+500"), gui("shop_clear")
        };
        int x = ROW_X;
        for (int i = 0; i < ids.length; i++) {
            int id = ids[i];
            this.priceButtons.add(this.hit(x, EDITOR_BUTTONS_Y, widths[i], EDITOR_BUTTON_HEIGHT, labels[i], () -> this.press(id)));
            x += widths[i] + 1;
        }

        this.pageUp = this.hit(PAGE_X, ROW_TOP, PAGE_SIZE, PAGE_SIZE, Component.literal("▲"), () -> this.press(CurrencyExchangeMenu.BUTTON_PAGE_PREVIOUS));
        this.pageDown = this.hit(PAGE_X, ROW_TOP + 5 * (ROW_HEIGHT + 1) - 1 - PAGE_SIZE, PAGE_SIZE, PAGE_SIZE, Component.literal("▼"), () -> this.press(CurrencyExchangeMenu.BUTTON_PAGE_NEXT));
        this.withdraw = this.hit(186, FOOTER_BUTTON_Y, 52, 13, gui("shop_withdraw"), () -> this.press(CurrencyExchangeMenu.BUTTON_WITHDRAW));
    }

    private static int rowTop(int row) {
        return ROW_TOP + row * (ROW_HEIGHT + 1);
    }

    /** A clickable area whose face is painted by the screen. */
    private Button hit(int x, int y, int width, int height, Component label, Runnable action) {
        Button button = this.hitSilent(x, y, width, height, action);
        this.faces.add(new Face(button, label));
        return button;
    }

    /** A clickable area with no painted face. */
    private Button hitSilent(int x, int y, int width, int height, Runnable action) {
        Button button = Button.builder(Component.empty(), ignored -> action.run())
            .bounds(this.leftPos + x, this.topPos + y, width, height)
            .build();
        button.setAlpha(0.0F);
        return this.addRenderableWidget(button);
    }

    /** Sends one shop action to the server, which decides whether it is allowed. */
    private void press(int id) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
        }
    }

    private void updateWidgets() {
        boolean shop = this.menu.isShopTab();
        boolean bound = this.menu.hasShopKingdom();
        boolean member = this.menu.isShopMember();
        this.tabExchange.active = shop;
        this.tabShop.active = !shop;
        for (int row = 0; row < CurrencyExchangeMenu.SHOP_ROWS; row++) {
            boolean filled = shop && bound && !this.menu.rowItem(row).isEmpty();
            this.select[row].visible = filled && member;
            this.buy[row].visible = filled && this.menu.rowPrice(row) > 0;
        }
        boolean editing = shop && bound && member && this.menu.selectedRow() >= 0;
        for (Button button : this.priceButtons) {
            button.visible = editing;
        }
        boolean paged = shop && bound && this.menu.shopPageCount() > 1;
        this.pageUp.visible = paged;
        this.pageUp.active = this.menu.shopPage() > 0;
        this.pageDown.visible = paged;
        this.pageDown.active = this.menu.shopPage() < this.menu.shopPageCount() - 1;
        this.withdraw.visible = shop && bound && member;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.updateWidgets();
        int left = this.leftPos;
        int top = this.topPos;
        renderFrame(graphics, left, top);
        if (this.menu.isShopTab()) {
            this.renderShop(graphics, left, top);
        } else {
            this.renderExchangeTexts(graphics, left, top);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(this.font, this.title, left + SCREEN_WIDTH / 2, top + 9, 0xFFFFD878);
        this.paintFaces(graphics, mouseX, mouseY);
    }

    private void renderExchangeTexts(GuiGraphicsExtractor graphics, int left, int top) {
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

    /** The "Loja" tab: header, one row per shelf item, the price editor for members and the footer. */
    private void renderShop(GuiGraphicsExtractor graphics, int left, int top) {
        graphics.fill(left + SHOP_PANEL_LEFT, top + SHOP_PANEL_TOP, left + SHOP_PANEL_RIGHT, top + SHOP_PANEL_BOTTOM, 0xFF1A1C1D);
        graphics.outline(left + SHOP_PANEL_LEFT, top + SHOP_PANEL_TOP, SHOP_PANEL_RIGHT - SHOP_PANEL_LEFT, SHOP_PANEL_BOTTOM - SHOP_PANEL_TOP, 0xFF9A6A2A);

        if (!this.menu.hasShopKingdom()) {
            graphics.centeredText(this.font, gui("shop_no_kingdom_1"), left + SCREEN_WIDTH / 2, top + 70, GOLD_SOFT);
            graphics.centeredText(this.font, gui("shop_no_kingdom_2"), left + SCREEN_WIDTH / 2, top + 84, GREY);
            return;
        }

        graphics.centeredText(this.font, gui("shop_header", this.menu.shopKingdomName()), left + SCREEN_WIDTH / 2, top + 29, GOLD);

        boolean anyRow = false;
        for (int row = 0; row < CurrencyExchangeMenu.SHOP_ROWS; row++) {
            if (this.menu.rowItem(row).isEmpty()) {
                continue;
            }
            anyRow = true;
            this.renderRow(graphics, left, top, row);
        }
        if (!anyRow) {
            graphics.centeredText(this.font, gui(this.menu.isShopMember() ? "shop_empty_member" : "shop_empty_buyer"), left + SCREEN_WIDTH / 2, top + 80, GREY);
        }
        if (this.menu.shopPageCount() > 1) {
            Component page = Component.literal((this.menu.shopPage() + 1) + "/" + this.menu.shopPageCount());
            graphics.centeredText(this.font, page, left + PAGE_X + PAGE_SIZE / 2, top + 84, GOLD_SOFT);
        }

        if (this.menu.isShopMember()) {
            this.renderEditor(graphics, left, top);
            Component takings = Component.translatable("gui.crownscoins.shop_till", CurrencyExchangeMenu.coinsText(this.menu.till()));
            graphics.text(this.font, takings, left + 18, top + FOOTER_TEXT_Y, GOLD_SOFT);
        } else {
            CoinWallet.Payment purse = this.purse();
            Component carried = Component.translatable("gui.crownscoins.shop_you_have", CurrencyExchangeMenu.coinsText(purse));
            graphics.text(this.font, carried, left + 18, top + FOOTER_TEXT_Y, GOLD_SOFT);
        }
    }

    private void renderRow(GuiGraphicsExtractor graphics, int left, int top, int row) {
        int x = left + ROW_X;
        int y = top + rowTop(row);
        boolean selected = this.menu.isShopMember() && this.menu.selectedRow() == row;
        graphics.fill(x, y, x + ROW_WIDTH, y + ROW_HEIGHT, 0xFF2B2D2E);
        graphics.outline(x, y, ROW_WIDTH, ROW_HEIGHT, selected ? 0xFFFFD34F : 0xFF151617);
        // The item itself is the slot at (SHOP_ROW_X, ...): draw its frame under the picture.
        graphics.fill(x + 1, y + 1, x + 19, y + 17, 0xFF1B1C1D);

        Component name = Component.translatable("gui.crownscoins.shop_item_line", this.menu.rowItem(row).getHoverName(), this.menu.rowStock(row));
        graphics.text(this.font, name, x + 24, y + 1, 0xFFE8E8E8);
        int price = this.menu.rowPrice(row);
        if (price > 0) {
            graphics.text(this.font, CurrencyExchangeMenu.priceText(price), x + 24, y + 9, 0xFFFFD34F);
        } else {
            graphics.text(this.font, gui("shop_no_price"), x + 24, y + 9, GREY_DIM);
        }
    }

    private void renderEditor(GuiGraphicsExtractor graphics, int left, int top) {
        int row = this.menu.selectedRow();
        Component line;
        if (row < 0) {
            line = gui("shop_pick_item");
        } else if (this.menu.rowPrice(row) > 0) {
            line = Component.translatable("gui.crownscoins.shop_price_line", CurrencyExchangeMenu.priceText(this.menu.rowPrice(row)));
        } else {
            line = Component.translatable("gui.crownscoins.shop_price_line", gui("shop_no_price"));
        }
        graphics.centeredText(this.font, line, left + 14 + ROW_WIDTH / 2, top + EDITOR_TEXT_Y, row < 0 ? GREY : GOLD);
    }

    /** What the player carries, in the shop's currency only. */
    private CoinWallet.Payment purse() {
        if (this.minecraft == null || this.minecraft.player == null || this.menu.shopKingdomId() == null) {
            return new CoinWallet.Payment(0, 0, 0);
        }
        for (CoinWallet.Holding holding : CoinWallet.holdings(this.minecraft.player.getInventory())) {
            if (holding.kingdomId().equals(this.menu.shopKingdomId())) {
                return new CoinWallet.Payment(holding.gold(), holding.iron(), holding.copper());
            }
        }
        return new CoinWallet.Payment(0, 0, 0);
    }

    private void paintFaces(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        boolean shop = this.menu.isShopTab();
        long carried = this.purse().value();
        for (Face face : this.faces) {
            Button button = face.button();
            if (!button.visible) {
                continue;
            }
            boolean hovered = mouseX >= button.getX() && mouseX < button.getX() + button.getWidth()
                && mouseY >= button.getY() && mouseY < button.getY() + button.getHeight();
            boolean enabled = button.active;
            if (shop && isBuyButton(button) && !this.canAffordRow(button, carried)) {
                enabled = false;
            }
            int x = button.getX();
            int y = button.getY();
            int w = button.getWidth();
            int h = button.getHeight();
            boolean tab = button == this.tabExchange || button == this.tabShop;
            // A tab is "active" in the vanilla sense when it can be clicked, so the shown one is the disabled one.
            boolean current = tab && !button.active;
            int fill = current ? 0xFF6B4A22 : (hovered && enabled ? 0xFF5E4220 : (enabled ? 0xFF3A2A18 : 0xFF2B2018));
            graphics.fill(x, y, x + w, y + h, 0xFF151617);
            graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
            graphics.outline(x, y, w, h, current || enabled ? 0xFFB0843C : 0xFF5A544C);
            int color = current ? GOLD : (enabled ? GOLD : 0xFF78643C);
            graphics.centeredText(this.font, face.label(), x + w / 2, y + (h - 8) / 2 + 1, color);
        }
    }

    private boolean isBuyButton(Button button) {
        for (Button candidate : this.buy) {
            if (candidate == button) {
                return true;
            }
        }
        return false;
    }

    /** Greys the buy button when the player's coins in this currency add up to less than the price. */
    private boolean canAffordRow(Button button, long carried) {
        for (int row = 0; row < this.buy.length; row++) {
            if (this.buy[row] == button) {
                return carried >= this.menu.rowPrice(row);
            }
        }
        return true;
    }

    private void renderFrame(GuiGraphicsExtractor graphics, int left, int top) {
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

        if (!this.menu.isShopTab()) {
            slotFrame(graphics, left + EXCHANGE_INPUT_X, top + EXCHANGE_Y);
            slotFrame(graphics, left + EXCHANGE_OUTPUT_X, top + EXCHANGE_Y);
            slotFrame(graphics, left + MELT_INPUT_X, top + MELT_Y);
            slotFrame(graphics, left + MELT_OUTPUT_X, top + MELT_Y);
        }
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
