package com.example;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ExampleMod implements ModInitializer {
	public static final String MOD_ID = "modid";
	private static final String RESPONSES_API_URL = "https://api.openai.com/v1/responses";
	private static final Gson GSON = new Gson();

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	private final ExecutorService requestExecutor = Executors.newCachedThreadPool(runnable -> {
		Thread thread = new Thread(runnable, MOD_ID + "-ai-request");
		thread.setDaemon(true);
		return thread;
	});

	@Override
	public void onInitialize() {
		ModConfig config = ModConfig.load();
		HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(15))
			.build();

		LOGGER.info("Registering chat forwarding with response prefix '{}' using model '{}'", config.responsePrefix(), config.model());

		ServerMessageEvents.CHAT_MESSAGE.register((message, sender, parameters) ->
			handlePlayerMessage(sender, message.getSignedContent(), config, httpClient)
		);

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
			dispatcher.register(CommandManager.literal(config.commandName())
				.then(CommandManager.argument("message", StringArgumentType.greedyString())
					.executes(context -> {
						handlePlayerMessage(
							context.getSource().getPlayerOrThrow(),
							StringArgumentType.getString(context, "message"),
							config,
							httpClient
						);
						return 1;
					})
				)
			)
		);
	}

	private void handlePlayerMessage(ServerPlayerEntity player, String message, ModConfig config, HttpClient httpClient) {
		String trimmedMessage = message == null ? "" : message.trim();

		if (trimmedMessage.isEmpty()) {
			return;
		}

		if (config.apiKey().isEmpty()) {
			player.sendMessage(Text.literal(config.responsePrefix() + "Set api_key in config/fabric-ai-assistant.properties before using the AI assistant."), false);
			return;
		}

		MinecraftServer server = player.getServer();
		if (server == null) {
			LOGGER.warn("Skipping AI request for {} because the server reference was null", player.getName().getString());
			return;
		}

		UUID playerId = player.getUuid();
		String playerName = player.getName().getString();
		requestExecutor.submit(() -> requestAiReply(server, playerId, playerName, trimmedMessage, config, httpClient));
	}

	private void requestAiReply(
		MinecraftServer server,
		UUID playerId,
		String playerName,
		String playerMessage,
		ModConfig config,
		HttpClient httpClient
	) {
		try {
			String reply = fetchAiReply(playerName, playerMessage, config, httpClient);
			server.execute(() -> deliverReply(server, playerId, config.responsePrefix() + reply));
		} catch (Exception exception) {
			LOGGER.error("Failed to fetch AI reply for {}", playerName, exception);
			String playerFacingError = buildPlayerFacingError(exception);
			server.execute(() -> deliverReply(server, playerId, config.responsePrefix() + playerFacingError));
		}
	}

	private String fetchAiReply(String playerName, String playerMessage, ModConfig config, HttpClient httpClient) throws IOException, InterruptedException {
		JsonObject payload = new JsonObject();
		payload.addProperty("model", config.model());

		JsonArray input = new JsonArray();
		input.add(buildMessage("system", config.systemPrompt()));
		input.add(buildMessage("user", "Player " + playerName + " says: " + playerMessage));
		payload.add("input", input);

		HttpRequest request = HttpRequest.newBuilder(URI.create(RESPONSES_API_URL))
			.timeout(Duration.ofSeconds(45))
			.header("Authorization", "Bearer " + config.apiKey())
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(payload)))
			.build();

		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() < 200 || response.statusCode() >= 300) {
			throw new IOException("AI API returned " + response.statusCode() + ": " + response.body());
		}

		String outputText = extractOutputText(response.body());
		if (outputText == null || outputText.isBlank()) {
			throw new IOException("AI API response did not contain output_text");
		}

		return outputText.trim();
	}

	private JsonObject buildMessage(String role, String text) {
		JsonObject message = new JsonObject();
		message.addProperty("role", role);

		JsonArray content = new JsonArray();
		JsonObject textPart = new JsonObject();
		textPart.addProperty("type", "input_text");
		textPart.addProperty("text", text);
		content.add(textPart);

		message.add("content", content);
		return message;
	}

	private String extractOutputText(String responseBody) {
		JsonObject root = GSON.fromJson(responseBody, JsonObject.class);

		if (root == null) {
			return null;
		}

		JsonElement outputTextElement = root.get("output_text");
		if (outputTextElement != null && outputTextElement.isJsonPrimitive()) {
			return outputTextElement.getAsString();
		}

		JsonArray output = root.getAsJsonArray("output");
		if (output == null) {
			return null;
		}

		StringBuilder builder = new StringBuilder();
		for (JsonElement outputElement : output) {
			JsonObject outputObject = outputElement.getAsJsonObject();
			JsonArray content = outputObject.getAsJsonArray("content");
			if (content == null) {
				continue;
			}

			for (JsonElement contentElement : content) {
				JsonObject contentObject = contentElement.getAsJsonObject();
				JsonElement textElement = contentObject.get("text");
				if (textElement != null && textElement.isJsonPrimitive()) {
					if (!builder.isEmpty()) {
						builder.append('\n');
					}
					builder.append(textElement.getAsString());
				}
			}
		}

		return builder.isEmpty() ? null : builder.toString();
	}

	private String buildPlayerFacingError(Exception exception) {
		String message = exception.getMessage();
		if (message == null || message.isBlank()) {
			return "The AI request failed. Check the server log for details.";
		}

		if (message.contains("insufficient_quota")) {
			return "The configured OpenAI API key has no available quota or billing. Check the account's billing and usage limits.";
		}

		if (message.contains("model_not_found")) {
			return "The configured model name is invalid. Check the 'model' value in the server config.";
		}

		if (message.contains("401")) {
			return "The configured OpenAI API key was rejected. Check the 'api_key' value in the server config.";
		}

		return "The AI request failed. Check the server log for details.";
	}

	private void deliverReply(MinecraftServer server, UUID playerId, String reply) {
		ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
		if (player == null) {
			return;
		}

		for (String line : reply.split("\\R")) {
			String trimmedLine = line.trim();
			if (!trimmedLine.isEmpty()) {
				player.sendMessage(Text.literal(trimmedLine), false);
			}
		}
	}
}
