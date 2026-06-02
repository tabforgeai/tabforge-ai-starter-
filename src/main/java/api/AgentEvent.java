package api;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * One event pushed from the backend to the AI panel over the SSE channel
 * ({@code GET /api/ai/stream}).
 *
 * <h2>Place in the flow</h2>
 * <pre>
 *   ChatProcessor / agent StepListener
 *        → AiEventChannel.emit(sessionId, AgentEvent)
 *        → SSE (JSON, serialized by JSON-B)
 *        → browser: PFTemplate.AgentTransport → AgentEventBus.emit(event)
 *        → layout.js renders it
 * </pre>
 *
 * <h2>How the client interprets {@link #type}</h2>
 * <ul>
 *   <li>{@code "assistant_message"} → routed into the chat bubble; uses {@link #text}
 *       (rendered with the typewriter animation by {@code aiPanel.streamAssistant}).</li>
 *   <li>everything else ({@code "agent_started"}, {@code "tool_call"},
 *       {@code "agent_finished"}, {@code "error"}, …) → a row in the Activity tab.</li>
 * </ul>
 *
 * Only non-null fields are serialized (JSON-B skips nulls), so each static factory
 * sets just the fields meaningful for that event type. Instances are created through
 * the factory methods, never with the bare constructor, so {@link #id} and
 * {@link #timestamp} are always populated.
 */
public class AgentEvent {

    /** Process-wide counter used to give every event a unique, ordered id. */
    private static final AtomicLong SEQ = new AtomicLong();

    private String id;
    private String type;
    private String status;
    private String title;
    private String agent;
    private String tool;
    private String details;
    private String text;
    private String timestamp;

    /** Required by JSON-B. Application code uses the static factories instead. */
    public AgentEvent() {
    }

    private static String nextId(String type) {
        return type + '-' + SEQ.incrementAndGet();
    }

    private static String now() {
        return Instant.now().toString();
    }

    /**
     * The agent has begun handling a request — the first Activity row.
     *
     * @param agent the logical agent name shown in the timeline (e.g. {@code "Assistant"})
     * @param title short human-readable label (e.g. {@code "Processing request"})
     * @return a populated {@code agent_started} event
     */
    public static AgentEvent agentStarted(String agent, String title) {
        AgentEvent e = base("agent_started", "running");
        e.agent = agent;
        e.title = title;
        return e;
    }

    /**
     * One completed tool call by the agent — an Activity row describing what was
     * called and what came back. Built from a {@code dyntabs.ai.agent.AgentStep}
     * inside {@link ChatProcessor}'s step listener.
     *
     * @param agent   the agent that made the call
     * @param tool    the tool/method name (e.g. {@code "getCurrentDateTime"})
     * @param details a short "arguments → result" summary
     * @return a populated {@code tool_call} event with success status
     */
    public static AgentEvent toolCall(String agent, String tool, String details) {
        AgentEvent e = base("tool_call", "success");
        e.agent = agent;
        e.tool = tool;
        e.title = tool;
        e.details = details;
        return e;
    }

    /**
     * The assistant's final reply — rendered in the chat bubble, not the Activity tab.
     *
     * @param text the reply text (Markdown supported on the client)
     * @return a populated {@code assistant_message} event
     */
    public static AgentEvent assistantMessage(String text) {
        AgentEvent e = base("assistant_message", null);
        e.text = text;
        return e;
    }

    /**
     * The agent finished — the closing Activity row.
     *
     * @param agent the agent name
     * @param title short label (e.g. {@code "Response ready"})
     * @return a populated {@code agent_finished} event with success status
     */
    public static AgentEvent agentFinished(String agent, String title) {
        AgentEvent e = base("agent_finished", "success");
        e.agent = agent;
        e.title = title;
        return e;
    }

    /**
     * Something failed during processing — an Activity warning/error row.
     * The caller typically also emits an {@link #assistantMessage} so the failure
     * is visible in the chat itself.
     *
     * @param title   short label (e.g. {@code "Agent failed"})
     * @param details the readable error message
     * @return a populated {@code error} event
     */
    public static AgentEvent error(String title, String details) {
        AgentEvent e = base("error", "error");
        e.title = title;
        e.details = details;
        return e;
    }

    /** Shared initializer: sets type, status, id and timestamp. */
    private static AgentEvent base(String type, String status) {
        AgentEvent e = new AgentEvent();
        e.type = type;
        e.status = status;
        e.id = nextId(type);
        e.timestamp = now();
        return e;
    }

    // ── Getters (read by JSON-B during serialization) ──────────────────────────
    public String getId()        { return id; }
    public String getType()      { return type; }
    public String getStatus()    { return status; }
    public String getTitle()     { return title; }
    public String getAgent()     { return agent; }
    public String getTool()      { return tool; }
    public String getDetails()   { return details; }
    public String getText()      { return text; }
    public String getTimestamp() { return timestamp; }
}
