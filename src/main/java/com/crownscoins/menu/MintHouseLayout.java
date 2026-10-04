package com.crownscoins.menu;

/**
 * Coordinates shared by the Mint House menu, its screen and the generated
 * background texture. Keeping them here prevents item slots from drifting
 * away from their painted frames whenever the workstation UI is redesigned.
 */
public final class MintHouseLayout {
    /** Full workstation used by the right-hand design table. */
    public static final int SCREEN_WIDTH = 480;
    public static final int SCREEN_HEIGHT = 360;


    public static final int HEADER_X = 12;
    public static final int HEADER_Y = 6;
    public static final int HEADER_WIDTH = 456;
    public static final int HEADER_HEIGHT = 44;
    /** Full painted gear plate in the player-authored 480px header artwork. */
    public static final int SETTINGS_GEAR_X = 426;
    public static final int SETTINGS_GEAR_Y = 27;
    public static final int SETTINGS_GEAR_WIDTH = 38;
    public static final int SETTINGS_GEAR_HEIGHT = 39;

    /** The design side uses base coins from the internal chest, never a loose input socket. */
    public static final int MATERIAL_PANEL_X = 12;
    public static final int MATERIAL_PANEL_Y = 56;
    public static final int MATERIAL_PANEL_WIDTH = 92;
    public static final int MATERIAL_PANEL_HEIGHT = 82;

    public static final int PREVIEW_PANEL_X = 114;
    public static final int PREVIEW_PANEL_Y = 56;
    public static final int PREVIEW_PANEL_WIDTH = 164;
    public static final int PREVIEW_PANEL_HEIGHT = 82;
    public static final int PREVIEW_CENTER_X = 196;
    public static final int PREVIEW_CENTER_Y = 97;

    /** Coordinates of the 9 by 3 chest painted in the supplied Aseprite layout. */
    public static final int COIN_CHEST_PANEL_X = 14;
    public static final int COIN_CHEST_PANEL_Y = 190;
    public static final int COIN_CHEST_PANEL_WIDTH = 296;
    public static final int COIN_CHEST_PANEL_HEIGHT = 152;
    /** First 16px item is centered in the first painted 32px arca cell. */
    public static final int COIN_STORAGE_X = 72;
    public static final int COIN_STORAGE_Y = 228;
    public static final int COIN_STORAGE_STEP = 20;

    /**
     * The former three metal cards are intentionally replaced by one clean
     * gallery. The material slot above chooses its metal automatically.
     */
    /** Twelve light choices, six on each row, over the empty central gallery. */
    public static final int SHAPE_GALLERY_X = 72;
    public static final int SHAPE_GALLERY_Y = 91;
    public static final int SHAPE_GALLERY_BUTTON_SIZE = 28;
    public static final int SHAPE_GALLERY_BUTTON_GAP = 28;
    public static final int SHAPE_GALLERY_ROW_GAP = 12;
    public static final int SHAPE_GALLERY_COLUMNS = 6;
    public static final int SHAPE_GALLERY_BUTTON_COUNT = 12;

    /** The large backpack stays hidden, but the quick-access bar is always usable. */
    /** The player-authored layout keeps the quick-access bar below the internal chest. */
    public static final int PLAYER_HOTBAR_X = 72;
    public static final int PLAYER_HOTBAR_Y = 300;
    /** Hotbar slots use the same 20px pitch as the arca grid above them. */
    public static final int PLAYER_HOTBAR_STEP = 20;

    public static final int ACTION_PANEL_X = 320;
    public static final int ACTION_PANEL_Y = 190;
    public static final int ACTION_PANEL_WIDTH = 146;
    public static final int ACTION_PANEL_HEIGHT = 152;

    public static final int CONFIRM_X = 332;
    public static final int CONFIRM_Y = 271;
    public static final int CONFIRM_WIDTH = 124;
    public static final int CONFIRM_HEIGHT = 29;
    public static final int BACK_X = 342;
    public static final int BACK_Y = 309;
    public static final int BACK_WIDTH = 104;
    public static final int BACK_HEIGHT = 25;

    private MintHouseLayout() {
    }

    public static int shapeGalleryButtonX(int index) {
        return SHAPE_GALLERY_X + (index % SHAPE_GALLERY_COLUMNS) * (SHAPE_GALLERY_BUTTON_SIZE + SHAPE_GALLERY_BUTTON_GAP);
    }

    public static int shapeGalleryButtonY(int index) {
        return SHAPE_GALLERY_Y + (index / SHAPE_GALLERY_COLUMNS) * (SHAPE_GALLERY_BUTTON_SIZE + SHAPE_GALLERY_ROW_GAP);
    }

    /** Centers a normal 16px item inside the larger painted chest recess. */
    public static int coinStorageSlotX(int slot) {
        return COIN_STORAGE_X + (slot % 9) * COIN_STORAGE_STEP;
    }

    public static int coinStorageSlotY(int slot) {
        return COIN_STORAGE_Y + (slot / 9) * COIN_STORAGE_STEP;
    }
}
