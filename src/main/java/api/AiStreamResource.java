package api;

import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;

/**
 * Long-lived Server-Sent Events endpoint the AI panel connects to once at page load.
 *
 * <h2>Place in the flow</h2>
 * <pre>
 *   browser: PFTemplate.AgentTransport.connectSSE('/api/ai/stream')   (EventSource, GET)
 *        → this resource registers the connection in {@link AiEventChannel}
 *        → the connection stays open; events are pushed later by ChatProcessor
 * </pre>
 *
 * This is the INBOUND half of the two-channel design: the user's messages go OUT over
 * {@code POST /api/ai/chat}; agent activity and the chat reply come BACK over this SSE
 * stream. The two are correlated by HTTP session id (the {@code JSESSIONID} cookie is
 * sent on both the {@code EventSource} GET and the {@code fetch} POST, same-origin).
 *
 * {@code EventSource} reconnects on its own if the connection drops; each reconnect
 * simply registers a new sink with the session's broadcaster.
 */
@Path("/ai")
public class AiStreamResource {

    @Inject
    private AiEventChannel channel;

    /**
     * Open the SSE stream for the calling session.
     *
     * The method returns immediately, but the {@code sink} is kept open by the JAX-RS
     * runtime and by {@link AiEventChannel}; the browser receives events until it
     * closes the {@code EventSource} (e.g. on navigation).
     *
     * @param sse     JAX-RS SSE factory (injected) — passed to the channel to build events
     * @param sink    the output channel for this connection (injected) — registered with the channel
     * @param request used only to obtain the HTTP session id that correlates this stream with POSTs
     */
    @GET
    @Path("/stream")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public void stream(@Context Sse sse,
                       @Context SseEventSink sink,
                       @Context HttpServletRequest request) {
        String sessionId = request.getSession(true).getId();
        channel.subscribe(sessionId, sse, sink);
    }
}
