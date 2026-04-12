# fabric-ai-assistant
A server-side Fabric mod that forwards player chat messages to an AI model and sends the model's reply back to that player.

The mod creates `config/fabric-ai-assistant.properties` on first launch.

Editable settings:

- `response_prefix` controls the text prepended to replies.
- `chat_command` registers an optional manual trigger without a leading slash. For example, `chat_command=ai` registers `/ai <message>`.
- `api_key` stores the OpenAI API key used for requests.
- `model` selects the OpenAI model name used for responses.
- `system_prompt` defines the instructions prepended before each player message.

Normal player chat is forwarded automatically. The reply is sent privately back to the player who triggered it.
