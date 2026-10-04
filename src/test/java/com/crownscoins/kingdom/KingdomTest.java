package com.crownscoins.kingdom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class KingdomTest {
    private static final UUID FOUNDER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

    private static Kingdom kingdom() {
        return Kingdom.create(FOUNDER, "Aldenbruk", "Florins", Symbol.LION);
    }

    @Test
    void founderIsTheFirstMemberAndCannotBeRemoved() {
        Kingdom kingdom = kingdom();
        assertTrue(kingdom.isFounder(FOUNDER));
        assertTrue(kingdom.isMember(FOUNDER));
        assertFalse(kingdom.removeMember(FOUNDER));
        assertTrue(kingdom.isMember(FOUNDER));
    }

    @Test
    void membersCanBeAddedAndRemoved() {
        Kingdom kingdom = kingdom();
        assertTrue(kingdom.addMember(OTHER));
        assertTrue(kingdom.isMember(OTHER));
        assertTrue(kingdom.removeMember(OTHER));
        assertFalse(kingdom.isMember(OTHER));
    }

    @Test
    void namesAreStrippedAndValidated() {
        assertEquals("Aldenbruk", Kingdom.create(FOUNDER, "  Aldenbruk  ", "Florins", Symbol.LION).name());
        assertThrows(IllegalArgumentException.class, () -> Kingdom.create(FOUNDER, "A", "Florins", Symbol.LION));
        assertThrows(IllegalArgumentException.class, () -> Kingdom.create(FOUNDER, "Aldenbruk", "", Symbol.LION));
        assertThrows(IllegalArgumentException.class,
            () -> Kingdom.create(FOUNDER, "x".repeat(Kingdom.MAX_KINGDOM_NAME_LENGTH + 1), "Florins", Symbol.LION));
        assertThrows(IllegalArgumentException.class, () -> Kingdom.create(FOUNDER, "Alden§bruk", "Florins", Symbol.LION));
        assertThrows(IllegalArgumentException.class, () -> Kingdom.create(FOUNDER, "Alden\nbruk", "Florins", Symbol.LION));
    }

    @Test
    void canonicalNameIgnoresCaseAndPadding() {
        assertEquals(Kingdom.canonicalName("ALDENBRUK"), Kingdom.canonicalName(" aldenbruk "));
    }

    @Test
    void coinValuesAreTheSameCatalogForEveryKingdom() {
        Kingdom kingdom = kingdom();
        assertEquals(1, kingdom.value(Kingdom.Metal.COPPER));
        assertEquals(20, kingdom.value(Kingdom.Metal.IRON));
        assertEquals(500, kingdom.value(Kingdom.Metal.GOLD));
    }

    @Test
    void codecRoundTripKeepsEverything() {
        Kingdom original = kingdom();
        original.addMember(OTHER);
        JsonElement json = Kingdom.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        Kingdom decoded = Kingdom.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();

        assertEquals(original.id(), decoded.id());
        assertEquals(original.name(), decoded.name());
        assertEquals(original.currencyName(), decoded.currencyName());
        assertEquals(original.crest(), decoded.crest());
        assertEquals(original.members(), decoded.members());
        // Coin values are no longer part of the saved form.
        assertFalse(json.getAsJsonObject().has("iron_value"));
    }

    private static JsonObject encoded() {
        return Kingdom.CODEC.encodeStart(JsonOps.INSTANCE, kingdom()).getOrThrow().getAsJsonObject();
    }

    @Test
    void oldSavesWithCoinValuesStillLoad() {
        JsonObject legacy = encoded();
        legacy.addProperty("iron_value", 20);
        legacy.addProperty("copper_value", 1);
        legacy.addProperty("gold_value", 500);
        Kingdom decoded = Kingdom.CODEC.parse(JsonOps.INSTANCE, legacy).getOrThrow();
        assertEquals("Aldenbruk", decoded.name());
    }

    @Test
    void invalidSavedDataIsACodecErrorNotAnException() {
        JsonObject shortName = encoded();
        shortName.addProperty("name", "A");
        var nameResult = Kingdom.CODEC.parse(JsonOps.INSTANCE, shortName);
        assertTrue(nameResult.error().isPresent());
        assertTrue(nameResult.error().get().message().contains("Invalid saved kingdom"));

        JsonObject unknownCrest = encoded();
        unknownCrest.addProperty("crest", "NOT_A_SYMBOL");
        var crestResult = Kingdom.CODEC.parse(JsonOps.INSTANCE, unknownCrest);
        assertTrue(crestResult.error().isPresent());
        assertTrue(crestResult.error().get().message().contains("Unknown symbol"));
    }
}
