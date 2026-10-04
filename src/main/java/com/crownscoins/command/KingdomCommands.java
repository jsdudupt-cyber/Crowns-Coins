package com.crownscoins.command;

import com.crownscoins.CrownsCoins;
import com.crownscoins.kingdom.Kingdom;
import com.crownscoins.kingdom.KingdomSavedData;
import com.crownscoins.kingdom.PlayerLookup;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.NameAndId;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Administrator tools for kingdoms, for operators only:
 * {@code /crownscoins kingdom list|info|delete|transfer|add|remove}.
 *
 * <p>They exist so a server is never stuck with a kingdom whose founder has left or
 * made a mistake. Kingdom names with spaces go in quotes.</p>
 */
@EventBusSubscriber(modid = CrownsCoins.MOD_ID)
public final class KingdomCommands {
    private static final SuggestionProvider<CommandSourceStack> KINGDOM_NAMES = (context, builder) ->
        SharedSuggestionProvider.suggest(
            KingdomSavedData.get(context.getSource().getLevel()).kingdoms().stream()
                .map(kingdom -> kingdom.name().contains(" ") ? "\"" + kingdom.name() + "\"" : kingdom.name()),
            builder
        );
    private static final SuggestionProvider<CommandSourceStack> ONLINE_PLAYERS = (context, builder) ->
        SharedSuggestionProvider.suggest(
            context.getSource().getServer().getPlayerList().getPlayers().stream().map(player -> player.getGameProfile().name()),
            builder
        );

    private KingdomCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        // The root literal is shared with the player commands in WalletCommands, so only the
        // kingdom branch is limited to operators.
        event.getDispatcher().register(Commands.literal("crownscoins")
            .then(Commands.literal("kingdom")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("list").executes(KingdomCommands::list))
                .then(Commands.literal("info").then(kingdomArgument().executes(KingdomCommands::info)))
                .then(Commands.literal("delete").then(kingdomArgument().executes(KingdomCommands::delete)))
                .then(Commands.literal("transfer").then(kingdomArgument().then(playerArgument().executes(KingdomCommands::transfer))))
                .then(Commands.literal("add").then(kingdomArgument().then(playerArgument().executes(KingdomCommands::add))))
                .then(Commands.literal("remove").then(kingdomArgument().then(playerArgument().executes(KingdomCommands::remove))))));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> kingdomArgument() {
        return Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOM_NAMES);
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> playerArgument() {
        return Commands.argument("player", StringArgumentType.word()).suggests(ONLINE_PLAYERS);
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        List<Kingdom> kingdoms = KingdomSavedData.get(source.getLevel()).kingdoms().stream()
            .sorted(Comparator.comparing(kingdom -> kingdom.name().toLowerCase())).toList();
        if (kingdoms.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.crownscoins.list_empty"), false);
            return 0;
        }
        MinecraftServer server = source.getServer();
        source.sendSuccess(() -> Component.translatable("command.crownscoins.list_header", kingdoms.size()), false);
        for (Kingdom kingdom : kingdoms) {
            source.sendSuccess(() -> Component.translatable(
                "command.crownscoins.list_entry",
                kingdom.name(),
                PlayerLookup.nameOf(server, kingdom.founder()),
                kingdom.members().size()
            ), false);
        }
        return kingdoms.size();
    }

    private static int info(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Optional<Kingdom> found = find(context);
        if (found.isEmpty()) {
            return notFound(context);
        }
        Kingdom kingdom = found.get();
        MinecraftServer server = source.getServer();
        String members = kingdom.members().stream()
            .map(id -> PlayerLookup.nameOf(server, id) + (kingdom.isFounder(id) ? "*" : ""))
            .sorted()
            .collect(Collectors.joining(", "));
        source.sendSuccess(() -> Component.translatable(
            "command.crownscoins.info",
            kingdom.name(),
            kingdom.currencyName(),
            PlayerLookup.nameOf(server, kingdom.founder()),
            members
        ), false);
        return 1;
    }

    private static int delete(CommandContext<CommandSourceStack> context) {
        Optional<Kingdom> found = find(context);
        if (found.isEmpty()) {
            return notFound(context);
        }
        Kingdom kingdom = found.get();
        KingdomSavedData.get(context.getSource().getLevel()).deleteKingdom(kingdom.id());
        context.getSource().sendSuccess(() -> Component.translatable("command.crownscoins.deleted", kingdom.name()), true);
        return 1;
    }

    /** Hands the kingdom to another player, adding them first if they belong to no kingdom yet. */
    private static int transfer(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Optional<Kingdom> found = find(context);
        if (found.isEmpty()) {
            return notFound(context);
        }
        Optional<NameAndId> target = PlayerLookup.byName(source.getServer(), StringArgumentType.getString(context, "player"));
        if (target.isEmpty()) {
            return playerNotFound(context);
        }
        Kingdom kingdom = found.get();
        KingdomSavedData data = KingdomSavedData.get(source.getLevel());
        if (!kingdom.isMember(target.get().id()) && !data.addMember(kingdom.id(), target.get().id())) {
            source.sendFailure(Component.translatable("command.crownscoins.other_kingdom", target.get().name()));
            return 0;
        }
        if (data.transferFounder(kingdom.id(), target.get().id()).isEmpty()) {
            source.sendFailure(Component.translatable("command.crownscoins.failed"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.crownscoins.transferred", target.get().name(), kingdom.name()), true);
        return 1;
    }

    private static int add(CommandContext<CommandSourceStack> context) {
        return change(context, true);
    }

    private static int remove(CommandContext<CommandSourceStack> context) {
        return change(context, false);
    }

    private static int change(CommandContext<CommandSourceStack> context, boolean add) {
        CommandSourceStack source = context.getSource();
        Optional<Kingdom> found = find(context);
        if (found.isEmpty()) {
            return notFound(context);
        }
        Optional<NameAndId> target = PlayerLookup.byName(source.getServer(), StringArgumentType.getString(context, "player"));
        if (target.isEmpty()) {
            return playerNotFound(context);
        }
        Kingdom kingdom = found.get();
        KingdomSavedData data = KingdomSavedData.get(source.getLevel());
        boolean changed = add ? data.addMember(kingdom.id(), target.get().id()) : data.removeMember(kingdom.id(), target.get().id());
        if (!changed) {
            source.sendFailure(Component.translatable(add ? "command.crownscoins.add_failed" : "command.crownscoins.remove_failed", target.get().name()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(
            add ? "command.crownscoins.added" : "command.crownscoins.removed", target.get().name(), kingdom.name()), true);
        return 1;
    }

    private static Optional<Kingdom> find(CommandContext<CommandSourceStack> context) {
        try {
            return KingdomSavedData.get(context.getSource().getLevel()).findByName(StringArgumentType.getString(context, "kingdom"));
        } catch (IllegalArgumentException invalidName) {
            return Optional.empty();
        }
    }

    private static int notFound(CommandContext<CommandSourceStack> context) {
        context.getSource().sendFailure(Component.translatable("command.crownscoins.not_found", StringArgumentType.getString(context, "kingdom")));
        return 0;
    }

    private static int playerNotFound(CommandContext<CommandSourceStack> context) {
        context.getSource().sendFailure(Component.translatable("command.crownscoins.player_not_found", StringArgumentType.getString(context, "player")));
        return 0;
    }
}
