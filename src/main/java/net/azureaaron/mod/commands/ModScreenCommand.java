package net.azureaaron.mod.commands;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;

import com.mojang.brigadier.tree.LiteralCommandNode;
import net.azureaaron.mod.Colour;
import net.azureaaron.mod.config.AaronModConfigManager;
import net.azureaaron.mod.screens.ModScreen;
import net.azureaaron.mod.utils.Constants;
import net.azureaaron.mod.utils.Scheduler;
import net.azureaaron.mod.utils.Skyblock;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.text.Text;

public class ModScreenCommand {
    private static final long ONE_TICK = 50L;

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandRegistryAccess registryAccess) {
        LiteralCommandNode<FabricClientCommandSource> baseCommand =
                dispatcher.register(literal("aaronsmod")
                        .executes(context -> handleCommand(context.getSource()))
                        .then(literal("config")
                                .executes(context -> handleOpenConfig(context.getSource())))
                        .then(literal("options")
                                .executes(context -> handleOpenConfig(context.getSource())))
                        .then(literal("reload")
                                .executes(context -> handleReload(context.getSource()))));
        dispatcher.register(literal("aaronmod").redirect(baseCommand));
    }

    private static int handleCommand(FabricClientCommandSource source) {
        MinecraftClient client = source.getClient();

        client.send(() -> client.setScreen(new ModScreen(null)));

        return Command.SINGLE_SUCCESS;
    }

    private static int handleOpenConfig(FabricClientCommandSource source) {
        MinecraftClient client = source.getClient();
        TimerTask timedTask = new TimerTask() {
            @Override
            public void run() {
                client.send(() -> client.setScreen(AaronModConfigManager.createGui(null)));
            }
        };

        new Timer().schedule(timedTask, ONE_TICK);

        return Command.SINGLE_SUCCESS;
    }

    private static int handleReload(FabricClientCommandSource source) {
        Colour.ColourProfiles colourProfile = AaronModConfigManager.get().colourProfile;
        if (!Skyblock.isLoaded()) {
            source.sendFeedback(
                    Constants.PREFIX.get().append(
                            Text.literal("Data is still in loop of getting loaded, please wait.")
                                    .withColor(colourProfile.primaryColour.getAsInt())
                    )
            );
        } else {
            source.sendFeedback(
                    Constants.PREFIX.get().append(
                            Text.literal("Data will be reloaded.")
                                    .withColor(colourProfile.primaryColour.getAsInt())
                    )
            );
            MinecraftClient client = source.getClient();
            Scheduler.schedule(() -> Skyblock.reloadData(client, true), 1, TimeUnit.SECONDS);
        }

        return Command.SINGLE_SUCCESS;
    }
}
