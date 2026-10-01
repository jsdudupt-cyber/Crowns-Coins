package com.crownscoins.client;

import com.crownscoins.CrownsCoins;
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
    private int animationTick;

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
        drawProgress(graphics, left, top);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (this.menu.hasNuggets()) {
            this.animationTick++;
        } else {
            this.animationTick = 0;
        }
    }

    /** The art supplies the furnace; only the orange fill changes while it processes nuggets. */
    private void drawProgress(GuiGraphicsExtractor graphics, int left, int top) {
        int progress = this.menu.hasNuggets() ? 10 + (this.animationTick % 54) : 0;
        if (progress > 0) {
            graphics.fill(left + 211, top + 158, left + 211 + progress, top + 163, 0xFFFFA32B);
        }
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
