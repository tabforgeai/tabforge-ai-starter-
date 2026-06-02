package api;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

/**
 * Activates Jakarta REST (JAX-RS) and roots every REST resource under /api.
 *
 * With this class present, no web.xml servlet mapping is needed — the server
 * scans for {@code @Path} resources automatically. Add more resources by
 * creating more {@code @Path}-annotated classes; they all share this prefix.
 */
@ApplicationPath("/api")
public class RestApplication extends Application {
}
