package com.crownscoins.menu;

/**
 * Coordinates shared by the Mint House menu, its screen and the generated
 * background texture. Keeping them here prevents item slots from drifting
 * away from their painted frames whenever the workstation UI is redesigned.
 */
public final class MintHouseLayout {
    public static final int SCREEN_WIDTH = 720;
    public static final int SCREEN_HEIGHT = 540;

    public static final int HEADER_X = 20;
    public static final int HEADER_Y = 8;
    public static final int HEADER_WIDTH = 680;
    public static final int HEADER_HEIGHT = 58;

    public static final int MATERIAL_PANEL_X = 20;
    public static final int MATERIAL_PANEL_Y = 78;
    public static final int MATERIAL_PANEL_WIDTH = 164;
    public static final int MATERIAL_PANEL_HEIGHT = 108;
    public static final int MATERIAL_SLOT_X = 94;
    public static final int MATERIAL_SLOT_Y = 121;

    public static final int PREVIEW_PANEL_X = 194;
    public static final int PREVIEW_PANEL_Y = 78;
    public static final int PREVIEW_PANEL_WIDTH = 298;
    public static final int PREVIEW_PANEL_HEIGHT = 108;
    public static final int PREVIEW_CENTER_X = 343;
    public static final int PREVIEW_CENTER_Y = 137;

    public static final int COIN_CHEST_PANEL_X = 502;
    public static final int COIN_CHEST_PANEL_Y = 78;
    public static final int COIN_CHEST_PANEL_WIDTH = 198;
    public static final int COIN_CHEST_PANEL_HEIGHT = 108;
    public static final int COIN_STORAGE_X = 520;
    public static final int COIN_STORAGE_Y = 106;

    public static final int METAL_CARD_Y = 198;
    public static final int METAL_CARD_WIDTH = 214;
    public static final int METAL_CARD_HEIGHT = 80;
    public static final int[] METAL_CARD_X = {20, 253, 486};

    public static final int INVENTORY_PANEL_X = 20;
    public static final int INVENTORY_PANEL_Y = 294;
    public static final int INVENTORY_PANEL_WIDTH = 480;
    public static final int INVENTORY_PANEL_HEIGHT = 233;
    public static final int PLAYER_INVENTORY_X = 179;
    public static final int PLAYER_INVENTORY_Y = 338;
    public static final int PLAYER_HOTBAR_Y = 398;

    public static final int ACTION_PANEL_X = 510;
    public static final int ACTION_PANEL_Y = 294;
    public static final int ACTION_PANEL_WIDTH = 190;
    public static final int ACTION_PANEL_HEIGHT = 233;
    public static final int CONFIRM_X = 526;
    public static final int CONFIRM_Y = 466;
    public static final int CONFIRM_WIDTH = 164;
    public static final int CONFIRM_HEIGHT = 32;
    public static final int BACK_X = 547;
    public static final int BACK_Y = 505;
    public static final int BACK_WIDTH = 122;
    public static final int BACK_HEIGHT = 18;

    private MintHouseLayout() {
    }

    public static int metalCardX(int index) {
        return METAL_CARD_X[index];
    }
}
