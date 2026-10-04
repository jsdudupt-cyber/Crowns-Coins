package com.crownscoins;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server-side options, saved per world in {@code serverconfig/crownscoins-server.toml}.
 * The defaults suit a small group of friends who trust each other; a larger or public
 * server can turn the protections on.
 */
public final class CrownsCoinsConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue PROTECT_MINT_HOUSE = BUILDER
        .comment(
            "When true, only members of the kingdom a Mint House is bound to (and operators) can break it.",
            "A Mint House that is not bound to a kingdom yet can be broken by anyone.",
            "Off by default, for groups of friends. Turn it on for a server with players you do not trust."
        )
        .define("protectMintHouse", false);

    public static final ModConfigSpec.BooleanValue ALLOW_HOPPER_OUTPUT = BUILDER
        .comment(
            "Let a hopper under a Mint House pull the finished coins out of its chest, to build",
            "automatic coin lines. Hoppers can always feed nuggets in.",
            "On by default. Turn it off on a server where anyone could place a hopper next to someone",
            "else's Mint House and take their coins."
        )
        .define("allowHopperOutput", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private CrownsCoinsConfig() {
    }
}
