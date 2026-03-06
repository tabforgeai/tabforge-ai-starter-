# TabForge AI Starter

A pre-configured, ready-to-use Jakarta EE application that serves as the starting point for building apps with [TabForge AI](https://github.com/tabforgeai/tabforge-ai).

Everything is already wired up — multi-tab UI, CDI, JSF, security skeleton, and EasyAI configuration. Clone it, add your business logic, and start building.

---

## What's Included

- DynTabs multi-tab PrimeFaces UI (pre-configured)
- Jakarta EE 11 project structure (standard Maven layout)
- CDI beans, JSF navigation, and a login page skeleton
- `easyai.properties` template for AI provider configuration
- Logback for logging

## Requirements

- Java 21+
- Jakarta EE 11 application server (GlassFish 8, Payara 7, WildFly with EE 11 support)
- PrimeFaces 15+ (bundled via Maven)

## Getting Started

1. Clone or download this project
2. Add your tab beans and content pages following the [DynTabs guide](https://github.com/tabforgeai/tabforge-ai/blob/main/docs/dyntabs_guide.txt)
3. Optionally configure `src/main/resources/easyai.properties` for AI features
4. Build and deploy:

```
mvn clean package
```

Deploy `target/tabforge_ai_starter_1.0.war` to your application server.
