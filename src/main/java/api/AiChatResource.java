package api;

import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * The OUTBOUND endpoint the AI chat panel posts each user message to.
 *
 * <h2>Place in the flow</h2>
 * <pre>
 *   browser: InputEventBus 'user_message' → fetch POST /api/ai/chat {"text": "..."}
 *        → this resource resolves the session id and hands off to {@link ChatProcessor}
 *        → returns 202 Accepted immediately (no reply body)
 *        → the actual reply + activity arrive on the separate SSE stream
 *          (GET /api/ai/stream, see {@link AiStreamResource})
 * </pre>
 *
 * The reply is intentionally NOT returned in this HTTP response: in the streaming design
 * it comes back as an {@code assistant_message} event over SSE, which the template routes
 * into the chat bubble. The two channels are correlated by HTTP session id.
 *
 * Wired in template.xhtml:
 * <pre>
 *   var AI_CHAT_ENDPOINT   = '#{request.contextPath}/api/ai/chat';
 *   var AI_STREAM_ENDPOINT = '#{request.contextPath}/api/ai/stream';
 *   PFTemplate.AgentTransport.connectSSE(AI_STREAM_ENDPOINT);
 *   PFTemplate.InputEventBus.on('user_message', m =&gt; fetch(AI_CHAT_ENDPOINT, ... JSON {text} ));
 * </pre>
 */
@Path("/ai")
public class AiChatResource {

    @Inject
    private ChatProcessor processor;

    /**
     * Accept one user message and kick off processing; the result streams back over SSE.
     *
     * @param request the JSON body {@code { "text": "..." }} (bound by JSON-B)
     * @param http    used only to obtain the session id correlating this POST with the SSE stream
     * @return {@code 202 Accepted} with no body — the reply is delivered via SSE
     */
    @POST
    @Path("/chat")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response chat(ChatRequest request, @Context HttpServletRequest http) {
        String sessionId = http.getSession(true).getId();
        String text = (request == null) ? null : request.getText();
        processor.process(sessionId, text);   // emits agent_started → … → assistant_message → agent_finished
        return Response.accepted().build();
    }

    /** JSON request body: <code>{ "text": "..." }</code>. Bound by JSON-B. */
    public static class ChatRequest {
        private String text;
        public String getText()            { return text; }
        public void   setText(String text) { this.text = text; }
    }
}
