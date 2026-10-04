package com.crownscoins.kingdom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class KingdomSavedDataTest {
    private static final UUID FOUNDER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID SECOND_FOUNDER = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final UUID MEMBER = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

    private static Kingdom found(KingdomSavedData data, UUID founder, String name) {
        return data.createKingdom(founder, name, "Moeda de " + name, Symbol.LION);
    }

    @Test
    void aPlayerCanFoundOnlyOneKingdom() {
        KingdomSavedData data = new KingdomSavedData();
        found(data, FOUNDER, "Aldenbruk");
        assertThrows(IllegalStateException.class, () -> found(data, FOUNDER, "Outro Reino"));
    }

    @Test
    void kingdomNamesAreUniqueIgnoringCase() {
        KingdomSavedData data = new KingdomSavedData();
        found(data, FOUNDER, "Aldenbruk");
        assertThrows(IllegalArgumentException.class, () -> found(data, SECOND_FOUNDER, "ALDENBRUK"));
    }

    @Test
    void aPlayerCannotJoinASecondKingdom() {
        KingdomSavedData data = new KingdomSavedData();
        Kingdom first = found(data, FOUNDER, "Aldenbruk");
        Kingdom second = found(data, SECOND_FOUNDER, "Valmora");

        assertTrue(data.addMember(first.id(), MEMBER));
        assertFalse(data.addMember(second.id(), MEMBER));
        assertEquals(first.id(), data.findByMember(MEMBER).orElseThrow().id());
    }

    @Test
    void removedMembersLeaveTheIndexAndMayJoinAgain() {
        KingdomSavedData data = new KingdomSavedData();
        Kingdom kingdom = found(data, FOUNDER, "Aldenbruk");
        data.addMember(kingdom.id(), MEMBER);

        assertTrue(data.removeMember(kingdom.id(), MEMBER));
        assertTrue(data.findByMember(MEMBER).isEmpty());
        assertFalse(data.removeMember(kingdom.id(), FOUNDER));
        assertTrue(data.addMember(kingdom.id(), MEMBER));
    }

    @Test
    void onlyTheFounderCanRenameTheCurrency() {
        KingdomSavedData data = new KingdomSavedData();
        Kingdom kingdom = found(data, FOUNDER, "Aldenbruk");
        data.addMember(kingdom.id(), MEMBER);

        assertTrue(data.updateCurrencyName(kingdom.id(), MEMBER, "Ouro").isEmpty());
        assertEquals("Ouro", data.updateCurrencyName(kingdom.id(), FOUNDER, "Ouro").orElseThrow().currencyName());
    }

    @Test
    void renamingAKingdomKeepsNamesUniqueAndFreesTheOldOne() {
        KingdomSavedData data = new KingdomSavedData();
        Kingdom first = found(data, FOUNDER, "Aldenbruk");
        found(data, SECOND_FOUNDER, "Valmora");

        assertTrue(data.updateKingdomName(first.id(), FOUNDER, "Valmora").isEmpty());
        assertTrue(data.updateKingdomName(first.id(), FOUNDER, "Nova Aldenbruk").isPresent());
        assertTrue(data.findByName("Aldenbruk").isEmpty());
        assertTrue(data.findByName("nova aldenbruk").isPresent());
    }
}
