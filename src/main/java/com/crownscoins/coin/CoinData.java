package com.crownscoins.coin;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipProvider;

/**
 * Persistent, synchronized provenance for one minted coin stack.
 *
 * <p>Coins saved by older versions also carry a crest, a style id and a symbol
 * list. Those never affected the coin's look, so they are no longer stored:
 * the codec simply ignores the extra fields when it reads an old coin.</p>
 */
public record CoinData(
    UUID kingdomId,
    String kingdomName,
    String currencyName,
    Material material,
    int value,
    int shapeId
) implements TooltipProvider {
    /** Shape zero is a plain base coin; shapes one through twelve are the minted designs. */
    public static final int DEFAULT_SHAPE_ID = 0;
    public static final int MAX_SHAPE_ID = 12;
    public static final int MAX_VALUE = 1_000_000;
    private static final Codec<Integer> SHAPE_ID_CODEC = Codec.intRange(DEFAULT_SHAPE_ID, MAX_SHAPE_ID);

    public static final Codec<CoinData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        UUIDUtil.CODEC.fieldOf("kingdom_id").forGetter(CoinData::kingdomId),
        Codec.STRING.fieldOf("kingdom_name").forGetter(CoinData::kingdomName),
        Codec.STRING.fieldOf("currency_name").forGetter(CoinData::currencyName),
        Material.CODEC.fieldOf("material").forGetter(CoinData::material),
        Codec.intRange(1, MAX_VALUE).fieldOf("value").forGetter(CoinData::value),
        // Optional so all pre-shape item stacks deserialize as plain base coins
        // rather than failing to load a saved world.
        SHAPE_ID_CODEC.optionalFieldOf("shape_id", DEFAULT_SHAPE_ID).forGetter(CoinData::shapeId)
    ).apply(instance, CoinData::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, CoinData> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public CoinData {
        kingdomId = Objects.requireNonNull(kingdomId, "kingdomId");
        kingdomName = validateText(kingdomName, "kingdomName", 2, 32);
        currencyName = validateText(currencyName, "currencyName", 1, 24);
        material = Objects.requireNonNull(material, "material");
        if (value < 1 || value > MAX_VALUE) {
            throw new IllegalArgumentException("Coin value is out of range");
        }
        if (!isValidShapeId(shapeId)) {
            throw new IllegalArgumentException("Coin shape is out of range");
        }
    }

    /** Returns whether an untrusted request refers to a supported coin shape. */
    public static boolean isValidShapeId(int shapeId) {
        return shapeId >= DEFAULT_SHAPE_ID && shapeId <= MAX_SHAPE_ID;
    }

    private static String validateText(String value, String field, int minimum, int maximum) {
        String normalized = Objects.requireNonNull(value, field).strip();
        int length = normalized.codePointCount(0, normalized.length());
        if (length < minimum || length > maximum) {
            throw new IllegalArgumentException(field + " length is out of range");
        }
        for (int offset = 0; offset < normalized.length();) {
            int codePoint = normalized.codePointAt(offset);
            if (Character.isISOControl(codePoint) || codePoint == '\u00A7') {
                throw new IllegalArgumentException(field + " contains a disallowed character");
            }
            offset += Character.charCount(codePoint);
        }
        return normalized;
    }

    @Override
    public void addToTooltip(Item.TooltipContext context, java.util.function.Consumer<Component> tooltip, TooltipFlag tooltipFlag, DataComponentGetter components) {
        tooltip.accept(Component.translatable("tooltip.crownscoins.currency", currencyName));
        tooltip.accept(Component.translatable("tooltip.crownscoins.kingdom", kingdomName));
        tooltip.accept(Component.translatable("tooltip.crownscoins.value", value));
        tooltip.accept(Component.translatable("tooltip.crownscoins.metal", materialName(material)));
        if (shapeId != DEFAULT_SHAPE_ID) {
            tooltip.accept(Component.translatable(
                "tooltip.crownscoins.shape",
                Component.translatable("gui.crownscoins.coin_shape." + shapeId)
            ));
        }
    }

    private static Component materialName(Material material) {
        return Component.translatable("tooltip.crownscoins.metal." + material.translationKey());
    }

    /** Fixed catalog value, kept independent from the mutable kingdom record. */
    public enum Material {
        IRON,
        COPPER,
        GOLD;

        public static final Codec<Material> CODEC = Codec.STRING.comapFlatMap(name -> {
            try {
                return DataResult.success(Material.valueOf(name));
            } catch (IllegalArgumentException ignored) {
                return DataResult.error(() -> "Unknown coin material: " + name);
            }
        }, Material::name);

        public String translationKey() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
