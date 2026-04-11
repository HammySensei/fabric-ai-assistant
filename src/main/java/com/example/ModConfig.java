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

	private final String responsePrefix;
	private final String commandName;

	private ModConfig(String responsePrefix, String commandName) {
		this.responsePrefix = responsePrefix;
		this.commandName = commandName;
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

			String responsePrefix = properties.getProperty("response_prefix", DEFAULT_RESPONSE_PREFIX);
			String commandName = sanitizeCommandName(properties.getProperty("chat_command", DEFAULT_COMMAND_NAME));

			if (!commandName.equals(properties.getProperty("chat_command"))) {
				properties.setProperty("chat_command", commandName);
				changed = true;
			}

			if (changed || Files.notExists(configPath)) {
				writeConfig(configPath, properties);
			}

			return new ModConfig(responsePrefix, commandName);
		} catch (IOException exception) {
			ExampleMod.LOGGER.error("Failed to load config from {}", configPath, exception);
			return new ModConfig(DEFAULT_RESPONSE_PREFIX, DEFAULT_COMMAND_NAME);
		}
	}

	private static void writeConfig(Path configPath, Properties properties) throws IOException {
		try (OutputStream outputStream = Files.newOutputStream(configPath)) {
			properties.store(outputStream, """
				fabric-ai-assistant config
				response_prefix: text prepended to bot replies
				chat_command: command name without the leading slash
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
}
