package api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dyntabs.ai.EasyAI;
import dyntabs.ai.EasyAgent;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;

/**
 * Orchestrates one user message: decides whether it is a plain chat turn or an agent
 * task, runs it, and lets EasyAI stream the whole story to the browser as a sequence of
 * {@link AgentEvent}s over SSE.
 *
 * <h2>The 2.1 pattern — one listener, no manual glue</h2>
 * Both paths attach an {@link EasyAiActivityBridge} via {@code .withEventListener(bridge)} and
 * then simply do their work. EasyAI itself emits the lifecycle events (started → tool calls →
 * finished, or started → reply → finished); the bridge turns each into an Activity row or a chat
 * bubble. This class adds only two things the core cannot know on its own:
 * <ul>
 *   <li>the agent's <b>final prose answer</b> (the agent narrates its steps but returns its
 *       answer as a value, so we push it to the bubble explicitly), and</li>
 *   <li>a <b>readable error bubble</b> if the operation throws (the bridge already drew the red
 *       Activity row; we add the human-facing apology in the chat).</li>
 * </ul>
 *
 * <h2>Place in the flow</h2>
 * <pre>
 *   AiChatResource (POST /api/ai/chat)
 *        → process(sessionId, text)
 *        → EasyAI.chat()/agent().withEventListener(bridge) … run
 *        → bridge → AiEventChannel → SSE → AI panel (chat bubble + Activity tab)
 * </pre>
 *
 * <h2>Two paths</h2>
 * <ul>
 *   <li><b>Chat (default):</b> any normal message → {@link ChatService} (session memory).
 *       Timeline: agent_started → assistant_message (reply) → agent_finished.</li>
 *   <li><b>Agent demo:</b> a message prefixed with {@value #AGENT_PREFIX} → {@link EasyAI#agent()}
 *       with {@link DemoToolService}. Each tool call surfaces live as a {@code tool_call} →
 *       {@code tool_result} pair. Example: {@code /agent what is 23 + 19 and what time is it?}</li>
 * </ul>
 *
 * Request-scoped: a fresh instance handles each POST; all cross-request state lives in the
 * application-scoped {@link AiEventChannel} and the session-scoped {@link ChatService}.
 */
@RequestScoped
public class ChatProcessor {

    private static final Logger log = LoggerFactory.getLogger(ChatProcessor.class);

    /** Messages beginning with this prefix are routed to the agent demo path. */
    static final String AGENT_PREFIX = "/agent ";

    @Inject
    private ChatService chatService;

    @Inject
    private DemoToolService tools;

    @Inject
    private AiEventChannel channel;

    /**
     * Handle one user message end-to-end; the outcome streams back over SSE.
     *
     * @param sessionId the HTTP session id used to target this user's SSE stream
     * @param userText  the raw message typed in the chat panel (may be null/blank)
     */
    public void process(String sessionId, String userText) {
        if (userText == null || userText.isBlank()) {
            channel.emit(sessionId, AgentEvent.assistantMessage("Please type a message."));
            return;
        }
        if (userText.startsWith(AGENT_PREFIX)) {
            runAgent(sessionId, userText.substring(AGENT_PREFIX.length()).trim());
        } else {
            runChat(sessionId, userText);
        }
    }

    /**
     * Plain chat turn: the session {@link ChatService} (with the bridge attached) narrates the
     * whole turn itself, so this method only has to translate a failure into a readable bubble.
     *
     * @param sessionId target SSE session
     * @param text      the user's message
     */
    private void runChat(String sessionId, String text) {
        try {
            chatService.send(sessionId, text); // bridge emits started → reply (bubble) → finished
        } catch (Exception e) {
            log.error("Chat failed", e);
            // the bridge already drew an 'error' Activity row; add the human-facing apology
            channel.emit(sessionId, AgentEvent.assistantMessage(
                    "Sorry, something went wrong: " + EasyAI.extractErrorMessage(e)));
        }
    }

    /**
     * Agent demo: build an {@link EasyAgent} over {@link DemoToolService} with the bridge attached,
     * then execute. The bridge surfaces every tool call live; we push the agent's final prose
     * answer to the chat bubble (the core narrates steps, not the closing sentence).
     *
     * @param sessionId target SSE session (the bridge is bound to it)
     * @param task      the task text (message with the {@value #AGENT_PREFIX} prefix removed)
     */
    private void runAgent(String sessionId, String task) {
        EasyAgent agent = EasyAI.agent()
                .withServices(tools)
                .withMaxSteps(8)
                .withPlanningPrompt(true)
                .withEventListener(new EasyAiActivityBridge(channel, sessionId)) // ← live tool-call feed
                .build();
        try {
            String reply = agent.execute(task);                               // bridge narrates the steps
            channel.emit(sessionId, AgentEvent.assistantMessage(reply));      // final prose → chat bubble
        } catch (Exception e) {
            log.error("Agent run failed", e);
            // the bridge already drew an 'error' Activity row; add the human-facing apology
            channel.emit(sessionId, AgentEvent.assistantMessage(
                    "Sorry, the agent failed: " + EasyAI.extractErrorMessage(e)));
        }
    }
}
