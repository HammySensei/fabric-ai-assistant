# fabric-ai-assistant
A server-side Fabric mod that adds a private AI assistant for Minecraft servers using server-specific knowledge.

The mod now creates `config/fabric-ai-assistant.properties` on first launch.

Editable settings:

- `response_prefix` controls the text prepended to replies.
- `chat_command` controls the command name without a leading slash. For example, `chat_command=ai` registers `/ai`.
