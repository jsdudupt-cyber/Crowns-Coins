package com.crownscoins.client;

import com.crownscoins.CrownsCoins;
import com.crownscoins.block.MintHouseBlockEntity;
import com.crownscoins.menu.MintFurnaceMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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

    public MintFurnaceScreen(MintFurnaceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
        this.titleLabelX = -10_000;
        this.inventoryLabelX = -10_000;
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
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        drawProgress(graphics, left, top, partialTick);
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
