# TabForge AI Starter

A pre-configured, ready-to-use Jakarta EE application that serves as the starting point for
building apps with [TabForge AI](https://github.com/tabforgeai/tabforge-ai).

It ships with a modern UI ([**PF Modern Template**](https://github.com/tabforgeai/pf-modern-template))
and a **working AI assistant wired end-to-end** — chat and a tool-using agent, streamed live to the
browser over REST + Server-Sent Events. Clone it, drop in your AI provider key, add your business
logic, and start building.

---

## What's Included

- **PF Modern Template UI** — topbar, collapsible sidebar, light/dark/dim themes, command palette
  (`Ctrl+K`), status bar, and a built-in **AI Assistant panel** (Chat + live **Activity timeline**).
- **DynTabs** multi-tab PrimeFaces UI (pre-configured).
- **Working AI backend wiring** — the AI panel is connected to a real backend:
  - `POST /api/ai/chat` — receives the typed message (outbound).
  - `GET  /api/ai/stream` — Server-Sent Events channel that streams the reply and live agent
    activity back to the panel (inbound).
  - Powered by **EasyAI** (`tabforge-ai`), with per-session chat memory.
- **Demo agent + tools** (`DemoToolService`) — prefix a message with `/agent ` and the agent calls
  real tools (`getCurrentDateTime`, `add`, `getWeather`); each call shows up live in the Activity tab.
- Jakarta EE 11 structure, CDI, JSF navigation, and a Jakarta Security custom-form login.
- `easyai.properties` template for AI provider configuration.
- Logback for logging.

## Requirements

- Java 21+
- Jakarta EE 11 application server (GlassFish 8, Payara 7, WildFly with EE 11 support)
- PrimeFaces 15+ (bundled via Maven)

## Getting Started

1. Clone or download this project.
2. Configure your AI provider in `src/main/resources/easyai.properties` (see below). For a quick
   no-key start, point it at a local [Ollama](https://ollama.com) instance.
3. Build and deploy:

   ```
   mvn clean package
   ```

   Deploy `target/tabforge_ai_starter_1.2.war` to your application server.
   (See `BUILD_DEPLOY_UNDEPLOY.TXT` for ready-made GlassFish `asadmin` commands.)
4. Log in, open the **AI Assistant** panel (sparkle icon, top-right), and send a message.

## The AI Assistant (how it's wired)

The UI is **backend-agnostic** — it emits the user's message and renders whatever events the
backend streams back. All the wiring lives in the `api/` package and one script block in
`template.xhtml`; the template itself knows nothing about EasyAI.

| Path | What happens |
|------|--------------|
| **Chat** (any message) | `POST /api/ai/chat` → `ChatService` (EasyAI chat, session memory) → reply streamed back over SSE as `assistant_message` → rendered in the chat bubble. |
| **Agent demo** (`/agent …`) | `EasyAI.agent()` runs over `DemoToolService`; each tool call is emitted live as a `tool_call` event → shown in the **Activity** tab. Example: `/agent what is 23 + 19 and what time is it?` |

Two channels (outbound POST + inbound SSE) are correlated by HTTP session. Key classes:

```
api/RestApplication.java   @ApplicationPath("/api") — activates JAX-RS
api/AiChatResource.java    POST /api/ai/chat  → ChatProcessor (returns 202)
api/AiStreamResource.java  GET  /api/ai/stream → SSE subscription
api/AiEventChannel.java    session → SseBroadcaster registry; emits events
api/ChatProcessor.java     routes chat vs /agent; emits agent_started → … → assistant_message → agent_finished
api/ChatService.java       @SessionScoped EasyAI chat with memory
api/DemoToolService.java   sample tools the agent can call
api/AgentEvent.java        the JSON event shape sent over SSE
```

## Configure AI

Edit `src/main/resources/easyai.properties`:

```properties
easyai.provider=openai
easyai.api-key=sk-YOUR-KEY
easyai.model-name=gpt-4o-mini
```

For local [Ollama](https://ollama.com) (free, no key needed):

```properties
easyai.provider=ollama
easyai.model-name=llama3
```

Without a key the wiring still works end-to-end — you'll see a clear "no API key" message in the
chat, which confirms the request reached the provider and came back through the full stack.

## Going Further

- **Add a tab, AI assistant, RAG, or autonomous agent:** see `llms.txt` in this repo and the
  [TabForge AI guide](https://github.com/tabforgeai/tabforge-ai/blob/main/llms.txt).
- **Customize the UI** (logo, menu, themes): see the
  [PF Modern Template installation guide](https://github.com/tabforgeai/pf-modern-template/blob/main/doc/installation-guide.md).
