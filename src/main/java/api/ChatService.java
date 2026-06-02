package api;

import java.io.Serializable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dyntabs.ai.Conversation;
import dyntabs.ai.EasyAI;
import jakarta.enterprise.context.SessionScoped;
import jakarta.inject.Named;

/**
 * Session-scoped wrapper around an EasyAI {@link Conversation}, so each user
 * session keeps its own multi-turn chat memory.
 *
 * The Conversation is {@code transient} and built lazily: a session may be
 * passivated (serialized) by the server, and the underlying model client must
 * not be serialized — {@link #conversation()} simply rebuilds it on demand.
 *
 * Provider, API key and model are read from {@code src/main/resources/easyai.properties}.
 */
@Named
@SessionScoped
public class ChatService implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private static final int    MEMORY_MESSAGES = 20;
    private static final String SYSTEM_MESSAGE  =
            "You are a helpful assistant embedded in a Jakarta EE application.";

    private transient Conversation conversation;

    private Conversation conversation() {
        if (conversation == null) {
            conversation = EasyAI.chat()
                    .withMemory(MEMORY_MESSAGES)
                    .withSystemMessage(SYSTEM_MESSAGE)
                    .build();
        }
        return conversation;
    }

    /**
     * Send one user message and return the assistant's reply.
     *
     * Never throws: any provider or configuration error (e.g. a missing API key
     * in easyai.properties) is logged and returned as a readable message, so the
     * chat panel can display it instead of failing the request.
     *
     * @param userText the message typed in the chat panel
     * @return the assistant reply, or a human-readable error string
     */
    public String send(String userText) {
        if (userText == null || userText.isBlank()) {
            return "Please type a message.";
        }
        try {
            String reply = conversation().send(userText);
            return (reply == null || reply.isBlank()) ? "(The model returned an empty response.)" : reply;
        } catch (Exception e) {
            log.error("AI call failed", e);
            return "Sorry, something went wrong: " + EasyAI.extractErrorMessage(e);
        }
    }
}
