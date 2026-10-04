package com.crownscoins.coin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CoinDataTest {
    private static final UUID KINGDOM = UUID.fromString("00000000-0000-0000-0000-0000000000c3");

    private static CoinData coin(CoinData.Material material, int value, int shape) {
        return new CoinData(KINGDOM, "Aldenbruk", "Florins", material, value, shape);
    }

    @Test
    void baseCoinsUseShapeZero() {
        assertEquals(CoinData.DEFAULT_SHAPE_ID, coin(CoinData.Material.COPPER, 1, CoinData.DEFAULT_SHAPE_ID).shapeId());
    }

    @Test
    void shapeIdsAreRangeChecked() {
        assertTrue(CoinData.isValidShapeId(0));
        assertTrue(CoinData.isValidShapeId(CoinData.MAX_SHAPE_ID));
        assertFalse(CoinData.isValidShapeId(-1));
        assertFalse(CoinData.isValidShapeId(CoinData.MAX_SHAPE_ID + 1));
        assertThrows(IllegalArgumentException.class, () -> coin(CoinData.Material.GOLD, 500, CoinData.MAX_SHAPE_ID + 1));
    }

    @Test
    void valuesAndTextAreValidated() {
        assertThrows(IllegalArgumentException.class, () -> coin(CoinData.Material.GOLD, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> coin(CoinData.Material.GOLD, CoinData.MAX_VALUE + 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new CoinData(
            KINGDOM, "Aldenbruk", "Flo§rins", CoinData.Material.GOLD, 500, 1));
    }

    @Test
    void codecRoundTrip() {
        CoinData original = coin(CoinData.Material.IRON, 20, 7);
        JsonElement json = CoinData.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        assertEquals(original, CoinData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
    }

    private static JsonObject encoded() {
        return CoinData.CODEC.encodeStart(JsonOps.INSTANCE, coin(CoinData.Material.COPPER, 1, 5)).getOrThrow().getAsJsonObject();
    }

    @Test
    void coinsWithoutAShapeFieldLoadAsBaseCoins() {
        JsonObject legacy = encoded();
        legacy.remove("shape_id");
        assertEquals(CoinData.DEFAULT_SHAPE_ID, CoinData.CODEC.parse(JsonOps.INSTANCE, legacy).getOrThrow().shapeId());
    }

    @Test
    void unknownMaterialIsACodecErrorNotAnException() {
        JsonObject broken = encoded();
        broken.addProperty("material", "PLATINUM");
        var result = CoinData.CODEC.parse(JsonOps.INSTANCE, broken);
        assertTrue(result.error().isPresent());
        assertTrue(result.error().get().message().contains("Unknown coin material"));
    }

    @Test
    void coinsSavedWithTheOldCrestStyleAndSymbolsStillLoad() {
        JsonObject legacy = encoded();
        legacy.addProperty("kingdom_crest", "CROWN");
        legacy.addProperty("style_id", 4);
        legacy.add("symbols", new com.google.gson.JsonArray());
        CoinData decoded = CoinData.CODEC.parse(JsonOps.INSTANCE, legacy).getOrThrow();
        assertEquals("Aldenbruk", decoded.kingdomName());
        assertEquals(5, decoded.shapeId());
        // Saving it again drops the unused fields.
        JsonObject resaved = CoinData.CODEC.encodeStart(JsonOps.INSTANCE, decoded).getOrThrow().getAsJsonObject();
        assertFalse(resaved.has("style_id"));
        assertFalse(resaved.has("symbols"));
        assertFalse(resaved.has("kingdom_crest"));
    }
}
