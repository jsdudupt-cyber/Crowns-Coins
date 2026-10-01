package com.crownscoins.client;

import com.crownscoins.CrownsCoins;
import com.crownscoins.kingdom.Kingdom;
import com.crownscoins.kingdom.KingdomCrest;
import com.crownscoins.kingdom.Symbol;
import com.crownscoins.menu.MintHouseLayout;
import com.crownscoins.menu.MintHouseMenu;
import com.crownscoins.network.MintCoinPayload;
import com.crownscoins.network.UpdateCurrencyNamePayload;
import com.crownscoins.network.UpdateKingdomNamePayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Client view for the Mint House Coin Forge.
 *
 * <p>Its background deliberately contains only fixed decoration. All real
 * slots still belong to {@link MintHouseMenu}, so material handling, the coin
 * chest and the player's inventory remain ordinary Minecraft interactions.</p>
 */
public final class MintHouseScreen extends AbstractContainerScreen<MintHouseMenu> {
    private static final int SCREEN_WIDTH = MintHouseLayout.SCREEN_WIDTH;
    private static final int SCREEN_HEIGHT = MintHouseLayout.SCREEN_HEIGHT;
    private static final Identifier PRESS_BACKGROUND = Identifier.fromNamespaceAndPath(
        CrownsCoins.MOD_ID,
        "textures/gui/mint_house_press.png"
    );
    /** The player-supplied 480x360 Aseprite art is the design-side background. */
    private static final Identifier DESIGN_BACKGROUND = Identifier.fromNamespaceAndPath(
        CrownsCoins.MOD_ID,
        "textures/gui/mint_house_user_layout.png"
    );
    /** Keep the live coin small enough that the forge animation stays legible. */
    private static final int PREVIEW_SIZE = 36;
    private static final int MINT_ANIMATION_TICKS = 20;

    private static final int PANEL_INNER = 0xFF17191A;
    private static final int BORDER_DARK = 0xFF201A14;
    private static final int BORDER = 0xFF8A663B;
    private static final int GOLD_DARK = 0xFF9E6A1E;
    private static final int GOLD = 0xFFFFD34F;
    private static final int TEXT = 0xFFD7D9D9;
    private static final int SUBTLE_TEXT = 0xFFA8A49B;
    private static final int WOOD_DARK = 0xFF3A2415;
    private static final int WOOD = 0xFF68421F;
    private static final int WOOD_LIGHT = 0xFF9A6934;
    private static final int IRON = 0xFF4B4B49;
    private static final int SLOT_DARK = 0xFF101112;
    private static final int SLOT_INNER = 0xFF252627;
    /**
     * Asset contract for form selection.  The icon row uses
     * textures/item/shape/shape_01.png through shape_10.png; the live preview
     * uses the matching 16px metal sprite under textures/item/coin_shape/.
     */
    private static final String[] SHAPE_TEXTURE_SUFFIXES = {
        "round",
        "octagon",
        "square_hole",
        "scalloped",
        "hexagon",
        "chipped",
        "oval",
        "shield",
        "triangle",
        "dodecagon_chipped",
        "royal_11",
        "royal_12"
    };

    private final MintHouseMenu.ClientMintData display;
    /** The left physical half is a purpose-built compact press screen. */
    private final boolean pressScreen;
    private final List<ShapeButton> shapeButtons = new ArrayList<>();
    private Kingdom.Metal selectedMetal = Kingdom.Metal.COPPER;
    /** Null until the synchronized input socket holds a supported nugget. */
    private Kingdom.Metal detectedMetal;
    /** One-based because that is the stable ID sent to the minting payload. */
    private int selectedShape = 1;
    /** Client-only, short forge animation started after a valid mint click. */
    private int mintAnimationTicks;
    private Button confirmButton;
    private Button backButton;
    private Button mintTabButton;
    private Button currencyTabButton;
    private Button saveCurrencyButton;
    private Button saveKingdomNameButton;
    private EditBox currencyNameField;
    private EditBox kingdomNameField;
    private boolean currencyTabOpen;
    private Component status = Component.empty();

    public MintHouseScreen(MintHouseMenu menu, Inventory inventory, Component title) {
        super(
            menu,
            inventory,
            title,
            menu.clientData().openDesignAtStart() ? SCREEN_WIDTH : MintHouseLayout.PRESS_SCREEN_WIDTH,
            menu.clientData().openDesignAtStart() ? SCREEN_HEIGHT : MintHouseLayout.PRESS_SCREEN_HEIGHT
        );
        this.display = menu.clientData();
        this.pressScreen = !this.display.openDesignAtStart();
        this.titleLabelX = -10_000;
        this.inventoryLabelX = -10_000;
    }

    @Override
    protected void init() {
        super.init();

        if (this.pressScreen) {
            this.confirmButton = this.addRenderableWidget(invisible(Button.builder(Component.empty(), ignored -> mint())
                .bounds(
                    this.leftPos + MintHouseLayout.PRESS_CONFIRM_X,
                    this.topPos + MintHouseLayout.PRESS_CONFIRM_Y,
                    MintHouseLayout.PRESS_CONFIRM_WIDTH,
                    MintHouseLayout.PRESS_CONFIRM_HEIGHT
                )
                .build()));
            this.backButton = this.addRenderableWidget(invisible(Button.builder(Component.empty(), ignored -> this.onClose())
                .bounds(
                    this.leftPos + MintHouseLayout.PRESS_BACK_X,
                    this.topPos + MintHouseLayout.PRESS_BACK_Y,
                    MintHouseLayout.PRESS_BACK_WIDTH,
                    MintHouseLayout.PRESS_BACK_HEIGHT
                )
                .build()));
            this.refreshDetectedMetal();
            this.refreshSelectionState();
            return;
        }

        this.shapeButtons.clear();
        for (int index = 0; index < MintHouseLayout.SHAPE_GALLERY_BUTTON_COUNT; index++) {
            int shape = index + 1;
            Button shapeButton = this.addRenderableWidget(invisible(Button.builder(
                    Component.empty(),
                    ignored -> selectShape(shape)
                )
                .bounds(
                    this.leftPos + MintHouseLayout.shapeGalleryButtonX(index),
                    this.topPos + MintHouseLayout.shapeGalleryButtonY(index),
                    MintHouseLayout.SHAPE_GALLERY_BUTTON_SIZE,
                    MintHouseLayout.SHAPE_GALLERY_BUTTON_SIZE
                )
                .tooltip(Tooltip.create(gui("coin_shape." + shape)))
                .build()));
            this.shapeButtons.add(new ShapeButton(shapeButton, shape));
        }

        this.confirmButton = this.addRenderableWidget(invisible(Button.builder(Component.empty(), ignored -> mint())
            .bounds(
                this.leftPos + MintHouseLayout.CONFIRM_X,
                this.topPos + MintHouseLayout.CONFIRM_Y,
                MintHouseLayout.CONFIRM_WIDTH,
                MintHouseLayout.CONFIRM_HEIGHT
            )
            .build()));
        this.backButton = this.addRenderableWidget(invisible(Button.builder(Component.empty(), ignored -> {
                if (!this.currencyTabOpen) {
                    this.setCurrencyTab(true);
                } else {
                    this.onClose();
                }
            })
            .bounds(
                this.leftPos + MintHouseLayout.BACK_X,
                this.topPos + MintHouseLayout.BACK_Y,
                MintHouseLayout.BACK_WIDTH,
                MintHouseLayout.BACK_HEIGHT
            )
            .build()));

        if (this.display.canEditCurrency()) {
            this.mintTabButton = this.addRenderableWidget(invisible(Button.builder(Component.empty(), ignored -> this.setCurrencyTab(!this.currencyTabOpen))
                .bounds(
                    this.leftPos + MintHouseLayout.SETTINGS_GEAR_X,
                    this.topPos + MintHouseLayout.SETTINGS_GEAR_Y,
                    MintHouseLayout.SETTINGS_GEAR_WIDTH,
                    MintHouseLayout.SETTINGS_GEAR_HEIGHT
                )
                .tooltip(Tooltip.create(gui("currency_settings")))
                .build()));
        }

        Component kingdomNameLabel = gui("kingdom_name");
        this.kingdomNameField = this.addRenderableWidget(new EditBox(
            this.font,
            this.leftPos + 43,
            this.topPos + 26,
            106,
            16,
            kingdomNameLabel
        ));
        this.kingdomNameField.setMaxLength(Kingdom.MAX_KINGDOM_NAME_LENGTH);
        this.kingdomNameField.setHint(kingdomNameLabel);
        this.kingdomNameField.setValue(this.display.kingdomName());
        this.kingdomNameField.setResponder(ignored -> this.refreshKingdomNameSaveState());
        this.saveKingdomNameButton = this.addRenderableWidget(invisible(Button.builder(
                Component.empty(),
                ignored -> this.saveKingdomName()
            )
            .bounds(this.leftPos + 153, this.topPos + 27, 16, 14)
            .tooltip(Tooltip.create(gui("save_kingdom")))
            .build()));

        Component currencyNameLabel = gui("currency_name");
        this.currencyNameField = this.addRenderableWidget(new EditBox(
            this.font,
            this.leftPos + 125,
            this.topPos + 136,
            230,
            18,
            currencyNameLabel
        ));
        this.currencyNameField.setMaxLength(Kingdom.MAX_CURRENCY_NAME_LENGTH);
        this.currencyNameField.setHint(currencyNameLabel);
        this.currencyNameField.setValue(this.display.currencyName());
        this.currencyNameField.setResponder(ignored -> this.refreshCurrencySaveState());
            this.saveCurrencyButton = this.addRenderableWidget(invisible(Button.builder(Component.empty(), ignored -> saveCurrencyName())
            .bounds(this.leftPos + 181, this.topPos + 162, 118, 18)
            .build()));

        this.refreshDetectedMetal();
        // The press opens directly on the catalogue. The small gear opens settings.
        this.setCurrencyTab(true);
        this.refreshSelectionState();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (this.mintAnimationTicks > 0) {
            this.mintAnimationTicks--;
        }
        this.refreshDetectedMetal();
        this.refreshSelectionState();
    }

    /**
     * The inserted nugget determines the only valid denomination. This makes
     * the visual choice match the actual server-side ingredient at all times.
     */
    private void refreshDetectedMetal() {
        Kingdom.Metal inputMetal = this.menu.materialMetal().orElse(null);
        if (inputMetal == this.detectedMetal) {
            return;
        }
        this.detectedMetal = inputMetal;
        if (inputMetal != null) {
            this.selectedMetal = inputMetal;
        }
        this.status = Component.empty();
        this.refreshShapeButtonVisibility();
    }

    private void selectShape(int shape) {
        if (shape < 1 || shape > MintHouseLayout.SHAPE_GALLERY_BUTTON_COUNT || this.detectedMetal == null) {
            return;
        }
        this.selectedShape = shape;
    }

    private void refreshSelectionState() {
        if (this.confirmButton == null) {
            return;
        }
        int quantity = this.detectedMetal == null ? 0 : this.menu.mintableCoinCountFor(this.selectedMetal);
        this.confirmButton.active = KingdomCrest.isSupported(this.display.crest())
            && this.detectedMetal != null
            && quantity > 0;
        if (quantity > 0) {
            this.confirmButton.setMessage(gui("mint_stack", quantity));
            this.confirmButton.setTooltip(Tooltip.create(gui(
                "mint_stack_tooltip",
                quantity,
                MintHouseMenu.nuggetsPerCoin(this.selectedMetal)
            )));
        } else {
            this.confirmButton.setMessage(gui("confirm"));
            this.confirmButton.setTooltip(Tooltip.create(gui(
                "mint_stack_empty",
                MintHouseMenu.nuggetsPerCoin(this.selectedMetal),
                nuggetMaterialName(this.selectedMetal)
            )));
        }
    }

    private void refreshCurrencySaveState() {
        if (this.saveCurrencyButton != null) {
            this.saveCurrencyButton.active = this.display.canEditCurrency()
                && validCurrencyName(this.currencyNameField == null ? "" : this.currencyNameField.getValue());
        }
    }

    private void refreshKingdomNameSaveState() {
        if (this.saveKingdomNameButton != null) {
            this.saveKingdomNameButton.active = this.display.canEditCurrency()
                && validKingdomName(this.kingdomNameField == null ? "" : this.kingdomNameField.getValue());
        }
    }

    private void setCurrencyTab(boolean open) {
        if (open && !this.display.canEditCurrency()) {
            return;
        }
        this.currencyTabOpen = open;
        this.refreshShapeButtonVisibility();
        if (this.confirmButton != null) {
            this.confirmButton.visible = open;
        }
        if (this.backButton != null) {
            this.backButton.setTooltip(Tooltip.create(gui(open ? "back" : "tab_currency")));
        }
        if (this.mintTabButton != null) {
            this.mintTabButton.active = true;
        }
        if (this.currencyNameField != null) {
            this.currencyNameField.visible = !open;
            this.currencyNameField.setFocused(!open);
            this.setFocused(!open ? this.currencyNameField : null);
        }
        if (this.kingdomNameField != null) {
            this.kingdomNameField.visible = false;
        }
        if (this.saveCurrencyButton != null) {
            this.saveCurrencyButton.visible = !open;
        }
        if (this.saveKingdomNameButton != null) {
            this.saveKingdomNameButton.visible = false;
        }
        this.refreshCurrencySaveState();
        this.refreshKingdomNameSaveState();
    }

    private void refreshShapeButtonVisibility() {
        // The press creates only a clean round base; forms belong to the design side.
        boolean visible = this.currencyTabOpen && this.detectedMetal != null;
        for (ShapeButton button : this.shapeButtons) {
            button.button().visible = visible;
            button.button().active = visible;
        }
    }

    private void saveCurrencyName() {
        if (!this.display.canEditCurrency() || this.currencyNameField == null) {
            return;
        }
        String currencyName = this.currencyNameField.getValue().strip();
        if (!validCurrencyName(currencyName)) {
            this.status = gui("currency_name_invalid");
            return;
        }
        ClientPacketDistributor.sendToServer(new UpdateCurrencyNamePayload(this.menu.containerId, currencyName));
        this.status = gui("currency_name_sent");
    }

    /** Saves the actual bound kingdom name; the server enforces founder ownership. */
    private void saveKingdomName() {
        if (!this.display.canEditCurrency() || this.kingdomNameField == null) {
            return;
        }
        String kingdomName = this.kingdomNameField.getValue().strip();
        if (!validKingdomName(kingdomName)) {
            this.status = gui("kingdom_name_invalid");
            return;
        }
        ClientPacketDistributor.sendToServer(new UpdateKingdomNamePayload(this.menu.containerId, kingdomName));
        this.status = gui("kingdom_name_sent");
    }

    private static boolean validCurrencyName(String value) {
        int length = value.strip().codePointCount(0, value.strip().length());
        return length >= Kingdom.MIN_CURRENCY_NAME_LENGTH && length <= Kingdom.MAX_CURRENCY_NAME_LENGTH;
    }

    private static boolean validKingdomName(String value) {
        int length = value.strip().codePointCount(0, value.strip().length());
        return length >= Kingdom.MIN_KINGDOM_NAME_LENGTH && length <= Kingdom.MAX_KINGDOM_NAME_LENGTH;
    }

    private void mint() {
        if (this.detectedMetal == null) {
            this.status = gui("insert_nuggets");
            return;
        }
        int quantity = this.menu.mintableCoinCountFor(this.selectedMetal);
        if (quantity <= 0) {
            this.status = gui(
                "insert_matching_nuggets",
                MintHouseMenu.nuggetsPerCoin(this.selectedMetal),
                nuggetMaterialName(this.selectedMetal)
            );
            return;
        }
        this.mintAnimationTicks = MINT_ANIMATION_TICKS;
        ClientPacketDistributor.sendToServer(new MintCoinPayload(
            this.menu.containerId,
            metalId(this.selectedMetal),
            this.currencyTabOpen ? this.selectedShape : 0
        ));
        this.status = gui("mint_sent", quantity);
    }

    /** Prevent the inventory shortcut from interrupting an active name edit. */
    @Override
    public boolean keyPressed(KeyEvent event) {
        EditBox activeNameField = this.currencyNameField != null && this.currencyNameField.isFocused()
            ? this.currencyNameField
            : this.kingdomNameField != null && this.kingdomNameField.isFocused()
                ? this.kingdomNameField
                : null;
        if (activeNameField != null) {
            if (activeNameField.keyPressed(event)) {
                return true;
            }
            if (!event.isEscape()) {
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int left = this.leftPos;
        int top = this.topPos;
        if (this.pressScreen) {
            renderPressBackground(graphics, left, top);
            super.extractRenderState(graphics, mouseX, mouseY, partialTick);
            if (!this.status.getString().isEmpty()) {
                graphics.centeredText(this.font, this.status, left + MintHouseLayout.PRESS_SCREEN_WIDTH / 2, top + 145, GOLD);
            }
            return;
        }
        if (this.currencyTabOpen) {
            renderDesignBackground(graphics, left, top);
            renderDesignStorageSlots(graphics, left, top);
            renderDesignHotbar(graphics, left, top);
            super.extractRenderState(graphics, mouseX, mouseY, partialTick);
            renderDesignHeaderText(graphics, left, top);
            renderShapeGallery(graphics, left, top);
            renderActionPanel(graphics, left, top);
        } else {
            // Keep the same header art while configuring: it keeps the gear
            // in the exact same place instead of switching to old chrome.
            renderDesignBackground(graphics, left, top);
            renderSettingsBackground(graphics, left, top);
            super.extractRenderState(graphics, mouseX, mouseY, partialTick);
            renderSettingsSlotMask(graphics, left, top);
            renderDesignHeaderText(graphics, left, top);
            renderSettingsText(graphics, left, top);
        }
        if (!this.status.getString().isEmpty()) {
            // Keep feedback out of the shape gallery, hotbar and name field.
            if (this.currencyTabOpen) {
                graphics.centeredText(this.font, this.status, left + 393, top + 252, GOLD);
            } else {
                graphics.centeredText(this.font, this.status, left + SCREEN_WIDTH / 2, top + 214, GOLD);
            }
        }
    }

    private void renderDesignBackground(GuiGraphicsExtractor graphics, int left, int top) {
        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            DESIGN_BACKGROUND,
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
    }

    /** Visible, clickable quick-access bar painted along the bottom of the internal chest. */
    private void renderDesignHotbar(GuiGraphicsExtractor graphics, int left, int top) {
        renderSlotGrid(
            graphics,
            left + MintHouseLayout.PLAYER_HOTBAR_X,
            top + MintHouseLayout.PLAYER_HOTBAR_Y,
            9,
            1
        );
    }

    /** Match the visual slots to the real 16px click areas inside the arca. */
    private void renderDesignStorageSlots(GuiGraphicsExtractor graphics, int left, int top) {
        for (int slot = 0; slot < 27; slot++) {
            renderSlotFrame(
                graphics,
                left + MintHouseLayout.coinStorageSlotX(slot),
                top + MintHouseLayout.coinStorageSlotY(slot)
            );
        }
    }

    /** Replaces the names painted in the mockup with the live kingdom data. */
    private void renderDesignHeaderText(GuiGraphicsExtractor graphics, int left, int top) {
        graphics.fill(left + 66, top + 36, left + 201, top + 60, PANEL_INNER);
        graphics.text(this.font, Component.literal(shortName(this.display.kingdomName(), 16)), left + 72, top + 43, GOLD);
        graphics.fill(left + 285, top + 36, left + 405, top + 60, PANEL_INNER);
        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            coinShapeTexture(this.selectedMetal, visualShape()),
            left + 263,
            top + 37,
            0.0F,
            0.0F,
            18,
            18,
            16,
            16,
            16,
            16
        );
        graphics.text(this.font, Component.literal(shortName(this.display.currencyName(), 12)), left + 290, top + 43, GOLD);
    }

    /** The Pixelorama artwork supplied by the player is the real left-hand press background. */
    private void renderPressBackground(GuiGraphicsExtractor graphics, int left, int top) {
        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            PRESS_BACKGROUND,
            left,
            top,
            0.0F,
            0.0F,
            MintHouseLayout.PRESS_SCREEN_WIDTH,
            MintHouseLayout.PRESS_SCREEN_HEIGHT,
            MintHouseLayout.PRESS_SCREEN_WIDTH,
            MintHouseLayout.PRESS_SCREEN_HEIGHT,
            MintHouseLayout.PRESS_SCREEN_WIDTH,
            MintHouseLayout.PRESS_SCREEN_HEIGHT
        );
    }

    private void renderChrome(GuiGraphicsExtractor graphics, int left, int top) {
        // Version B: a compact, warm workbench rather than a dense metal dashboard.
        graphics.fill(left, top, left + SCREEN_WIDTH, top + SCREEN_HEIGHT, BORDER_DARK);
        graphics.outline(left, top, SCREEN_WIDTH, SCREEN_HEIGHT, IRON);
        graphics.outline(left + 3, top + 3, SCREEN_WIDTH - 6, SCREEN_HEIGHT - 6, WOOD_LIGHT);
        graphics.fill(left + 6, top + 6, left + SCREEN_WIDTH - 6, top + SCREEN_HEIGHT - 6, WOOD_DARK);

        renderWorkbenchPanel(graphics, left + MintHouseLayout.HEADER_X, top + MintHouseLayout.HEADER_Y,
            MintHouseLayout.HEADER_WIDTH, MintHouseLayout.HEADER_HEIGHT);
        graphics.fill(left + 153, top + 5, left + 327, top + 27, WOOD);
        graphics.outline(left + 153, top + 5, 174, 22, GOLD_DARK);
        renderWorkbenchPanel(graphics, left + MintHouseLayout.MATERIAL_PANEL_X, top + MintHouseLayout.MATERIAL_PANEL_Y,
            MintHouseLayout.MATERIAL_PANEL_WIDTH, MintHouseLayout.MATERIAL_PANEL_HEIGHT);
        renderWorkbenchPanel(graphics, left + MintHouseLayout.PREVIEW_PANEL_X, top + MintHouseLayout.PREVIEW_PANEL_Y,
            MintHouseLayout.PREVIEW_PANEL_WIDTH, MintHouseLayout.PREVIEW_PANEL_HEIGHT);
        renderWorkbenchPanel(graphics, left + MintHouseLayout.COIN_CHEST_PANEL_X, top + MintHouseLayout.COIN_CHEST_PANEL_Y,
            MintHouseLayout.COIN_CHEST_PANEL_WIDTH, MintHouseLayout.COIN_CHEST_PANEL_HEIGHT);
        renderWorkbenchPanel(graphics, left + 12, top + 146, 456, 62);
        renderWorkbenchPanel(graphics, left + MintHouseLayout.INVENTORY_PANEL_X, top + MintHouseLayout.INVENTORY_PANEL_Y,
            MintHouseLayout.INVENTORY_PANEL_WIDTH, MintHouseLayout.INVENTORY_PANEL_HEIGHT);
        renderWorkbenchPanel(graphics, left + MintHouseLayout.ACTION_PANEL_X, top + MintHouseLayout.ACTION_PANEL_Y,
            MintHouseLayout.ACTION_PANEL_WIDTH, MintHouseLayout.ACTION_PANEL_HEIGHT);

        renderSlotFrame(graphics, left + MintHouseLayout.MATERIAL_SLOT_X, top + MintHouseLayout.MATERIAL_SLOT_Y);
        renderSlotGrid(graphics, left + MintHouseLayout.COIN_STORAGE_X, top + MintHouseLayout.COIN_STORAGE_Y, 9, 3);
        renderSlotGrid(graphics, left + MintHouseLayout.PLAYER_INVENTORY_X, top + MintHouseLayout.PLAYER_INVENTORY_Y, 9, 3);
        renderSlotGrid(graphics, left + MintHouseLayout.PLAYER_INVENTORY_X, top + MintHouseLayout.PLAYER_HOTBAR_Y, 9, 1);
    }

    private void renderWorkbenchPanel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, WOOD_DARK);
        graphics.outline(x, y, width, height, BORDER_DARK);
        graphics.outline(x + 2, y + 2, width - 4, height - 4, WOOD_LIGHT);
        graphics.fill(x + 5, y + 5, x + width - 5, y + height - 5, PANEL_INNER);
    }

    /** Recreates the familiar vanilla slot recesses over the new painted workbench. */
    private void renderSlotGrid(GuiGraphicsExtractor graphics, int x, int y, int columns, int rows) {
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                renderSlotFrame(graphics, x + column * 18, y + row * 18);
            }
        }
    }

    private void renderSlotFrame(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, SLOT_DARK);
        graphics.outline(x - 1, y - 1, 18, 18, WOOD_DARK);
        graphics.fill(x + 1, y + 1, x + 16, y + 16, SLOT_INNER);
    }

    private void renderHeaderText(GuiGraphicsExtractor graphics, int left, int top) {
        graphics.centeredText(this.font, gui("mint_house_heading"), left + SCREEN_WIDTH / 2, top + 10, GOLD);
        renderHeaderCrest(graphics, left + 18, top + 26, this.display.crest());
        graphics.text(this.font, gui("kingdom", shortName(this.display.kingdomName(), 16)), left + 43, top + 29, TEXT);
        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            coinShapeTexture(this.selectedMetal, visualShape()),
            left + 267,
            top + 25,
            0.0F,
            0.0F,
            18,
            18,
            16,
            16,
            16,
            16
        );
        graphics.text(this.font, Component.literal(shortName(this.display.currencyName(), 10)), left + 289, top + 29, GOLD);
        if (this.display.canEditCurrency()) {
            renderGearButton(graphics, left + 432, top + 24, !this.currencyTabOpen);
        }
    }

    private void renderHeaderCrest(GuiGraphicsExtractor graphics, int x, int y, Symbol crest) {
        graphics.fill(x - 1, y - 1, x + 20, y + 20, PANEL_INNER);
        graphics.outline(x - 1, y - 1, 21, 21, GOLD_DARK);
        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            crestCenterTexture(crest),
            x,
            y,
            0.0F,
            0.0F,
            18,
            18,
            32,
            32,
            32,
            32
        );
    }

    /** Drawn before widgets so the editable text remains visible over the frame. */
    private void renderKingdomNameFieldBackground(GuiGraphicsExtractor graphics, int left, int top) {
        int fieldX = left + 42;
        int fieldY = top + 25;
        graphics.fill(fieldX, fieldY, fieldX + 108, fieldY + 18, PANEL_INNER);
        graphics.outline(fieldX, fieldY, 108, 18, GOLD_DARK);
    }

    /** Small forward arrow positioned beside the editable bound-realm name. */
    private void renderKingdomNameSaveArrow(GuiGraphicsExtractor graphics, int left, int top) {
        boolean active = this.saveKingdomNameButton != null && this.saveKingdomNameButton.active;
        renderSmallHeaderButton(graphics, left + 153, top + 27, 16, 14, Component.literal("→"), active);
    }

    private void renderPreview(GuiGraphicsExtractor graphics, int left, int top) {
        if (this.detectedMetal == null) {
            return;
        }
        int previewX = left + MintHouseLayout.PREVIEW_CENTER_X - PREVIEW_SIZE / 2;
        int previewY = top + MintHouseLayout.PREVIEW_CENTER_Y - PREVIEW_SIZE / 2 - 1;
        renderAnvil(graphics, previewX + PREVIEW_SIZE / 2, previewY + PREVIEW_SIZE - 1);
        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            coinShapeTexture(this.selectedMetal, visualShape()),
            previewX,
            previewY,
            0.0F,
            0.0F,
            PREVIEW_SIZE,
            PREVIEW_SIZE,
            16,
            16,
            16,
            16
        );
        if (this.mintAnimationTicks > 0) {
            renderMintHammer(graphics, previewX + PREVIEW_SIZE / 2, previewY, this.mintAnimationTicks);
        }
    }

    /** A tiny fixed anvil grounds the otherwise floating item preview. */
    private void renderAnvil(GuiGraphicsExtractor graphics, int centerX, int baseY) {
        int x = centerX - 14;
        graphics.fill(x + 3, baseY - 5, x + 25, baseY - 2, BORDER_DARK);
        graphics.fill(x + 6, baseY - 8, x + 22, baseY - 5, 0xFF8B8D89);
        graphics.fill(x + 9, baseY - 2, x + 19, baseY + 2, 0xFF5B5C59);
        graphics.fill(x + 5, baseY + 2, x + 23, baseY + 5, BORDER_DARK);
        graphics.fill(x + 8, baseY + 2, x + 20, baseY + 3, 0xFF9C9E99);
    }

    /**
     * A local press animation: raised hammer, impact with sparks, then lift.
     * It intentionally does not report server success; that remains the normal
     * container/payload responsibility.
     */
    private void renderMintHammer(GuiGraphicsExtractor graphics, int centerX, int previewY, int ticksRemaining) {
        int elapsed = MINT_ANIMATION_TICKS - ticksRemaining;
        int headY;
        boolean impact;
        if (elapsed < 7) {
            headY = previewY - 10 + elapsed * 2;
            impact = false;
        } else if (elapsed < 12) {
            headY = previewY + 4;
            impact = true;
        } else {
            headY = previewY + 4 - (elapsed - 11) * 2;
            impact = false;
        }

        int headX = centerX - 9;
        graphics.fill(headX, headY, headX + 18, headY + 6, BORDER_DARK);
        graphics.fill(headX + 2, headY + 1, headX + 16, headY + 4, 0xFF777972);
        graphics.fill(headX + 3, headY + 1, headX + 7, headY + 2, 0xFFC2C4BC);
        graphics.fill(headX + 7, headY + 5, headX + 11, headY + 18, BORDER_DARK);
        graphics.fill(headX + 8, headY + 6, headX + 10, headY + 17, 0xFF9B591F);

        if (impact) {
            int sparkY = headY + 17;
            graphics.fill(centerX - 17, sparkY, centerX - 12, sparkY + 2, GOLD);
            graphics.fill(centerX + 12, sparkY - 2, centerX + 17, sparkY, GOLD);
            graphics.fill(centerX - 11, sparkY + 3, centerX - 9, sparkY + 6, GOLD);
            graphics.fill(centerX + 9, sparkY + 2, centerX + 11, sparkY + 5, GOLD);
        }
    }

    /**
     * The white-marked card area becomes one border-free gallery. It stays
     * empty until a nugget is inserted, then shows the ten mintable forms in
     * the exact metal detected in the socket.
     */
    private void renderShapeGallery(GuiGraphicsExtractor graphics, int left, int top) {
        if (this.detectedMetal == null) {
            return;
        }

        for (int index = 0; index < MintHouseLayout.SHAPE_GALLERY_BUTTON_COUNT; index++) {
            int shape = index + 1;
            int cellX = left + MintHouseLayout.shapeGalleryButtonX(index);
            int cellY = top + MintHouseLayout.shapeGalleryButtonY(index);
            boolean selected = shape == this.selectedShape;
            int size = selected ? 26 : 20;
            int coinX = cellX + (MintHouseLayout.SHAPE_GALLERY_BUTTON_SIZE - size) / 2;
            int coinY = cellY + (MintHouseLayout.SHAPE_GALLERY_BUTTON_SIZE - size) / 2;
            graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                coinShapeTexture(this.selectedMetal, shape),
                coinX,
                coinY,
                0.0F,
                0.0F,
                size,
                size,
                16,
                16,
                16,
                16
            );
            if (selected) {
                int centerX = cellX + MintHouseLayout.SHAPE_GALLERY_BUTTON_SIZE / 2;
                graphics.fill(centerX - 2, cellY + 26, centerX + 3, cellY + 28, GOLD);
            }
        }
    }

    private void renderActionPanel(GuiGraphicsExtractor graphics, int left, int top) {
        int quantity = this.detectedMetal == null ? 0 : Math.max(0, this.menu.mintableCoinCountFor(this.selectedMetal));
        if (this.detectedMetal != null) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, coinShapeTexture(this.selectedMetal, 1), left + 342, top + 226,
                0.0F, 0.0F, 20, 20, 16, 16, 16, 16);
            graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                coinShapeTexture(this.selectedMetal, visualShape()),
                left + 417,
                top + 226,
                0.0F,
                0.0F,
                20,
                20,
                16,
                16,
                16,
                16
            );
        }
        if (this.confirmButton == null || !this.confirmButton.active) {
            graphics.fill(left + MintHouseLayout.CONFIRM_X, top + MintHouseLayout.CONFIRM_Y,
                left + MintHouseLayout.CONFIRM_X + MintHouseLayout.CONFIRM_WIDTH,
                top + MintHouseLayout.CONFIRM_Y + MintHouseLayout.CONFIRM_HEIGHT, 0x990E0F10);
        } else if (quantity > 0) {
            graphics.outline(left + MintHouseLayout.CONFIRM_X, top + MintHouseLayout.CONFIRM_Y,
                MintHouseLayout.CONFIRM_WIDTH, MintHouseLayout.CONFIRM_HEIGHT, GOLD);
        }
    }

    private void renderCurrencyTabBackground(GuiGraphicsExtractor graphics, int left, int top) {
        graphics.fill(left + 12, top + 56, left + 468, top + 202, 0xFF14161A);
        graphics.outline(left + 12, top + 56, 456, 146, BORDER);
        renderWorkbenchPanel(graphics, left + 280, top + 78, 188, 74);
        renderSlotGrid(graphics, left + MintHouseLayout.COIN_STORAGE_X, top + MintHouseLayout.COIN_STORAGE_Y, 9, 3);
    }

    private void renderCurrencyTabText(GuiGraphicsExtractor graphics, int left, int top) {
        graphics.centeredText(this.font, Component.literal("ESCOLHA O DESENHO DA MOEDA"), left + 145, top + 67, GOLD);
        graphics.centeredText(this.font, Component.literal("ARCA INTERNA DE MOEDAS"), left + 374, top + 84, GOLD);
        if (this.detectedMetal == null) {
            graphics.centeredText(this.font, Component.literal("A fornalha enviará moedas-base para a arca."), left + 145, top + 103, TEXT);
        } else {
            graphics.centeredText(this.font, Component.literal("Moedas-base disponíveis: " + this.menu.mintableCoinCountFor(this.selectedMetal)), left + 145, top + 103, TEXT);
            graphics.centeredText(this.font, Component.literal("Escolha um desenho para carregar a prensa."), left + 145, top + 127, GOLD);
        }
        graphics.centeredText(this.font, Component.literal("INVENTÁRIO DO JOGADOR"), left + 128, top + 228, GOLD);
        graphics.centeredText(this.font, Component.literal("PRENSA DE CUNHAGEM"), left + 361, top + 228, GOLD);
    }

    private void renderSettingsBackground(GuiGraphicsExtractor graphics, int left, int top) {
        // The body replaces the working view but leaves the supplied header
        // visible, including the correctly placed gear icon.
        renderWorkbenchPanel(graphics, left + 12, top + 80, 456, 268);
    }

    private void renderSettingsText(GuiGraphicsExtractor graphics, int left, int top) {
        graphics.centeredText(this.font, gui("settings_title"), left + SCREEN_WIDTH / 2, top + 96, GOLD);
        graphics.centeredText(this.font, gui("currency_label"), left + SCREEN_WIDTH / 2, top + 122, TEXT);
        renderActionButton(graphics, left + 181, top + 162, 118, 18, gui("save_name"),
            this.saveCurrencyButton != null && this.saveCurrencyButton.active, false);
        graphics.centeredText(this.font, gui("currency_note"),
            left + SCREEN_WIDTH / 2, top + 195, SUBTLE_TEXT);
        renderActionButton(graphics, left + MintHouseLayout.BACK_X, top + MintHouseLayout.BACK_Y,
            MintHouseLayout.BACK_WIDTH, MintHouseLayout.BACK_HEIGHT, gui("back_to_press"), true, false);
    }

    /** Settings takes visual priority over all live slots from the workbench view. */
    private void renderSettingsSlotMask(GuiGraphicsExtractor graphics, int left, int top) {
        // Important: never paint over the name field (y=136).  The previous
        // mask covered it after Minecraft drew the text, making typing look
        // invisible.  Only cover live work slots below the settings content.
        graphics.fill(left + 150, top + 158, left + 330, top + 182, PANEL_INNER);
        graphics.fill(left + 18, top + 202, left + 462, top + 342, PANEL_INNER);
    }

    private void renderGearButton(GuiGraphicsExtractor graphics, int x, int y, boolean selected) {
        renderSmallHeaderButton(graphics, x, y, 22, 20, Component.literal("⚙"), selected);
    }

    private void renderSmallHeaderButton(
        GuiGraphicsExtractor graphics,
        int x,
        int y,
        int width,
        int height,
        Component label,
        boolean selected
    ) {
        graphics.fill(x, y, x + width, y + height, selected ? 0xFF382816 : PANEL_INNER);
        graphics.outline(x, y, width, height, selected ? GOLD : BORDER);
        graphics.centeredText(this.font, label, x + width / 2, y + 3, selected ? GOLD : TEXT);
    }

    private void renderActionButton(
        GuiGraphicsExtractor graphics,
        int x,
        int y,
        int width,
        int height,
        Component label,
        boolean active,
        boolean primary
    ) {
        int fill = !active ? 0xFF29292B : primary ? 0xFF8F6115 : 0xFF3A3938;
        int border = !active ? BORDER_DARK : primary ? GOLD : BORDER;
        int textColor = !active ? SUBTLE_TEXT : primary ? 0xFFFFE49A : TEXT;
        graphics.fill(x, y, x + width, y + height, fill);
        graphics.outline(x, y, width, height, border);
        if (active && primary) {
            graphics.outline(x + 2, y + 2, width - 4, height - 4, GOLD_DARK);
        }
        graphics.centeredText(this.font, label, x + width / 2, y + (height - 8) / 2, textColor);
    }

    /** Buttons retain normal click, focus and tooltip behaviour; the UI draws their faces. */
    private static Button invisible(Button button) {
        button.setAlpha(0.0F);
        return button;
    }

    private static int metalId(Kingdom.Metal metal) {
        return switch (metal) {
            case IRON -> MintHouseMenu.IRON_METAL_ID;
            case COPPER -> MintHouseMenu.COPPER_METAL_ID;
            case GOLD -> MintHouseMenu.GOLD_METAL_ID;
        };
    }

    private static Component metalName(Kingdom.Metal metal) {
        return Component.translatable("gui.crownscoins.metal." + metal.name().toLowerCase(Locale.ROOT));
    }

    /** The iron denomination is named silver, but its minting input remains iron nuggets. */
    private static Component nuggetMaterialName(Kingdom.Metal metal) {
        return Component.translatable("gui.crownscoins.nugget_material." + metal.name().toLowerCase(Locale.ROOT));
    }

    private static ItemStack nuggetStack(Kingdom.Metal metal) {
        return new ItemStack(switch (metal) {
            case COPPER -> Items.COPPER_NUGGET;
            case IRON -> Items.IRON_NUGGET;
            case GOLD -> Items.GOLD_NUGGET;
        });
    }

    private static Identifier coinShapeTexture(Kingdom.Metal metal, int shape) {
        int safeShape = normalizedShape(shape);
        return Identifier.fromNamespaceAndPath(
            CrownsCoins.MOD_ID,
            "textures/item/coin_shape/%s_%02d_%s.png".formatted(
                coinMetalName(metal),
                safeShape,
                SHAPE_TEXTURE_SUFFIXES[safeShape - 1]
            )
        );
    }

    private static int normalizedShape(int shape) {
        return Math.max(1, Math.min(SHAPE_TEXTURE_SUFFIXES.length, shape));
    }

    /** Shape zero is data for a clean base; it uses the first round sprite when drawn. */
    private int visualShape() {
        return this.currencyTabOpen ? this.selectedShape : 1;
    }

    private static String coinMetalName(Kingdom.Metal metal) {
        return switch (metal) {
            case COPPER -> "copper";
            case IRON -> "iron";
            case GOLD -> "gold";
        };
    }

    private static Identifier crestCenterTexture(Symbol crest) {
        return Identifier.fromNamespaceAndPath(
            CrownsCoins.MOD_ID,
            "textures/item/overlay/crest_center/%02d_%s.png".formatted(crest.id(), crest.name().toLowerCase(Locale.ROOT))
        );
    }

    private static String shortName(String value, int maximumCodePoints) {
        if (value.codePointCount(0, value.length()) <= maximumCodePoints) {
            return value;
        }
        int end = value.offsetByCodePoints(0, maximumCodePoints - 1);
        return value.substring(0, end) + "…";
    }

    private static Component gui(String key, Object... arguments) {
        return Component.translatable("gui.crownscoins." + key, arguments);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record ShapeButton(Button button, int shape) {
    }
}
