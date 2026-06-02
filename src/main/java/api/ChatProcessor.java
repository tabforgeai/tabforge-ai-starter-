package api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dyntabs.ai.EasyAI;
import dyntabs.ai.EasyAgent;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;

/**
 * Orchestrates one user message: decides whether it is a plain chat turn or an agent
 * task, runs it, and streams the result to the browser as a sequence of
 * {@link AgentEvent}s over SSE.
 *
 * <h2>Place in the flow</h2>
 * <pre>
 *   AiChatResource (POST /api/ai/chat)
 *        → process(sessionId, text)
 *        → emits: agent_started → [tool_call …] → assistant_message → agent_finished
 *        → AiEventChannel → SSE → AI panel (chat bubble + Activity tab)
 * </pre>
 *
 * <h2>Two paths</h2>
 * <ul>
 *   <li><b>Chat (default):</b> any normal message → {@link ChatService} (session memory).
 *       Produces a thin but real timeline: started → reply → finished.</li>
 *   <li><b>Agent demo:</b> a message prefixed with {@value #AGENT_PREFIX} → {@link EasyAI#agent()}
 *       with {@link DemoToolService}. The agent's tool calls surface live as {@code tool_call}
 *       rows in the Activity tab. Example: {@code /agent what is 23 + 19 and what time is it?}</li>
 * </ul>
 *
 * Request-scoped: a fresh instance handles each POST; all cross-request state lives in
 * the application-scoped {@link AiEventChannel} and the session-scoped {@link ChatService}.
 */
@RequestScoped
public class ChatProcessor {

    private static final Logger log = LoggerFactory.getLogger(ChatProcessor.class);

    /** Messages beginning with this prefix are routed to the agent demo path. */
    static final String AGENT_PREFIX = "/agent ";

    /** Max characters of a tool's arguments/result shown in an Activity row. */
    private static final int DETAIL_LIMIT = 120;

    @Inject
    private ChatService chatService;

    @Inject
    private DemoToolService tools;

    @Inject
    private AiEventChannel channel;

    /**
     * Handle one user message end-to-end and stream the outcome over SSE.
     *
     * Never throws: chat errors are already absorbed by {@link ChatService}, and agent
     * errors are caught here and surfaced as an {@code error} + {@code assistant_message}.
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
     * Plain chat turn: delegate to the session-scoped {@link ChatService} and stream the
     * reply. No tool calls, so the Activity tab shows only the start/finish rows.
     *
     * @param sessionId target SSE session
     * @param text      the user's message
     */
    private void runChat(String sessionId, String text) {
        channel.emit(sessionId, AgentEvent.agentStarted("Assistant", "Processing request"));
        String reply = chatService.send(text);            // blocking; never throws
        channel.emit(sessionId, AgentEvent.assistantMessage(reply));
        channel.emit(sessionId, AgentEvent.agentFinished("Assistant", "Response ready"));
    }

    /**
     * Agent demo: build an {@link EasyAgent} over {@link DemoToolService} and execute the
     * task. The step listener fires after each tool call and is mapped to a live
     * {@code tool_call} Activity row. The agent is built inside this method (not at field
     * init) so the injected {@link #tools} bean is available and the listener can close
     * over {@code sessionId}.
     *
     * @param sessionId target SSE session (captured by the step listener)
     * @param task      the task text (message with the {@value #AGENT_PREFIX} prefix removed)
     */
    private void runAgent(String sessionId, String task) {
        channel.emit(sessionId, AgentEvent.agentStarted("DemoAgent", "Planning task"));
        try {
            EasyAgent agent = EasyAI.agent()
                    .withServices(tools)
                    .withMaxSteps(8)
                    .withPlanningPrompt(true)
                    .withStepListener(step -> channel.emit(sessionId, AgentEvent.toolCall(
                            "DemoAgent",
                            step.toolName(),
                            truncate(step.arguments()) + " → " + truncate(step.result()))))
                    .build();

            String reply = agent.execute(task);
            channel.emit(sessionId, AgentEvent.assistantMessage(reply));
            channel.emit(sessionId, AgentEvent.agentFinished("DemoAgent", "Task complete"));
        } catch (Exception e) {
            log.error("Agent run failed", e);
            String readable = EasyAI.extractErrorMessage(e);
            channel.emit(sessionId, AgentEvent.error("Agent failed", readable));
            channel.emit(sessionId, AgentEvent.assistantMessage("Sorry, the agent failed: " + readable));
            channel.emit(sessionId, AgentEvent.agentFinished("DemoAgent", "Failed"));
        }
    }

    /**
     * Shorten a tool argument/result string for display in an Activity row.
     *
     * @param s the raw string (may be null)
     * @return {@code ""} for null, the string itself if short, or a truncated form with an ellipsis
     */
    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= DETAIL_LIMIT ? s : s.substring(0, DETAIL_LIMIT) + "…";
    }
}
