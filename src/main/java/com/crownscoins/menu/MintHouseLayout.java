package com.crownscoins.menu;

/**
 * Coordinates shared by the Mint House menu, its screen and the generated
 * background texture. Keeping them here prevents item slots from drifting
 * away from their painted frames whenever the workstation UI is redesigned.
 */
public final class MintHouseLayout {
    public static final int SCREEN_WIDTH = 480;
    public static final int SCREEN_HEIGHT = 360;

    public static final int HEADER_X = 12;
    public static final int HEADER_Y = 6;
    public static final int HEADER_WIDTH = 456;
    public static final int HEADER_HEIGHT = 44;

    public static final int MATERIAL_PANEL_X = 12;
    public static final int MATERIAL_PANEL_Y = 56;
    public static final int MATERIAL_PANEL_WIDTH = 92;
    public static final int MATERIAL_PANEL_HEIGHT = 82;
    public static final int MATERIAL_SLOT_X = 49;
    public static final int MATERIAL_SLOT_Y = 82;

    public static final int PREVIEW_PANEL_X = 114;
    public static final int PREVIEW_PANEL_Y = 56;
    public static final int PREVIEW_PANEL_WIDTH = 164;
    public static final int PREVIEW_PANEL_HEIGHT = 82;
    public static final int PREVIEW_CENTER_X = 196;
    public static final int PREVIEW_CENTER_Y = 97;

    public static final int COIN_CHEST_PANEL_X = 288;
    public static final int COIN_CHEST_PANEL_Y = 56;
    public static final int COIN_CHEST_PANEL_WIDTH = 180;
    public static final int COIN_CHEST_PANEL_HEIGHT = 82;
    public static final int COIN_STORAGE_X = 297;
    public static final int COIN_STORAGE_Y = 76;

    public static final int METAL_CARD_Y = 146;
    public static final int METAL_CARD_WIDTH = 144;
    public static final int METAL_CARD_HEIGHT = 62;
    public static final int[] METAL_CARD_X = {12, 168, 324};

    public static final int INVENTORY_PANEL_X = 12;
    public static final int INVENTORY_PANEL_Y = 218;
    public static final int INVENTORY_PANEL_WIDTH = 232;
    public static final int INVENTORY_PANEL_HEIGHT = 130;
    public static final int PLAYER_INVENTORY_X = 48;
    public static final int PLAYER_INVENTORY_Y = 252;
    public static final int PLAYER_HOTBAR_Y = 318;

    public static final int ACTION_PANEL_X = 254;
    public static final int ACTION_PANEL_Y = 218;
    public static final int ACTION_PANEL_WIDTH = 214;
    public static final int ACTION_PANEL_HEIGHT = 130;
    public static final int CONFIRM_X = 270;
    public static final int CONFIRM_Y = 294;
    public static final int CONFIRM_WIDTH = 182;
    public static final int CONFIRM_HEIGHT = 30;
    public static final int BACK_X = 307;
    public static final int BACK_Y = 329;
    public static final int BACK_WIDTH = 108;
    public static final int BACK_HEIGHT = 16;

    private MintHouseLayout() {
    }

    public static int metalCardX(int index) {
        return METAL_CARD_X[index];
    }
}
