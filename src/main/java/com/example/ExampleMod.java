package com.example;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.text.Text;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExampleMod implements ModInitializer {
	public static final String MOD_ID = "modid";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
			dispatcher.register(CommandManager.literal("ai")
				.executes(context -> {
					context.getSource().sendFeedback(
						() -> Text.literal("[AI] Test reply. Usage: /ai <message>"),
						false
					);
					return 1;
				})
				.then(CommandManager.argument("message", StringArgumentType.greedyString())
					.executes(context -> {
						String message = StringArgumentType.getString(context, "message");
						context.getSource().sendFeedback(
							() -> Text.literal("[AI] Test reply received: " + message),
							false
						);
						return 1;
					})
				)
			)
		);
	}
}
