package com.crownscoins;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server-side options, saved per world in {@code serverconfig/crownscoins-server.toml}.
 * The defaults favour safety on a multiplayer server.
 */
public final class CrownsCoinsConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue PROTECT_MINT_HOUSE = BUILDER
        .comment(
            "Only members of the kingdom a Mint House is bound to (and operators) can break it.",
            "A Mint House that is not bound to a kingdom yet can be broken by anyone."
        )
        .define("protectMintHouse", true);

    public static final ModConfigSpec.BooleanValue ALLOW_HOPPER_OUTPUT = BUILDER
        .comment(
            "Let a hopper under a Mint House pull the finished coins out of its chest.",
            "Off by default: anyone can place a hopper next to someone else's Mint House, so this",
            "would let non-members take coins. Hoppers can always feed nuggets in. Turn this on for",
            "single-player worlds or trusted servers to build automatic coin lines."
        )
        .define("allowHopperOutput", false);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private CrownsCoinsConfig() {
    }
}
