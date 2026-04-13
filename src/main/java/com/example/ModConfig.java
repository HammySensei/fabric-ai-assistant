package com.example;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class ModConfig {
	private static final String FILE_NAME = "fabric-ai-assistant.properties";
	private static final String DEFAULT_RESPONSE_PREFIX = "<server-ai> - ";
	private static final String DEFAULT_COMMAND_NAME = "ai";
	private static final String DEFAULT_MODEL = "gpt-4.1-mini";
	private static final String DEFAULT_SYSTEM_PROMPT = "You are a helpful Minecraft server assistant. Keep replies concise and directly answer the player's message.";
	private static final String DEFAULT_LOADING_TEXT = "Thinking...";

	private final String responsePrefix;
	private final String commandName;
	private final String apiKey;
	private final String model;
	private final String systemPrompt;
	private final String loadingText;

	private ModConfig(String responsePrefix, String commandName, String apiKey, String model, String systemPrompt, String loadingText) {
		this.responsePrefix = responsePrefix;
		this.commandName = commandName;
		this.apiKey = apiKey;
		this.model = model;
		this.systemPrompt = systemPrompt;
		this.loadingText = loadingText;
	}

	public static ModConfig load() {
		Path configPath = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
		Properties properties = new Properties();

		try {
			Files.createDirectories(configPath.getParent());

			if (Files.exists(configPath)) {
				try (InputStream inputStream = Files.newInputStream(configPath)) {
					properties.load(inputStream);
				}
			}

			boolean changed = false;

			if (!properties.containsKey("response_prefix")) {
				properties.setProperty("response_prefix", DEFAULT_RESPONSE_PREFIX);
				changed = true;
			}

			if (!properties.containsKey("chat_command")) {
				properties.setProperty("chat_command", DEFAULT_COMMAND_NAME);
				changed = true;
			}

			if (!properties.containsKey("api_key")) {
				properties.setProperty("api_key", "");
				changed = true;
			}

			if (!properties.containsKey("model")) {
				properties.setProperty("model", DEFAULT_MODEL);
				changed = true;
			}

			if (!properties.containsKey("system_prompt")) {
				properties.setProperty("system_prompt", DEFAULT_SYSTEM_PROMPT);
				changed = true;
			}

			if (!properties.containsKey("loading_text")) {
				properties.setProperty("loading_text", DEFAULT_LOADING_TEXT);
				changed = true;
			}

			String responsePrefix = properties.getProperty("response_prefix", DEFAULT_RESPONSE_PREFIX);
			String commandName = sanitizeCommandName(properties.getProperty("chat_command", DEFAULT_COMMAND_NAME));
			String apiKey = properties.getProperty("api_key", "").trim();
			String model = properties.getProperty("model", DEFAULT_MODEL).trim();
			String systemPrompt = properties.getProperty("system_prompt", DEFAULT_SYSTEM_PROMPT).trim();
			String loadingText = properties.getProperty("loading_text", DEFAULT_LOADING_TEXT).trim();

			if (!commandName.equals(properties.getProperty("chat_command"))) {
				properties.setProperty("chat_command", commandName);
				changed = true;
			}

			if (model.isEmpty()) {
				model = DEFAULT_MODEL;
				properties.setProperty("model", model);
				changed = true;
			}

			if (systemPrompt.isEmpty()) {
				systemPrompt = DEFAULT_SYSTEM_PROMPT;
				properties.setProperty("system_prompt", systemPrompt);
				changed = true;
			}

			if (loadingText.isEmpty()) {
				loadingText = DEFAULT_LOADING_TEXT;
				properties.setProperty("loading_text", loadingText);
				changed = true;
			}

			if (changed || Files.notExists(configPath)) {
				writeConfig(configPath, properties);
			}

			return new ModConfig(responsePrefix, commandName, apiKey, model, systemPrompt, loadingText);
		} catch (IOException exception) {
			ExampleMod.LOGGER.error("Failed to load config from {}", configPath, exception);
			return new ModConfig(DEFAULT_RESPONSE_PREFIX, DEFAULT_COMMAND_NAME, "", DEFAULT_MODEL, DEFAULT_SYSTEM_PROMPT, DEFAULT_LOADING_TEXT);
		}
	}

	private static void writeConfig(Path configPath, Properties properties) throws IOException {
		try (OutputStream outputStream = Files.newOutputStream(configPath)) {
			properties.store(outputStream, """
				fabric-ai-assistant config
				response_prefix: text prepended to bot replies
				chat_command: command name without the leading slash
				api_key: OpenAI API key used for AI requests
				model: OpenAI model name for responses
				system_prompt: instructions sent before each player message
				loading_text: chat message shown while the AI response is loading
				Example: chat_command=ai makes the command /ai
				""");
		}
	}

	private static String sanitizeCommandName(String rawCommandName) {
		if (rawCommandName == null) {
			return DEFAULT_COMMAND_NAME;
		}

		String sanitized = rawCommandName.trim();

		while (sanitized.startsWith("/")) {
			sanitized = sanitized.substring(1);
		}

		if (sanitized.isEmpty() || sanitized.contains(" ")) {
			ExampleMod.LOGGER.warn("Invalid chat_command '{}' in config, falling back to '{}'", rawCommandName, DEFAULT_COMMAND_NAME);
			return DEFAULT_COMMAND_NAME;
		}

		return sanitized;
	}

	public String responsePrefix() {
		return responsePrefix;
	}

	public String commandName() {
		return commandName;
	}

	public String apiKey() {
		return apiKey;
	}

	public String model() {
		return model;
	}

	public String systemPrompt() {
		return systemPrompt;
	}

	public String loadingText() {
		return loadingText;
	}
}
