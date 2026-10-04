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
import com.crownscoins.network.UpdateMembersPayload;
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
    /** The player-supplied 480x360 Aseprite art is the design-side background. */
    private static final Identifier DESIGN_BACKGROUND = Identifier.fromNamespaceAndPath(
        CrownsCoins.MOD_ID,
        "textures/gui/mint_house_user_layout.png"
    );
    /** Keep the live coin small enough that the forge animation stays legible. */
    private static final int PREVIEW_SIZE = 36;
    private static final int MINT_ANIMATION_TICKS = 20;
    /** Amount choices of the minting panel; {@link MintCoinPayload#ALL} stamps every base coin. */
    private static final int[] QUANTITY_CHOICES = {1, 8, 64, MintCoinPayload.ALL};
    private static final int[] QUANTITY_BUTTON_X = {332, 363, 394, 425};
    private static final int[] QUANTITY_BUTTON_WIDTH = {28, 28, 28, 31};
    private static final int QUANTITY_BUTTON_Y = 255;
    private static final int QUANTITY_BUTTON_HEIGHT = 13;
    /** The two 28px coin slots painted into the minting panel of the texture. */
    private static final int BASE_SLOT_X = 343;
    private static final int RESULT_SLOT_X = 416;
    private static final int COIN_SLOT_Y = 224;
    private static final int COIN_SLOT_SIZE = 28;
    /** Members section of the settings view: list box above one row of controls. */
    private static final int MEMBER_LIST_X = 40;
    private static final int MEMBER_LIST_Y = 228;
    private static final int MEMBER_LIST_WIDTH = 400;
    private static final int MEMBER_LIST_HEIGHT = 30;
    private static final int MEMBER_ROW_Y = 266;
    private static final int MEMBER_FIELD_X = 100;
    private static final int MEMBER_FIELD_WIDTH = 170;
    private static final int MEMBER_ADD_X = 276;
    private static final int MEMBER_REMOVE_X = 356;
    private static final int MEMBER_BUTTON_WIDTH = 76;

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

    /** Refreshed every tick: the server pushes new names and members while the screen is open. */
    private MintHouseMenu.ClientMintData display;
    private final List<ShapeButton> shapeButtons = new ArrayList<>();
    private Kingdom.Metal selectedMetal = Kingdom.Metal.COPPER;
    /** Null until the synchronized input socket holds a supported nugget. */
    private Kingdom.Metal detectedMetal;
    /** One-based because that is the stable ID sent to the minting payload. */
    private int selectedShape = 1;
    /** Requested amount for one mint; {@link MintCoinPayload#ALL} until the player picks a number. */
    private int selectedQuantity = MintCoinPayload.ALL;
    private final List<Button> quantityButtons = new ArrayList<>();
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
    private EditBox memberNameField;
    private Button addMemberButton;
    private Button removeMemberButton;
    private boolean currencyTabOpen;
    private Component status = Component.empty();

    public MintHouseScreen(MintHouseMenu menu, Inventory inventory, Component title) {
        super(
            menu,
            inventory,
            title,
            SCREEN_WIDTH,
            SCREEN_HEIGHT
        );
        this.display = menu.clientData();
        this.titleLabelX = -10_000;
        this.inventoryLabelX = -10_000;
    }

    @Override
    protected void init() {
        super.init();

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

        this.quantityButtons.clear();
        for (int index = 0; index < QUANTITY_CHOICES.length; index++) {
            int choice = QUANTITY_CHOICES[index];
            this.quantityButtons.add(this.addRenderableWidget(invisible(Button.builder(
                    Component.empty(),
                    ignored -> selectQuantity(choice)
                )
                .bounds(
                    this.leftPos + QUANTITY_BUTTON_X[index],
                    this.topPos + QUANTITY_BUTTON_Y,
                    QUANTITY_BUTTON_WIDTH[index],
                    QUANTITY_BUTTON_HEIGHT
                )
                .tooltip(Tooltip.create(choice == MintCoinPayload.ALL
                    ? gui("quantity_tooltip_all")
                    : gui("quantity_tooltip", choice)))
                .build())));
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

        Component playerNameLabel = gui("player_name");
        this.memberNameField = this.addRenderableWidget(new EditBox(
            this.font,
            this.leftPos + MEMBER_FIELD_X,
            this.topPos + MEMBER_ROW_Y,
            MEMBER_FIELD_WIDTH,
            18,
            playerNameLabel
        ));
        this.memberNameField.setMaxLength(UpdateMembersPayload.MAX_PLAYER_NAME_LENGTH);
        this.memberNameField.setHint(playerNameLabel);
        this.memberNameField.setResponder(ignored -> this.refreshMemberButtons());
        this.addMemberButton = this.addRenderableWidget(invisible(Button.builder(Component.empty(), ignored -> sendMemberRequest(true))
            .bounds(this.leftPos + MEMBER_ADD_X, this.topPos + MEMBER_ROW_Y, MEMBER_BUTTON_WIDTH, 18)
            .build()));
        this.removeMemberButton = this.addRenderableWidget(invisible(Button.builder(Component.empty(), ignored -> sendMemberRequest(false))
            .bounds(this.leftPos + MEMBER_REMOVE_X, this.topPos + MEMBER_ROW_Y, MEMBER_BUTTON_WIDTH, 18)
            .build()));

        this.refreshDetectedMetal();
        // The press opens directly on the catalogue. The small gear opens settings.
        this.setCurrencyTab(true);
        this.refreshSelectionState();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        this.display = this.menu.clientData();
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

    /** How many coins one click will mint: the chosen amount, never more than the chest holds. */
    private int mintAmount(int available) {
        return this.selectedQuantity == MintCoinPayload.ALL ? available : Math.min(this.selectedQuantity, available);
    }

    private void selectQuantity(int quantity) {
        this.selectedQuantity = quantity;
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
        int quantity = this.detectedMetal == null ? 0 : mintAmount(this.menu.mintableCoinCountFor(this.selectedMetal));
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

    private void refreshMemberButtons() {
        boolean ready = this.display.canEditCurrency()
            && this.memberNameField != null
            && !this.memberNameField.getValue().isBlank();
        if (this.addMemberButton != null) {
            this.addMemberButton.active = ready;
        }
        if (this.removeMemberButton != null) {
            this.removeMemberButton.active = ready;
        }
    }

    /** Sends only the typed name; the server decides whether the change is allowed. */
    private void sendMemberRequest(boolean add) {
        if (!this.display.canEditCurrency() || this.memberNameField == null) {
            return;
        }
        String playerName = this.memberNameField.getValue().strip();
        if (playerName.isEmpty()) {
            return;
        }
        ClientPacketDistributor.sendToServer(new UpdateMembersPayload(this.menu.containerId, add, playerName));
        this.memberNameField.setValue("");
        this.status = gui("member_sent");
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
        for (Button quantityButton : this.quantityButtons) {
            quantityButton.visible = open;
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
        boolean membersVisible = !open && this.display.canEditCurrency();
        if (this.memberNameField != null) {
            this.memberNameField.visible = membersVisible;
        }
        if (this.addMemberButton != null) {
            this.addMemberButton.visible = membersVisible;
        }
        if (this.removeMemberButton != null) {
            this.removeMemberButton.visible = membersVisible;
        }
        this.refreshMemberButtons();
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
        int quantity = mintAmount(this.menu.mintableCoinCountFor(this.selectedMetal));
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
            this.currencyTabOpen ? this.selectedShape : 0,
            this.selectedQuantity
        ));
        this.status = gui("mint_sent", quantity);
    }

    /** Prevent the inventory shortcut from interrupting an active name edit. */
    @Override
    public boolean keyPressed(KeyEvent event) {
        EditBox activeNameField = this.currencyNameField != null && this.currencyNameField.isFocused()
            ? this.currencyNameField
            : this.memberNameField != null && this.memberNameField.isFocused()
                ? this.memberNameField
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
            // The slot mask above covers this field, so draw it again on top.
            if (this.memberNameField != null && this.memberNameField.visible) {
                this.memberNameField.extractRenderState(graphics, mouseX, mouseY, partialTick);
            }
        }
        if (!this.status.getString().isEmpty()) {
            // Keep feedback out of the shape gallery, hotbar and name field.
            if (this.currencyTabOpen) {
                graphics.centeredText(this.font, this.status, left + 393, top + 252, GOLD);
            } else {
                graphics.centeredText(this.font, this.status, left + SCREEN_WIDTH / 2, top + 298, GOLD);
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
        graphics.text(this.font, Component.literal(shortName(this.display.kingdomName(), 16)), left + 72, top + 43, GOLD);
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
                renderSlotFrame(graphics, x + column * MintHouseLayout.COIN_STORAGE_STEP, y + row * MintHouseLayout.COIN_STORAGE_STEP);
            }
        }
    }

    private void renderSlotFrame(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, SLOT_DARK);
        graphics.outline(x - 1, y - 1, 18, 18, WOOD_DARK);
        graphics.fill(x + 1, y + 1, x + 16, y + 16, SLOT_INNER);
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
        int available = this.detectedMetal == null ? 0 : Math.max(0, this.menu.mintableCoinCountFor(this.selectedMetal));
        int quantity = mintAmount(available);
        for (int index = 0; index < QUANTITY_CHOICES.length; index++) {
            int choice = QUANTITY_CHOICES[index];
            Component label = choice == MintCoinPayload.ALL ? gui("quantity_all") : Component.literal(Integer.toString(choice));
            renderActionButton(graphics, left + QUANTITY_BUTTON_X[index], top + QUANTITY_BUTTON_Y,
                QUANTITY_BUTTON_WIDTH[index], QUANTITY_BUTTON_HEIGHT, label, true, choice == this.selectedQuantity);
        }
        if (this.detectedMetal != null) {
            // 16px coins centred in the two 28px slots painted into the texture,
            // each with its amount in the corner like a vanilla stack count.
            renderSlotCoin(graphics, left + BASE_SLOT_X, top + COIN_SLOT_Y, coinShapeTexture(this.selectedMetal, 1), available);
            renderSlotCoin(graphics, left + RESULT_SLOT_X, top + COIN_SLOT_Y,
                coinShapeTexture(this.selectedMetal, visualShape()), quantity);
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

    /** Draws one coin centred in a painted slot, with its amount in the lower-right corner. */
    private void renderSlotCoin(GuiGraphicsExtractor graphics, int slotX, int slotY, Identifier texture, int amount) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, slotX + (COIN_SLOT_SIZE - 16) / 2, slotY + (COIN_SLOT_SIZE - 16) / 2,
            0.0F, 0.0F, 16, 16, 16, 16, 16, 16);
        Component count = Component.literal(Integer.toString(amount));
        int textX = slotX + COIN_SLOT_SIZE - 3 - this.font.width(count);
        int textY = slotY + COIN_SLOT_SIZE - 11;
        graphics.text(this.font, count, textX + 1, textY + 1, 0xFF000000);
        graphics.text(this.font, count, textX, textY, 0xFFFFFFFF);
    }

    private void renderSettingsBackground(GuiGraphicsExtractor graphics, int left, int top) {
        // The body replaces the working view but leaves the supplied header
        // visible, including the correctly placed gear icon.
        renderWorkbenchPanel(graphics, left + 12, top + 68, 456, 280);
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
        if (this.display.canEditCurrency()) {
            renderMembersSection(graphics, left, top);
        }
    }

    /** Founder-only list plus add/remove controls, drawn inside the settings body panel. */
    private void renderMembersSection(GuiGraphicsExtractor graphics, int left, int top) {
        graphics.fill(left + 30, top + 208, left + 450, top + 209, BORDER);
        graphics.centeredText(this.font, gui("members_title"), left + SCREEN_WIDTH / 2, top + 213, GOLD);

        int boxX = left + MEMBER_LIST_X;
        int boxY = top + MEMBER_LIST_Y;
        graphics.fill(boxX, boxY, boxX + MEMBER_LIST_WIDTH, boxY + MEMBER_LIST_HEIGHT, PANEL_INNER);
        graphics.outline(boxX, boxY, MEMBER_LIST_WIDTH, MEMBER_LIST_HEIGHT, GOLD_DARK);
        List<String> names = this.display.memberNames();
        StringBuilder line = new StringBuilder();
        int lineY = boxY + 5;
        int lines = 0;
        for (int index = 0; index < names.size(); index++) {
            String entry = index == 0 ? gui("member_founder", names.get(index)).getString() : names.get(index);
            String candidate = line.isEmpty() ? entry : line + "   " + entry;
            if (this.font.width(candidate) > MEMBER_LIST_WIDTH - 12 && !line.isEmpty()) {
                graphics.text(this.font, Component.literal(line.toString()), boxX + 6, lineY, TEXT);
                lineY += 12;
                lines++;
                line = new StringBuilder(entry);
                if (lines == 2) {
                    line = new StringBuilder("…");
                    break;
                }
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (!line.isEmpty()) {
            graphics.text(this.font, Component.literal(line.toString()), boxX + 6, lineY, TEXT);
        }

        renderActionButton(graphics, left + MEMBER_ADD_X, top + MEMBER_ROW_Y, MEMBER_BUTTON_WIDTH, 18,
            gui("add_member"), this.addMemberButton != null && this.addMemberButton.active, false);
        renderActionButton(graphics, left + MEMBER_REMOVE_X, top + MEMBER_ROW_Y, MEMBER_BUTTON_WIDTH, 18,
            gui("remove_member"), this.removeMemberButton != null && this.removeMemberButton.active, false);
        graphics.centeredText(this.font, gui("members_hint"), left + 195, top + 288, SUBTLE_TEXT);
    }

    /** Settings takes visual priority over all live slots from the workbench view. */
    private void renderSettingsSlotMask(GuiGraphicsExtractor graphics, int left, int top) {
        // Important: never paint over the name field (y=136).  The previous
        // mask covered it after Minecraft drew the text, making typing look
        // invisible.  Only cover live work slots below the settings content.
        graphics.fill(left + 150, top + 158, left + 330, top + 182, PANEL_INNER);
        graphics.fill(left + 18, top + 202, left + 462, top + 342, PANEL_INNER);
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

    /** The iron denomination is named silver, but its minting input remains iron nuggets. */
    private static Component nuggetMaterialName(Kingdom.Metal metal) {
        return Component.translatable("gui.crownscoins.nugget_material." + metal.name().toLowerCase(Locale.ROOT));
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
