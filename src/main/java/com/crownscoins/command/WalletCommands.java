package com.crownscoins.command;

import com.crownscoins.CrownsCoins;
import com.crownscoins.ModAdvancements;
import com.crownscoins.coin.CoinWallet;
import com.crownscoins.kingdom.Kingdom;
import com.crownscoins.kingdom.KingdomSavedData;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Commands any player can use: {@code /crownscoins balance} and
 * {@code /crownscoins pay <player> <amount> [kingdom]}.
 *
 * <p>Money is the real coin items in the inventory. Paying moves exactly those coins
 * to the other player, so there is no separate account that could get out of step.
 * No change is given: the amount must be formed from the coins carried.</p>
 */
@EventBusSubscriber(modid = CrownsCoins.MOD_ID)
public final class WalletCommands {
    private static final int MAX_AMOUNT = 1_000_000;

    private WalletCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("crownscoins")
            .then(Commands.literal("balance").executes(WalletCommands::balance))
            .then(Commands.literal("pay")
                .then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.argument("amount", IntegerArgumentType.integer(1, MAX_AMOUNT))
                        .executes(context -> pay(context, null))
                        .then(Commands.argument("kingdom", StringArgumentType.string()).executes(
                            context -> pay(context, StringArgumentType.getString(context, "kingdom"))))))));
    }

    private static int balance(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        List<CoinWallet.Holding> holdings = CoinWallet.holdings(player.getInventory());
        if (holdings.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.translatable("command.crownscoins.balance_empty"), false);
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.translatable("command.crownscoins.balance_header"), false);
        for (CoinWallet.Holding holding : holdings) {
            context.getSource().sendSuccess(() -> Component.translatable(
                "command.crownscoins.balance_entry",
                holding.currencyName(),
                holding.value(),
                holding.gold(),
                holding.iron(),
                holding.copper()
            ), false);
        }
        return holdings.size();
    }

    private static int pay(CommandContext<CommandSourceStack> context, String kingdomName) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer payer = source.getPlayerOrException();
        ServerPlayer receiver = EntityArgument.getPlayer(context, "player");
        int amount = IntegerArgumentType.getInteger(context, "amount");
        if (receiver.getUUID().equals(payer.getUUID())) {
            source.sendFailure(Component.translatable("command.crownscoins.pay_self"));
            return 0;
        }

        List<CoinWallet.Holding> holdings = CoinWallet.holdings(payer.getInventory());
        Optional<CoinWallet.Holding> currency = chooseCurrency(source, payer, holdings, kingdomName);
        if (currency.isEmpty()) {
            return 0;
        }
        CoinWallet.Holding holding = currency.get();
        Optional<CoinWallet.Payment> plan = CoinWallet.plan(amount, holding.gold(), holding.iron(), holding.copper());
        if (plan.isEmpty()) {
            source.sendFailure(Component.translatable("command.crownscoins.pay_no_change", amount, holding.currencyName()));
            return 0;
        }

        for (ItemStack coins : CoinWallet.take(payer.getInventory(), holding.kingdomId(), plan.get())) {
            if (!receiver.getInventory().add(coins)) {
                receiver.drop(coins, false);
            }
        }
        source.sendSuccess(() -> Component.translatable("command.crownscoins.paid", amount, holding.currencyName(), receiver.getDisplayName()), false);
        ModAdvancements.award(payer, ModAdvancements.PAY);
        receiver.sendSystemMessage(Component.translatable("command.crownscoins.received", payer.getDisplayName(), amount, holding.currencyName()));
        return 1;
    }

    /**
     * Picks which currency to pay with: the named kingdom's, else the payer's own kingdom's
     * when they carry it, else the only currency they carry. Explains what to do otherwise.
     */
    private static Optional<CoinWallet.Holding> chooseCurrency(
        CommandSourceStack source, ServerPlayer payer, List<CoinWallet.Holding> holdings, String kingdomName
    ) {
        if (holdings.isEmpty()) {
            source.sendFailure(Component.translatable("command.crownscoins.balance_empty"));
            return Optional.empty();
        }
        KingdomSavedData data = KingdomSavedData.get(source.getLevel());
        if (kingdomName != null) {
            Optional<Kingdom> named;
            try {
                named = data.findByName(kingdomName);
            } catch (IllegalArgumentException invalidName) {
                named = Optional.empty();
            }
            if (named.isEmpty()) {
                source.sendFailure(Component.translatable("command.crownscoins.not_found", kingdomName));
                return Optional.empty();
            }
            UUID id = named.get().id();
            Optional<CoinWallet.Holding> match = holdings.stream().filter(holding -> holding.kingdomId().equals(id)).findFirst();
            if (match.isEmpty()) {
                source.sendFailure(Component.translatable("command.crownscoins.pay_no_coins", named.get().currencyName()));
            }
            return match;
        }
        Optional<Kingdom> own = data.findByMember(payer.getUUID());
        if (own.isPresent()) {
            UUID ownId = own.get().id();
            Optional<CoinWallet.Holding> ownHolding = holdings.stream().filter(holding -> holding.kingdomId().equals(ownId)).findFirst();
            if (ownHolding.isPresent()) {
                return ownHolding;
            }
        }
        if (holdings.size() == 1) {
            return Optional.of(holdings.get(0));
        }
        source.sendFailure(Component.translatable("command.crownscoins.pay_choose"));
        return Optional.empty();
    }
}
