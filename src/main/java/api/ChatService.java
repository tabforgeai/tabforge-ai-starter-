package api;

import java.io.Serializable;

import dyntabs.ai.Conversation;
import dyntabs.ai.EasyAI;
import jakarta.enterprise.context.SessionScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

/**
 * Session-scoped wrapper around an EasyAI {@link Conversation}, so each user
 * session keeps its own multi-turn chat memory — <em>and</em> narrates every turn
 * live into the Activity Panel.
 *
 * <h2>How the live narration is wired (the 2.1 recipe)</h2>
 * The Conversation is built with a single extra call — {@code .withEventListener(bridge)} —
 * where {@code bridge} is an {@link EasyAiActivityBridge} bound to this session. From then on
 * EasyAI emits a STARTED / RESULT / FINISHED event around each {@code send(..)}, the bridge maps
 * them to the template's wire format, and they appear as Activity rows + a chat bubble. That one
 * line is the <b>only</b> difference from a plain, silent {@code EasyAI.chat()} build.
 *
 * <h2>Why the listener is attached once, here</h2>
 * A {@code Conversation} carries the chat memory, so it is built once per session and reused.
 * The session id is stable for the life of the session, so the bridge is bound once at build time
 * (rebuilding per message would throw the memory away). The session id is supplied by the caller
 * ({@link ChatProcessor}) because it is read from the HTTP request, not available to this bean.
 *
 * <h2>Serialization note</h2>
 * The Conversation is {@code transient} and built lazily: a session may be passivated and the
 * underlying model client must not be serialized — {@link #conversation(String)} simply rebuilds
 * it on demand. {@link #channel} is an {@code @ApplicationScoped} CDI proxy and is serialization-safe.
 *
 * Provider, API key and model are read from {@code src/main/resources/easyai.properties}.
 */
@Named
@SessionScoped
public class ChatService implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final int    MEMORY_MESSAGES = 20;
    private static final String SYSTEM_MESSAGE  =
            "You are a helpful assistant embedded in a Jakarta EE application.";

    /** Application-scoped SSE registry the per-session bridge pushes events through. */
    @Inject
    private AiEventChannel channel;

    private transient Conversation conversation;

    /**
     * Lazily build (once) the session's Conversation, wiring the Activity-Panel bridge.
     *
     * @param sessionId the HTTP session id used by the bridge to target this user's SSE stream
     * @return the cached Conversation for this session
     */
    private Conversation conversation(String sessionId) {
        if (conversation == null) {
            conversation = EasyAI.chat()
                    .withMemory(MEMORY_MESSAGES)
                    .withSystemMessage(SYSTEM_MESSAGE)
                    .withEventListener(new EasyAiActivityBridge(channel, sessionId)) // ← live narration
                    .build();
        }
        return conversation;
    }

    /**
     * Send one user message; the reply is delivered to the chat bubble by the bridge (as an
     * {@code assistant_message} event), so callers usually ignore the returned value.
     *
     * <p>Unlike a "swallow-everything" helper, this method lets a provider/configuration error
     * propagate: EasyAI has already emitted an {@code ERROR} event (→ a red Activity row) by the
     * time it throws, and {@link ChatProcessor} adds a readable chat bubble. Keeping the two
     * concerns separate is what makes the bridge the single source of truth for the timeline.</p>
     *
     * @param sessionId the HTTP session id (SSE target for this turn's events)
     * @param userText  the message typed in the chat panel
     * @return the assistant reply text (also already streamed to the bubble via the bridge)
     */
    public String send(String sessionId, String userText) {
        return conversation(sessionId).send(userText);
    }
}
