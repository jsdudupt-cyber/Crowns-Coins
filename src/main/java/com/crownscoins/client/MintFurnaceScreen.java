package com.crownscoins.client;

import com.crownscoins.CrownsCoins;
import com.crownscoins.block.MintHouseBlockEntity;
import com.crownscoins.menu.MintFurnaceMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/** Furnace view using the player-authored 480px Aseprite production-line art. */
public final class MintFurnaceScreen extends AbstractContainerScreen<MintFurnaceMenu> {
    private static final int WIDTH = 480;
    private static final int HEIGHT = 360;
    private static final Identifier FURNACE_BACKGROUND = Identifier.fromNamespaceAndPath(
        CrownsCoins.MOD_ID,
        "textures/gui/mint_furnace_user_layout.png"
    );
    private static final int SLOT_BORDER = 0xFF151617;
    private static final int SLOT_EDGE = 0xFF8F8F8A;
    private static final int SLOT_FILL = 0xFF2B2D2E;
    /** Inner area of the painted progress trough (measured from the texture): 82 x 5 px, centred in its 7 px well. */
    private static final int TROUGH_X = 200;
    private static final int TROUGH_Y = 156;
    private static final int TROUGH_WIDTH = 82;
    private static final int TROUGH_HEIGHT = 5;
    /** The two page arrows sit in the left margin of the painted arca panel, beside the 3x3 grid. */
    private static final int PAGE_BUTTON_X = 373;
    private static final int PAGE_UP_Y = 100;
    private static final int PAGE_DOWN_Y = 143;
    private static final int PAGE_BUTTON_SIZE = 13;
    /** The painted arca panel, for mouse-wheel scrolling over it. */
    private static final int ARCA_PANEL_X = 371;
    private static final int ARCA_PANEL_Y = 92;
    private static final int ARCA_PANEL_WIDTH = 86;
    private static final int ARCA_PANEL_HEIGHT = 76;
    private Button pageUpButton;
    private Button pageDownButton;

    public MintFurnaceScreen(MintFurnaceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
        this.titleLabelX = -10_000;
        this.inventoryLabelX = -10_000;
    }

    @Override
    protected void init() {
        super.init();
        this.pageUpButton = this.addRenderableWidget(pageButton(PAGE_UP_Y, -1, Component.translatable("gui.crownscoins.chest_page_previous")));
        this.pageDownButton = this.addRenderableWidget(pageButton(PAGE_DOWN_Y, 1, Component.translatable("gui.crownscoins.chest_page_next")));
        this.refreshPageButtons();
    }

    /** An invisible clickable area; its face is drawn by {@link #renderPageControls}. */
    private Button pageButton(int y, int step, Component tooltip) {
        Button button = Button.builder(Component.empty(), ignored -> changePage(step))
            .bounds(this.leftPos + PAGE_BUTTON_X, this.topPos + y, PAGE_BUTTON_SIZE, PAGE_BUTTON_SIZE)
            .tooltip(Tooltip.create(tooltip))
            .build();
        button.setAlpha(0.0F);
        return button;
    }

    private void changePage(int step) {
        this.menu.setPage(this.menu.page() + step);
        this.refreshPageButtons();
    }

    private void refreshPageButtons() {
        if (this.pageUpButton != null) {
            this.pageUpButton.active = this.menu.page() > 0;
        }
        if (this.pageDownButton != null) {
            this.pageDownButton.active = this.menu.page() < MintFurnaceMenu.CHEST_PAGE_COUNT - 1;
        }
    }

    /** Scrolling the mouse wheel over the arca panel turns its pages. */
    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        boolean overArca = x >= this.leftPos + ARCA_PANEL_X && x < this.leftPos + ARCA_PANEL_X + ARCA_PANEL_WIDTH
            && y >= this.topPos + ARCA_PANEL_Y && y < this.topPos + ARCA_PANEL_Y + ARCA_PANEL_HEIGHT;
        if (overArca && scrollY != 0.0) {
            changePage(scrollY > 0.0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int left = this.leftPos;
        int top = this.topPos;
        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            FURNACE_BACKGROUND,
            left,
            top,
            0.0F,
            0.0F,
            WIDTH,
            HEIGHT,
            WIDTH,
            HEIGHT,
            WIDTH,
            HEIGHT
        );
        renderLiveSlotFrames(graphics, left, top);
        renderPageControls(graphics, left, top);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        drawProgress(graphics, left, top, partialTick);
    }

    /** Draws the two arrow buttons (dimmed when there is no page that way) and the "page/pages" label. */
    private void renderPageControls(GuiGraphicsExtractor graphics, int left, int top) {
        renderPageArrow(graphics, left + PAGE_BUTTON_X, top + PAGE_UP_Y, true, this.menu.page() > 0);
        renderPageArrow(graphics, left + PAGE_BUTTON_X, top + PAGE_DOWN_Y, false, this.menu.page() < MintFurnaceMenu.CHEST_PAGE_COUNT - 1);
        Component label = Component.literal((this.menu.page() + 1) + "/" + MintFurnaceMenu.CHEST_PAGE_COUNT);
        int centerX = left + MintFurnaceMenu.CHEST_SLOT_X + MintFurnaceMenu.CHEST_SLOT_X_STEP + 8;
        int x = centerX - this.font.width(label) / 2;
        graphics.text(this.font, label, x + 1, top + 160, 0xFF000000);
        graphics.text(this.font, label, x, top + 159, 0xFFFFD34F);
    }

    private void renderPageArrow(GuiGraphicsExtractor graphics, int x, int y, boolean up, boolean enabled) {
        int size = PAGE_BUTTON_SIZE;
        graphics.fill(x, y, x + size, y + size, 0xFF151617);
        graphics.outline(x, y, size, size, enabled ? 0xFFB0843C : 0xFF5A544C);
        graphics.fill(x + 1, y + 1, x + size - 1, y + size - 1, 0xFF34281C);
        int color = enabled ? 0xFFFFD34F : 0xFF78643C;
        // A small triangle drawn row by row: three rows tall is enough at this size.
        for (int row = 0; row < 4; row++) {
            int half = up ? row : 3 - row;
            int rowY = y + 4 + row + (up ? 0 : 1);
            graphics.fill(x + 6 - half, rowY, x + 7 + half, rowY + 1, color);
        }
    }

    /**
     * Fills the painted trough with the furnace's real progress for the coin being
     * smelted. It only shows while the furnace is working, and the partial tick makes
     * the fill glide instead of stepping once per game tick.
     */
    private void drawProgress(GuiGraphicsExtractor graphics, int left, int top, float partialTick) {
        if (!this.menu.isWorking()) {
            return;
        }
        float fraction = Math.min(1.0F, (this.menu.progressTicks() + partialTick) / MintHouseBlockEntity.FURNACE_TICKS_PER_COIN);
        int width = Math.round(TROUGH_WIDTH * fraction);
        if (width <= 0) {
            return;
        }
        int x = left + TROUGH_X;
        int y = top + TROUGH_Y;
        graphics.fill(x, y, x + width, y + TROUGH_HEIGHT, 0xFFFFA32B);
        graphics.fill(x, y, x + width, y + 1, 0xFFFFD27A);
        graphics.fill(x, y + TROUGH_HEIGHT - 1, x + width, y + TROUGH_HEIGHT, 0xFFD9741A);
    }

    /**
     * The artwork uses large decorative compartments.  Minecraft containers
     * interact with 16px item areas, so paint a real 18px slot frame at the
     * same coordinates.  What the player sees is now exactly what is hoverable.
     */
    private void renderLiveSlotFrames(GuiGraphicsExtractor graphics, int left, int top) {
        renderSlotFrame(graphics, left + MintFurnaceMenu.INPUT_SLOT_X, top + MintFurnaceMenu.INPUT_SLOT_Y);
        for (int slot = 0; slot < 9; slot++) {
            renderSlotFrame(graphics, left + MintFurnaceMenu.chestSlotX(slot), top + MintFurnaceMenu.chestSlotY(slot));
        }
        for (int column = 0; column < 9; column++) {
            renderSlotFrame(graphics, left + MintFurnaceMenu.playerSlotX(column), top + MintFurnaceMenu.PLAYER_ROW_ONE_Y);
            renderSlotFrame(graphics, left + MintFurnaceMenu.playerSlotX(column), top + MintFurnaceMenu.PLAYER_ROW_TWO_Y);
            renderSlotFrame(graphics, left + MintFurnaceMenu.playerSlotX(column), top + MintFurnaceMenu.PLAYER_HOTBAR_Y);
        }
    }

    private void renderSlotFrame(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, SLOT_BORDER);
        graphics.outline(x - 1, y - 1, 18, 18, SLOT_EDGE);
        graphics.fill(x + 1, y + 1, x + 16, y + 16, SLOT_FILL);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
