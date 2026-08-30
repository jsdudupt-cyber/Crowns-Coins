package com.crownscoins.coin;

import com.crownscoins.kingdom.Symbol;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipProvider;

/** Persistent, synchronized provenance for one minted coin stack. */
public record CoinData(
    UUID kingdomId,
    String kingdomName,
    String currencyName,
    Symbol kingdomCrest,
    Material material,
    int value,
    int styleId,
    List<Symbol> symbols
) implements TooltipProvider {
    public static final int MAX_SYMBOLS = 3;
    public static final int MAX_STYLE_ID = 25;
    public static final int MAX_VALUE = 1_000_000;
    private static final Codec<List<Symbol>> SYMBOLS_CODEC = Symbol.CODEC.listOf().validate(CoinData::validateSymbols);

    public static final Codec<CoinData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        UUIDUtil.CODEC.fieldOf("kingdom_id").forGetter(CoinData::kingdomId),
        Codec.STRING.fieldOf("kingdom_name").forGetter(CoinData::kingdomName),
        Codec.STRING.fieldOf("currency_name").forGetter(CoinData::currencyName),
        Symbol.CODEC.fieldOf("kingdom_crest").forGetter(CoinData::kingdomCrest),
        Material.CODEC.fieldOf("material").forGetter(CoinData::material),
        Codec.intRange(1, MAX_VALUE).fieldOf("value").forGetter(CoinData::value),
        Codec.intRange(1, MAX_STYLE_ID).fieldOf("style_id").forGetter(CoinData::styleId),
        SYMBOLS_CODEC.fieldOf("symbols").forGetter(CoinData::symbols)
    ).apply(instance, CoinData::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, CoinData> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public CoinData {
        kingdomId = Objects.requireNonNull(kingdomId, "kingdomId");
        kingdomName = validateText(kingdomName, "kingdomName", 2, 32);
        currencyName = validateText(currencyName, "currencyName", 1, 24);
        kingdomCrest = Objects.requireNonNull(kingdomCrest, "kingdomCrest");
        material = Objects.requireNonNull(material, "material");
        if (value < 1 || value > MAX_VALUE) {
            throw new IllegalArgumentException("Coin value is out of range");
        }
        if (styleId < 1 || styleId > MAX_STYLE_ID) {
            throw new IllegalArgumentException("Coin style is out of range");
        }
        symbols = List.copyOf(Objects.requireNonNull(symbols, "symbols"));
        if (validateSymbols(symbols).error().isPresent()) {
            throw new IllegalArgumentException("Coin symbols are invalid");
        }
    }

    private static DataResult<List<Symbol>> validateSymbols(List<Symbol> symbols) {
        if (symbols.size() > MAX_SYMBOLS) {
            return DataResult.error(() -> "A coin may have at most " + MAX_SYMBOLS + " symbols");
        }
        EnumSet<Symbol> unique = EnumSet.noneOf(Symbol.class);
        for (Symbol symbol : symbols) {
            if (symbol == null || !unique.add(symbol)) {
                return DataResult.error(() -> "Coin symbols must be non-null and unique");
            }
        }
        return DataResult.success(List.copyOf(symbols));
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
        tooltip.accept(Component.translatable("tooltip.crownscoins.crest", symbolName(kingdomCrest)));
        tooltip.accept(Component.translatable("tooltip.crownscoins.style", styleId));
        tooltip.accept(Component.translatable("tooltip.crownscoins.symbols", symbolsText(symbols)));
    }

    private static Component materialName(Material material) {
        return Component.translatable("tooltip.crownscoins.metal." + material.translationKey());
    }

    private static Component symbolName(Symbol symbol) {
        return Component.translatable("symbol.crownscoins." + symbol.name().toLowerCase(Locale.ROOT));
    }

    private static Component symbolsText(List<Symbol> symbols) {
        if (symbols.isEmpty()) {
            return Component.translatable("tooltip.crownscoins.none");
        }
        MutableComponent result = Component.empty();
        for (int index = 0; index < symbols.size(); index++) {
            if (index > 0) {
                result.append(Component.literal(", "));
            }
            result.append(symbolName(symbols.get(index)));
        }
        return result;
    }

    /** Fixed catalog value, kept independent from the mutable kingdom record. */
    public enum Material {
        IRON,
        COPPER,
        GOLD;

        public static final Codec<Material> CODEC = Codec.STRING.xmap(Material::valueOf, Material::name);

        public String translationKey() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
