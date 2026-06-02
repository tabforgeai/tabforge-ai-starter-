package api;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import jakarta.enterprise.context.Dependent;

/**
 * A tiny set of demo "tools" the agent path is allowed to call, so the Activity tab
 * shows real, multi-step agent work out of the box.
 *
 * <h2>Place in the flow</h2>
 * <pre>
 *   ChatProcessor.runAgent()
 *        → EasyAI.agent().withServices(demoToolService)
 *        → the model decides which method(s) to call to satisfy the task
 *        → each call surfaces as a 'tool_call' Activity row (via the StepListener)
 * </pre>
 *
 * EasyAI introspects the <em>public methods</em> of this object as callable tools. The
 * method name and parameter names matter (the {@code -parameters} compiler flag, already
 * set in pom.xml, preserves parameter names). Every method returns a {@code String} so
 * the result is directly usable by the model and readable in the timeline.
 *
 * Replace or extend these with your own services to make the agent do real work.
 *
 * Deliberately {@code @Dependent} (not a normal scope): EasyAI/LangChain4j inspect this
 * object's methods reflectively to discover tools, and a normal-scoped CDI client proxy
 * could hide parameter names ({@code -parameters}) and confuse tool discovery. {@code @Dependent}
 * injects the real instance, not a proxy.
 */
@Dependent
public class DemoToolService {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Returns the current server date and time. The agent calls this for questions
     * like "what time is it?".
     *
     * @return the current date-time formatted as {@code yyyy-MM-dd HH:mm:ss}
     */
    public String getCurrentDateTime() {
        return LocalDateTime.now().format(FMT);
    }

    /**
     * Adds two numbers. The agent calls this for simple arithmetic in a task.
     *
     * @param a the first addend
     * @param b the second addend
     * @return the sum, as a string
     */
    public String add(double a, double b) {
        return String.valueOf(a + b);
    }

    /**
     * Canned weather lookup for a city (demo only — returns a fixed answer, no real API).
     * Shows how a tool that takes a string argument appears in the Activity timeline.
     *
     * @param city the city name supplied by the model
     * @return a short, fixed weather description for that city
     */
    public String getWeather(String city) {
        return "Sunny, 24°C in " + city + " (demo data).";
    }
}
