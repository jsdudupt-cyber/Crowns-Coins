package com.crownscoins.coin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The current names of every kingdom, as the server last told this client.
 *
 * <p>Coins keep the names they were minted with, but those can go stale when a kingdom
 * or its currency is renamed. A coin's tooltip and title ask here first and fall back to
 * the stored names (for example when its kingdom was deleted). The cache is display data
 * only and is empty on a server.</p>
 */
public final class KingdomNames {
    /** One kingdom's current names. */
    public record Entry(UUID kingdomId, String kingdomName, String currencyName) {
    }

    private static volatile Map<UUID, Entry> current = Map.of();

    private KingdomNames() {
    }

    /** Replaces everything with the list the server just sent. */
    public static void replaceAll(List<Entry> entries) {
        Map<UUID, Entry> next = new HashMap<>();
        for (Entry entry : entries) {
            next.put(entry.kingdomId(), entry);
        }
        current = Map.copyOf(next);
    }

    public static String kingdomName(CoinData data) {
        Entry entry = current.get(data.kingdomId());
        return entry != null ? entry.kingdomName() : data.kingdomName();
    }

    public static String currencyName(CoinData data) {
        Entry entry = current.get(data.kingdomId());
        return entry != null ? entry.currencyName() : data.currencyName();
    }
}
