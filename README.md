# LLM LangChain4j — A LangChain4j Learning Backend

A Maven **multi-module** reactor ported from a sibling project (`llm-chat`) that used Spring AI
end-to-end. Every Spring AI integration has been replaced with **LangChain4j 1.16.3** — the goal
of this repo is to learn LangChain4j's real capabilities by rebuilding a working backend on top of
it, not to ship a product. Four independently runnable modules:

| Module                               | Port | Responsibility                                                                                                                                               |
|--------------------------------------|------|--------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [`llm-chat-agent`](./llm-chat-agent) | 8082 | Multi-turn chat with persistent memory, streaming, RAG, PDF/file reading, a guarded natural-language **text-to-SQL** endpoint, travel-guide and recipe demos |
| [`llm-audio`](./llm-audio)           | 8083 | Audio transcription (Whisper) and text-to-speech, and voice chat (calls `llm-chat-agent` over HTTP for the AI reply)                                         |
| [`llm-image`](./llm-image)           | 8084 | Image captioning (multimodal chat) and AI image generation (OpenAI Dall-E)                                                                                   |
| [`llm-playground`](./llm-playground) | 8085 | LangChain4j capabilities the other modules don't exercise: enum classification, structured extraction, summarization, standalone moderation                  |

All four share this repo's root `pom.xml` (a reactor parent extending `super-pom`, importing
`dev.langchain4j:langchain4j-bom:1.16.3`), the Maven wrapper, and `docker-compose.yml` (Postgres,
Redis, observability stack). Each module is its own Spring Boot app with its own
`application.yml`, API-key auth (`llm-playground` excepted — it has no persistence), and database
(`spring_ai`, `spring_ai_audio`, `spring_ai_image` — see `observability/init-db/`; names kept from
the original Spring AI project for infra continuity, not a library reference).

> Sibling services: [`llm-gateway`](../llm-gateway) (multi-provider routing + guardrails) and
> [`llm-rag-pipeline`](../llm-rag-pipeline) (ingestion + retrieval). This repo follows the same
> security, observability and project conventions as those two.

## 🛠️ Technology Stack

- **Spring Boot** 4.1.0 · **LangChain4j** 1.16.3 · **Java** 25 · **Maven**
- **OpenAI** (chat, embeddings, moderation, audio transcription, image generation) · the official
  **OpenAI Java SDK** directly for text-to-speech (LangChain4j has no TTS abstraction)
- **PostgreSQL** — chat memory, contacts, text-to-SQL data, API keys, document-ingestion tracking
- **Redis** — embedding store (`langchain4j-community-redis`), queried by the RAG content retriever
- **Spring Security** — API-key authentication (`X-API-Key`) + in-memory rate limiting
- **Observability**: Micrometer + Prometheus + Grafana + Tempo (traces) + Loki (logs)

## 🏗️ Layout

Each module is a self-contained Spring Boot app under `com.org.llm.*`; the package name repeats
across modules but they never share a classpath at runtime.

- **`llm-chat-agent/`** — `controller/` (chat, file, recipe, text-to-sql, RAG query-transform
  playground), `service/` (`ChatService`, `TravelGuideService`, `TextToSqlService`,
  `FileReadService`, `AnswerEvaluator`, …), `backend/` — **Strategy** pattern for where
  chat/travel-guide work executes (`ChatBackend`, `TravelPlanBackend`, each with a `Gateway*` and a
  `Local*` implementation, selected at startup by `app.gateway.enabled`), `assistant/` (LangChain4j
  `AiServices` interfaces: `ChatAssistant`, `TravelPlanAssistant`, `FaithfulnessJudge`),
  `guardrail/BlockedPhraseGuardrail`, `observability/LoggingChatModelListener`,
  `memory/JdbcChatMemoryStore`, `rag/` (query-transformer strategies, `RagFilterContext`,
  `RetrievedContentContext`, `CapturingContentRetriever`, `CompressThenExpandQueryTransformer`,
  `DocumentIngestionRunner`), `tool/` (weather, contacts), `config/` (`AIConfig`, `RagConfig`,
  `StartupValidator`).
- **`llm-audio/`** — `controller/` (`AudioController`, `VoiceChatController`), `service/`
  (`AudioService`, `VoiceChatService` — validate → store → transcribe → chat → synthesize),
  `client/ChatAgentClient` (calls `llm-chat-agent`'s `/chat` endpoint over HTTP for the AI reply).
- **`llm-image/`** — `controller/ImageRestController`, `service/ImageCaptionService`,
  `backend/ImageBackend` (`Gateway*`/`Local*` Dall-E strategy).
- **`llm-playground/`** — `assistant/` (`ClassifierAssistant`, `ExtractionAssistant`,
  `SummarizerAssistant`), `service/ModerationService`, `controller/PlaygroundController`,
  `config/AIConfig`. No database, no auth — a thin REST surface over a handful of `AiServices`.
- **Shared per module** (each module has its own copy — they're separate deployables, not a
  shared library): `security/` (`ApiKeyService`, `ApiKeyAuthFilter`, `RateLimitFilter`,
  `SecurityConfig`, `RestAuthenticationEntryPoint`), `exception/` (`GlobalExceptionHandler` +
  `ApiError`), `web/RequestIdFilter`, `config/ObservabilityConfig` + `AsyncConfig`.

## 🚀 Getting Started

### 1. Start infrastructure

```bash
docker compose up -d        # Postgres, Redis, RedisInsight + Prometheus/Grafana/Tempo/Loki
```

### 2. Configure secrets

```bash
export OPENAI_API_KEY=sk-...
export WEATHER_API_KEY=...            # only for llm-chat-agent's weather tool
```

(No Stability AI key — LangChain4j has no Stability AI integration, so `llm-image` now generates
with OpenAI Dall-E using the same `OPENAI_API_KEY`.)

### 3. Run each module you need

```bash
./mvnw -pl llm-chat-agent spring-boot:run    # port 8082
./mvnw -pl llm-audio spring-boot:run         # port 8083 — calls llm-chat-agent for voice-chat replies
./mvnw -pl llm-image spring-boot:run         # port 8084
./mvnw -pl llm-playground spring-boot:run    # port 8085 — no DB/auth, just AiServices demos
```

Or build/test the whole reactor from the root: `./mvnw verify`. Each module serves under context
path **`/ai`** on its own port (e.g. http://localhost:8082/ai).

## 🔑 Authentication

- API-key auth is **enabled by default** in `llm-chat-agent`, `llm-audio`, and `llm-image` — each
  request must include `X-API-Key` (`llm-playground` has no auth/persistence layer at all)
- Excluded from auth: actuator endpoints, the demo static HTML pages, and `/error`
- Keys are stored as SHA-256 hashes in each module's own `api_keys` PostgreSQL table (separate
  databases — `spring_ai`, `spring_ai_audio`, `spring_ai_image` — so a key minted for one module
  doesn't work on another) — raw values are never persisted
- Flyway seeds a **development key** per module, ready to use immediately:

```
llm-chat-agent: X-API-Key: llm-chat-dev-key-2026
llm-audio:      X-API-Key: llm-audio-dev-key-2026
llm-image:      X-API-Key: llm-image-dev-key-2026
```

```bash
curl -s "http://localhost:8082/ai/api/v1/recipe?ingredients=eggs,flour" \
  -H "X-API-Key: llm-chat-dev-key-2026"
```

Mint a real key (against the relevant module's database):

```bash
raw=$(openssl rand -hex 32)
hash=$(printf "%s" "$raw" | shasum -a 256 | cut -d' ' -f1)
psql -h localhost -U postgres -d spring_ai \
  -c "INSERT INTO api_keys (key_hash, label) VALUES ('$hash', 'my-client');"
echo "X-API-Key: $raw"
```

- To disable auth for local development: set `API_AUTH_ENABLED=false` (or `app.security.auth-enabled=false`)
- The demo HTML UIs under `/ai/*.html` assume an open instance or that you inject the dev key directly

## 🔀 Routing through llm-gateway

- By default (`app.gateway.enabled=true`), `llm-chat-agent`'s chat/structured travel-guide and
  `llm-image`'s image generation are **routed through `llm-gateway`** rather than calling
  OpenAI directly
- The gateway owns provider API keys, guardrails, failover logic, and per-session memory — centralising those concerns
  outside these services; it has no LangChain4j involvement at all (plain `WebClient` calls), so it's untouched by this
  migration
- When `GATEWAY_ENABLED=false`, the module calls its provider directly via LangChain4j (the path this README documents)
- `llm-image`'s captioning, `llm-audio`'s transcription/TTS, and `llm-chat-agent`'s file reading **always run locally
  ** — the gateway exposes no such endpoints

| Flow                                  | Gateway call                                   |
|---------------------------------------|------------------------------------------------|
| `llm-chat-agent` `/chat`              | `POST /llm/chat` (session = `conversationId`)  |
| `llm-chat-agent` `/chat/stream`       | `POST /llm/{provider}/stream` (SSE)            |
| `llm-chat-agent` `/chat/travel-guide` | `POST /llm/query` (strict-JSON → `TravelPlan`) |
| `llm-image` `/images/generate`        | `POST /llm/image` (OpenAI DALL·E)              |

- Configure via `app.gateway.*` env vars: `GATEWAY_ENABLED`, `GATEWAY_BASE_URL`, `GATEWAY_API_KEY`, `GATEWAY_PROVIDER`,
  `GATEWAY_MODEL` (`llm-image` also has `GATEWAY_IMAGE_MODEL`)
- Recommended run order for the full stack: `llm-gateway` (8080) → `llm-chat-agent` (8082) → `llm-audio` (8083) /
  `llm-image` (8084)

## 📡 Endpoints

### `llm-chat-agent` (port 8082, under `/ai`)

| Method | Path                          | Description                                                                                            |
|--------|-------------------------------|--------------------------------------------------------------------------------------------------------|
| POST   | `/api/v1/chat`                | Multi-turn chat (memory via `conversationId`)                                                          |
| POST   | `/api/v1/chat/stream`         | Server-sent streaming chat                                                                             |
| GET    | `/api/v1/chat/memory`         | Inspect conversation memory                                                                            |
| GET    | `/api/v1/chat/travel-guide`   | Structured travel-guide response                                                                       |
| POST   | `/api/v1/files/read`          | Read/summarise an uploaded file                                                                        |
| GET    | `/api/v1/recipe`              | Generate a recipe from ingredients                                                                     |
| POST   | `/api/v1/text-to-sql`         | NL → guarded read-only SQL + results                                                                   |
| POST   | `/api/v1/rag/query-transform` | Run a query through a single pre-retrieval transformer (rewrite/translate/compress/multi-query-expand) |

### `llm-audio` (port 8083, under `/ai`)

| Method | Path                       | Description                                                  |
|--------|----------------------------|--------------------------------------------------------------|
| POST   | `/api/v1/chat/audio`       | Chat with audio input (calls `llm-chat-agent` for the reply) |
| POST   | `/api/v1/chat/audio/voice` | Voice-to-voice chat                                          |
| POST   | `/api/v1/audio/to-text`    | Transcribe audio                                             |
| POST   | `/api/v1/audio/to-speech`  | Text-to-speech                                               |
| POST   | `/api/v1/audio/upload`     | Upload + process an audio file                               |

### `llm-image` (port 8084, under `/ai`)

| Method | Path                      | Description                                 |
|--------|---------------------------|---------------------------------------------|
| POST   | `/api/v1/images/caption`  | Caption an image                            |
| GET    | `/api/v1/images/generate` | Generate an image (gateway or local Dall-E) |

### `llm-playground` (port 8085, under `/ai`)

| Method | Path                           | Description                                    |
|--------|--------------------------------|------------------------------------------------|
| POST   | `/api/v1/playground/classify`  | Sentiment classification → `enum` output       |
| POST   | `/api/v1/playground/extract`   | Structured extraction → nested record output   |
| POST   | `/api/v1/playground/summarize` | Plain-text summarization, parameterized length |
| POST   | `/api/v1/playground/moderate`  | Standalone moderation check (no chat involved) |

## 📊 Observability

See [`PROMETHEUS_GRAFANA_SETUP.md`](./PROMETHEUS_GRAFANA_SETUP.md). Health at
`/ai/actuator/health`, Prometheus scrape at `/ai/actuator/prometheus`, Grafana at
http://localhost:3000 (admin/admin) with the auto-provisioned **LLM Chat** dashboard.
(`llm-playground` has no actuator/observability wiring — it's a bare demo module.)

### Actuator endpoints

| Endpoint                  | Description                                                                        |
|---------------------------|------------------------------------------------------------------------------------|
| `/ai/actuator/health`     | Full component health (DB, Redis, liveness/readiness probes)                       |
| `/ai/actuator/info`       | Build info (version, time), git info (branch, commit, dirty flag), Java/OS details |
| `/ai/actuator/metrics`    | Micrometer metrics                                                                 |
| `/ai/actuator/prometheus` | Prometheus scrape target                                                           |

- `/actuator/info` is enriched at build time by `spring-boot-maven-plugin` (`build-info` goal) and
  `git-commit-id-maven-plugin`
- Run `./mvnw package` to populate build timestamp, version, and Git commit details into the info endpoint

## 🧱 Configuration

- All tunables live in `application.yml` and accept environment variable overrides at runtime
- Key environment variables: `SERVER_PORT`, `POSTGRES_*`, `REDIS_*`, `API_AUTH_ENABLED`, `RATE_LIMIT_ENABLED`,
  `CORS_ALLOWED_ORIGINS`, `OTEL_EXPORTER_OTLP_ENDPOINT`, `CHAT_MODEL`, `EMBEDDING_MODEL`, `IMAGE_MODEL`
- No rebuild required — all knobs are externalised and take effect on the next startup

## ✅ Build & Test

```bash
./mvnw verify        # compile, test, JaCoCo coverage report (target/site/jacoco)
```

- Integration tests use **Testcontainers** (`TestcontainersConfiguration` + `@ServiceConnection`)
- A throwaway `postgres:18` container is started per test run — no locally provisioned database needed, only Docker
- The `EmbeddingStore` bean is `@MockitoBean`-replaced in `LLMApplicationTests` rather than run against a real Redis
  container, since `RedisEmbeddingStore` calls `FT.CREATE` on construction, which needs the RediSearch module (a
  `redis/redis-stack-server` image, not plain `redis:7-alpine`) — out of scope for a quick context-load test
- Flyway migrations and all JDBC queries in tests run against the real Postgres 18 container
- Validator logic (`SqlValidator`, `AudioValidator`) and the new `BlockedPhraseGuardrail` are covered by plain unit
  tests with no container dependency

## Technology Deep Dive

This section explains every significant library, framework, database, and infrastructure component used in this
project — what it is and exactly how it is wired up here.

---

### Spring Boot 4.1.0

**What it is.**

- Spring Boot is an opinionated framework that auto-configures a production-ready Java application from a single `main`
  class and a classpath of starter JARs
- Version 4.x requires Java 17+ and brings the `jakarta.*` namespace (Jakarta EE 11); this project runs on Java 25
- Auto-configuration is now further modularised — each technology ships its own auto-config module rather than bundling
  everything in one jar

**How it's used here.**

- Entry point is `LLMApplication`; `spring-boot-starter-web` stands up a Tomcat servlet container on port 8082 under
  context path `/ai`
- `spring-boot-starter-validation` enables `@Valid` on controller method parameters for automatic request-body
  validation
- `server.shutdown: graceful` with a 30-second drain window ensures rolling deployments do not drop in-flight requests
- The Spring Boot Maven plugin is configured with the `build-info` goal so `/ai/actuator/info` reports build timestamp,
  version, and Git commit
- LangChain4j has **no Spring Boot starter used here** — every `ChatModel`/`EmbeddingModel`/`ImageModel`/`AiServices`
  bean is a plain `@Bean` method in a `@Configuration` class (`AIConfig`, `RagConfig`); Spring's only role is dependency
  wiring and lifecycle, same as it would be for any other library

---

### LangChain4j 1.16.3

**What it is.**

- LangChain4j is a Java library for building LLM-powered applications: model abstractions
  (`ChatModel`, `StreamingChatModel`, `EmbeddingModel`, `ImageModel`, `AudioTranscriptionModel`,
  `ModerationModel`), a declarative `AiServices` proxy-based API (interfaces become working LLM
  clients), a modular RAG pipeline (`RetrievalAugmentor`, query transformers, content
  retrievers/aggregators/injectors), `@Tool`/`@P` function calling, structured output via
  reflection-derived JSON schemas, and a guardrail system (`InputGuardrail`/`OutputGuardrail`)
- Pinned here via `dev.langchain4j:langchain4j-bom:1.16.3` imported in the root `pom.xml`'s
  `dependencyManagement` — every module's `langchain4j`/`langchain4j-open-ai` version comes from
  that BOM; only the **community** modules (`langchain4j-community-redis`) sit on their own
  separate beta version track and need an explicit version

**How it's used here.**

- **`AiServices`** (built in `AIConfig`) replaces Spring AI's `ChatClient` + advisor chain: a
  single `ChatAssistant` interface is built once with `.chatModel()`, `.streamingChatModel()`,
  `.chatMemoryProvider()`, `.moderationModel()`, `.retrievalAugmentor()`, `.inputGuardrails()`, and
  `.tools()` — all fixed at construction time, unlike Spring AI's per-call `.advisors()`/`.tools()`
  attachment on `ChatClient.prompt()`
- **`BlockedPhraseGuardrail` (`InputGuardrail`)** blocks known jailbreak phrases before any model
  call — LangChain4j has no advisor-ordering concept (a guardrail always runs first), so it
  occupies the same "first line of defense" position `SafeGuardAdvisor.order(Integer.MIN_VALUE)`
  did in the Spring AI version
- **`JdbcChatMemoryStore` (`ChatMemoryStore`) + `MessageWindowChatMemory`** persist conversation
  history to PostgreSQL as JSON (via `ChatMessageSerializer`/`ChatMessageDeserializer`) and cap the
  window at 50 messages — LangChain4j ships the pluggable `ChatMemoryStore` *interface* but no JDBC
  implementation of its own, so this one is hand-written (see `db/migration/V6__create_chat_memory.sql`)
- **`@Moderate`** on `ChatAssistant.chat()`, backed by an `OpenAiModerationModel` bean, moderates
  every input automatically — added as an extra (Spring AI's version didn't have this) specifically
  to exercise LangChain4j's moderation-in-AiServices capability
- **`OpenAiAudioTranscriptionModel`** is injected into `llm-audio`'s `AudioService` for Whisper
  transcription; LangChain4j has **no text-to-speech abstraction at all** (verified: zero
  speech-synthesis classes in any artifact), so TTS calls the official OpenAI Java SDK directly —
  see the gap callout below
- **`ApachePdfBoxDocumentParser` + `DocumentSplitters.recursive()` + `EmbeddingStoreIngestor`**
  (`DocumentIngestionRunner`) parse, chunk, and embed the two corporate PDFs into Redis on
  startup — nothing in the original Spring AI version did this in this module (ingestion lived in
  a separate upstream service); the orphaned `app.documents.*` config implied it belonged here
- **`RedisEmbeddingStore`** (`langchain4j-community-redis`, built directly against a `JedisPooled`
  client — no Spring Boot starter, see the Redis section) is queried by an
  `EmbeddingStoreContentRetriever`, wrapped in a `CapturingContentRetriever` decorator that stashes
  what it retrieved into `RetrievedContentContext` for the citations response
- **`DefaultRetrievalAugmentor`** (`RagConfig`) wires the RAG pipeline:
  `CompressThenExpandQueryTransformer` (a custom class composing `CompressingQueryTransformer` then
  `ExpandingQueryTransformer` in plain Java — `DefaultRetrievalAugmentor` only has a single
  `queryTransformer` slot, unlike Spring AI's transformer-list-plus-separate-expander shape) →
  the content retriever above → `DefaultContentAggregator` → `DefaultContentInjector`
- **`MetadataFilterBuilder`** scopes a single chat turn's retrieval to one document by `fileName`
  metadata, via `RagFilterContext` (see "Per-request document filtering" below)
- **`PromptTemplate`** (LangChain4j's, Mustache-style `{{var}}` syntax) loads the
  `travel-guide.st` file and fills `{{city}}` / `{{days}}` placeholders before passing the prompt
  to the travel-plan backend
- **`@Tool`/`@P`** on `WeatherTools.getWeather` and `ContactsTool.findContactsByCity`/`formatAsCsv`
  registers those methods as callable functions the LLM can invoke during a chat turn — same
  function-calling capability Spring AI's `@Tool` provided, registered via `.tools(weatherTools,
  contactsTool)` on the `AiServices` builder instead of per-call on `ChatClient.prompt()`
- **`FaithfulnessJudge` (a custom `AiServices` interface)** replaces Spring AI's
  `FactCheckingEvaluator` — LangChain4j ships **no built-in RAG-answer evaluator at all**, so this
  is a hand-written "LLM-as-judge" interface returning a structured `FaithfulnessVerdict(boolean
  pass, String reasoning)`, gated behind `app.rag.evaluate-faithfulness`
- **Structured output** (`TravelPlanAssistant.plan()`, `FaithfulnessJudge.check()`, and every
  `llm-playground` assistant) returns a record/enum directly — LangChain4j reflects the return type
  into a JSON schema and parses the model's reply into it automatically, the same role Spring AI's
  `.call().entity(TravelPlan.class)` played

**Why an `AiServices` instance is built once, not per call**

Spring AI's `ChatClient` is a flexible builder you reconfigure on every `.prompt()` call —
`LocalChatBackend` attached `.tools(weatherTools, contactsTool)` and the RAG advisor fresh on each
`chat()`/`stream()` invocation. LangChain4j's `AiServices` proxy is built once and fixes its tools,
memory provider, retrieval augmentor, and guardrails at construction time; per-call concerns (the
optional `documentSource` filter, the dynamic system prompt with today's date) are passed as method
parameters (`@V("systemPrompt")`) or threaded through a `ThreadLocal` the singleton beans read from
(`RagFilterContext`, same trick the Spring AI version already used for its per-request filter).

**Capturing citations without a per-call response wrapper**

Spring AI's `ChatClientResponse.context()` exposed whatever the `RetrievalAugmentationAdvisor`
retrieved for that specific call. LangChain4j's plain `String chat(...)` method on an `AiServices`
interface returns no such wrapper, so two different mechanisms cover the two call shapes:

- **Non-streaming** (`ChatAssistant.chat()`): `CapturingContentRetriever` (a `ContentRetriever`
  decorator wrapping the real `EmbeddingStoreContentRetriever`) writes every retrieval's results
  into `RetrievedContentContext`, a `ThreadLocal` `LocalChatBackend` reads right after the call —
  safe because retrieval happens synchronously on the calling thread before the method returns.
- **Streaming** (`ChatAssistant.chatStream()`): `TokenStream#onRetrieved(Consumer<List<Content>>)`
  hands back exactly what was retrieved as a native callback — no `ThreadLocal` needed here at all.

**Per-request document filtering (`MetadataFilterBuilder`)**

`ChatRequest.documentSource` lets a caller scope a single chat turn's retrieval to one pre-loaded
document, instead of searching across all of them:

```json
POST /api/v1/chat
{
  "conversationId": "conv-1",
  "message": "How many days of annual leave do I get?",
  "documentSource": "AtlasCorp-TravelPolicy.pdf"
}
```

The `EmbeddingStoreContentRetriever` bean in `RagConfig` is a singleton, but the filter is
per-request, so it can't be passed as a fixed builder argument. Instead,
`dynamicFilter(Function<Query, Filter>)` is wired to `query -> ragFilterContext.get()` — a
`ThreadLocal` holder (`com.org.llm.rag.RagFilterContext`). `LocalChatBackend` builds a `Filter`
with `MetadataFilterBuilder.metadataKey("fileName").isEqualTo(documentSource)` when `documentSource`
is present, calls `ragFilterContext.set(...)` before invoking the `ChatAssistant`, and clears it in
a `finally`/`doFinally` block once the call (or stream) completes. When `documentSource` is absent,
the function returns `null` and retrieval is unfiltered, as before.

**Conversation ID flow**

Each chat request passes `conversationId` as an `@MemoryId`-annotated parameter on the
`AiServices` interface method:

```java
String chat(@MemoryId String conversationId, @V("systemPrompt") String systemPrompt, @UserMessage String message);
```

`AiServices` routes that value into the `ChatMemoryProvider` (`memoryId -> MessageWindowChatMemory...`)
to fetch and save the correct message window — no per-request state lives in any Spring bean, the
same statelessness Spring AI's `ChatMemory.CONVERSATION_ID` advisor parameter provided.

**Query transformation playground**

`CompressingQueryTransformer` and `ExpandingQueryTransformer` are the only two pre-retrieval
transformers LangChain4j ships (verified: `dev.langchain4j.rag.query.transformer` contains exactly
these two plus the `QueryTransformer` interface and a no-op default). `POST
/api/v1/rag/query-transform` exposes four techniques — the two real ones, plus two hand-written
replacements for capabilities LangChain4j doesn't have:

| Technique            | Backing implementation                                                                | What it does                                                                          |
|----------------------|---------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------|
| `REWRITE`            | Custom prompt against the deterministic RAG `ChatModel` (no LangChain4j class exists) | Rewrites a messy/conversational query into a clean, standalone search query           |
| `TRANSLATE`          | Custom prompt against the deterministic RAG `ChatModel` (no LangChain4j class exists) | Translates the query into the language of the indexed documents (defaults to English) |
| `COMPRESS`           | `CompressingQueryTransformer`                                                         | Folds conversation history + the current query into one standalone query              |
| `MULTI_QUERY_EXPAND` | `ExpandingQueryTransformer`                                                           | Generates several paraphrased variants of the query to improve recall                 |

```json
POST /api/v1/rag/query-transform
{
  "technique": "COMPRESS",
  "query": "what about the second one?",
  "history": ["What is the capital of Denmark?", "Copenhagen is the capital of Denmark."]
}
```

```json
{
  "technique": "COMPRESS",
  "originalQuery": "what about the second one?",
  "transformedQueries": ["What is the second largest city in Denmark?"]
}
```

**Design — Strategy pattern.** Each technique is a `com.org.llm.rag.QueryTransformationStrategy`
bean (`RewriteQueryStrategy`, `TranslateQueryStrategy`, `CompressQueryStrategy`,
`MultiQueryExpansionStrategy`), tagged by a `QueryTransformationTechnique` enum value.
`QueryTransformationService` collects all of them via constructor injection
(`List<QueryTransformationStrategy>`) into a `Map<QueryTransformationTechnique,
QueryTransformationStrategy>` and dispatches each request to the matching one — no `if`/`switch`
chain. The shared deterministic `ChatModel` bean (`ragChatModel`, temperature 0.0, `@Qualifier`-ed
to disambiguate it from the main conversational `ChatModel`) backs `CompressQueryStrategy` and
`MultiQueryExpansionStrategy` directly, and the two hand-written strategies (`RewriteQueryStrategy`,
`TranslateQueryStrategy`) use it for their custom prompts — so transformation calls never affect
the main conversation's temperature/options.

This endpoint is a playground for inspecting each technique's raw output — it does not itself
touch the embedding store. The production retrieval path (`LocalChatBackend.chat()` / `.stream()`)
wires `CompressingQueryTransformer` and `ExpandingQueryTransformer` together via
`CompressThenExpandQueryTransformer` and the `DefaultRetrievalAugmentor` bean described above.

---

### OpenAI API

**What it is.**

- OpenAI provides GPT-series chat-completion models, the Whisper speech-recognition model, TTS
  voice-synthesis models, embedding models, a moderation model, and Dall·E image generation
- Access is REST-based, authenticated with a bearer token supplied as `OPENAI_API_KEY`

**How it's used here.**

- In `llm-chat-agent`, when `app.gateway.enabled=false` (direct mode), `LocalChatBackend` calls
  OpenAI's chat-completion API via `ChatAssistant` (LangChain4j's `OpenAiChatModel`/`OpenAiStreamingChatModel`)
- In `llm-image`, `LocalImageBackend` calls OpenAI's image endpoint via LangChain4j's
  `OpenAiImageModel` (Dall-E)
- In `llm-audio`, `AudioService` calls Whisper (`whisper-1`) via LangChain4j's
  `OpenAiAudioTranscriptionModel` for transcription, and the **official OpenAI Java SDK directly**
  (`com.openai:openai-java`, `client.audio().speech().create(...)`) with `tts-1`/voice `echo` for
  speech synthesis — LangChain4j has no abstraction over this endpoint at all
- The API key is read from `OPENAI_API_KEY` (each module reads its own copy of the env var) directly
  via `@Value` in each module's `AIConfig`/`RagConfig` — LangChain4j has no auto-configuration to
  hide it behind, so every model/client bean takes it as an explicit constructor argument
- Each module's own `StartupValidator` fails the application at boot if its required key is missing, giving an immediate
  and unambiguous error

---

### LangChain4j vs Spring AI 2.0 — Feature Comparison

Built by inspecting the actual `dev.langchain4j` jars (`javap`, `unzip -l`) and the LangChain4j
GitHub source for the version pinned here (1.16.3), not secondhand blog posts — several
capabilities below don't show up clearly in the online docs.

| Capability                   | Spring AI 2.0                                                                                                                                        | LangChain4j 1.16.3                                                                                                                                                                                                           | Notes                                                                                                                                                                                    |
|------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Chat model abstraction       | `ChatModel` + `ChatClient` (fluent builder, per-call advisors/tools)                                                                                 | `ChatModel` (sync) + `StreamingChatModel`; `AiServices` (declarative proxy, fixed at build time)                                                                                                                             | Two different mental models: Spring AI lets you reconfigure per call; LangChain4j fixes an interface's behavior once and passes per-call concerns as method parameters                   |
| Streaming                    | `ChatClient.stream()` → `Flux<String>`/`ChatClientResponse`                                                                                          | `StreamingChatModel` + `TokenStream` (callback-based: `onPartialResponse`, `onRetrieved`, `onCompleteResponse`, `onError`)                                                                                                   | LangChain4j's `TokenStream.onRetrieved` gives RAG citations natively mid-stream; Spring AI needs the advisor-context trick (`DOCUMENT_CONTEXT`)                                          |
| Conversation memory          | `ChatMemory` + `MessageWindowChatMemory`; JDBC repo via `spring-ai-starter-model-chat-memory-repository-jdbc` (own DDL, `initialize-schema: always`) | `ChatMemory` + `MessageWindowChatMemory`; pluggable `ChatMemoryStore` **interface only** — no JDBC/SQL implementation ships at all                                                                                           | This repo hand-writes `JdbcChatMemoryStore` + its own Flyway migration to get JDBC persistence                                                                                           |
| Guardrails / safety          | `SafeGuardAdvisor` (sensitive-word blocking, advisor-ordered)                                                                                        | `InputGuardrail`/`OutputGuardrail` (`dev.langchain4j.guardrail`), richer result model (`success`/`failure`/`fatal`/`retry`/`reprompt`) — no advisor ordering since guardrails always run first                               | LangChain4j's guardrail API is more expressive (retry/reprompt verdicts) but has no concept of relative ordering among multiple guardrails                                               |
| Moderation                   | `ModerationModel` exists but no first-class `ChatClient` integration found in this version                                                           | `ModerationModel` + `@Moderate` annotation directly on `AiServices` methods                                                                                                                                                  | LangChain4j wires moderation *into* the service call declaratively; added here as an extra beyond parity                                                                                 |
| RAG: retrieval               | `VectorStoreDocumentRetriever` + `VectorStore` abstraction (many starters: Redis, PGVector, etc.)                                                    | `EmbeddingStoreContentRetriever` + `EmbeddingStore` abstraction (official Redis support deprecated at `1.0.0-alpha1`; current Redis support lives only in `langchain4j-community-redis`, a separate beta-versioned artifact) | Both are provider-pluggable; LangChain4j's Redis path specifically requires reaching into the community module, not the core one                                                         |
| RAG: query transformation    | `CompressionQueryTransformer`, `RewriteQueryTransformer`, `TranslationQueryTransformer`, `MultiQueryExpander` (4 distinct classes)                   | `CompressingQueryTransformer`, `ExpandingQueryTransformer` only — **no rewrite or translation transformer exists**                                                                                                           | This repo hand-writes prompt-based replacements for REWRITE/TRANSLATE (see playground table above)                                                                                       |
| RAG: orchestration           | `RetrievalAugmentationAdvisor` (transformer **list** + separate query expander + retriever + joiner + augmenter)                                     | `DefaultRetrievalAugmentor` (single `queryTransformer` slot + retriever + aggregator + injector)                                                                                                                             | Compress-then-expand needs a custom composing `QueryTransformer` in LangChain4j since there's no multi-transformer slot                                                                  |
| RAG: answer evaluation       | `FactCheckingEvaluator`, `RelevancyEvaluator` (`EvaluationRequest`/`EvaluationResponse`)                                                             | **None found** — no evaluator class anywhere in the inspected jars                                                                                                                                                           | This repo replaces it with a hand-written `FaithfulnessJudge` (`AiServices` LLM-as-judge returning a structured verdict)                                                                 |
| Tool / function calling      | `@Tool` (Spring AI), registered per `ChatClient.prompt().tools(...)` call                                                                            | `@Tool` + `@P` (`dev.langchain4j.agent.tool`), registered via `AiServices.builder().tools(...)` at build time                                                                                                                | Comparable capability; LangChain4j's `@P` gives per-parameter descriptions/required flags Spring AI's plain `@Tool` doesn't                                                              |
| Structured output            | `.call().entity(SomeRecord.class)` on `ChatClient`                                                                                                   | `AiServices` interface methods returning a record/enum/POJO directly — JSON schema auto-derived via reflection (`JsonSchemas`), `@Description` for hints                                                                     | Functionally equivalent; LangChain4j's is declarative on the interface rather than imperative on a call chain                                                                            |
| Multimodal input (image/PDF) | `.media(MimeType, Resource)` on `ChatClient`'s user spec                                                                                             | `ImageContent`/`PdfFileContent`/`TextContent` composed into a `UserMessage`                                                                                                                                                  | LangChain4j has a *dedicated* `PdfFileContent` type — confirmed to exist, not just image support                                                                                         |
| Observability hooks          | `SimpleLoggerAdvisor` (advisor-based)                                                                                                                | `ChatModelListener` (`onRequest`/`onResponse`/`onError`, attached to the model builder, not the service)                                                                                                                     | Comparable; attachment point differs (model-level vs advisor-level)                                                                                                                      |
| Embeddings                   | `EmbeddingModel` abstraction, many provider starters                                                                                                 | `EmbeddingModel` abstraction, many provider integrations (core + community)                                                                                                                                                  | Comparable                                                                                                                                                                               |
| Image generation             | `ImageModel` abstraction; official **Stability AI** starter (`spring-ai-starter-model-stability-ai`)                                                 | `ImageModel` abstraction; **OpenAI Dall-E only** — no Stability AI integration anywhere, including community modules                                                                                                         | Confirmed by searching the full `dev.langchain4j` Maven Central artifact list and the `langchain4j-community` repo; this repo switched `llm-image`'s local backend to Dall-E as a result |
| Audio transcription (STT)    | `OpenAiAudioTranscriptionModel`                                                                                                                      | `AudioTranscriptionModel` + `OpenAiAudioTranscriptionModel`                                                                                                                                                                  | Comparable                                                                                                                                                                               |
| Audio synthesis (TTS)        | `OpenAiAudioSpeechModel`                                                                                                                             | **None** — zero speech-synthesis classes in any LangChain4j artifact                                                                                                                                                         | This repo calls the official OpenAI Java SDK directly for TTS, bypassing both frameworks                                                                                                 |
| Document loading/splitting   | `DocumentReader` (PDF/Markdown/Tika starters)                                                                                                        | `DocumentParser` (PDFBox, Tika, Apache POI, etc. — separate artifacts) + `DocumentSplitter`/`DocumentSplitters` + `EmbeddingStoreIngestor`                                                                                   | Comparable shape; this repo only pulls in the PDFBox parser since both source documents are PDFs                                                                                         |
| Spring Boot integration      | Native — auto-configuration, `application.yml` properties, starters for every provider                                                               | None used here — every model/client is a plain `@Bean` method; a `langchain4j-spring-boot-starter` family exists but wasn't adopted (see code for why)                                                                       | Spring AI's biggest practical advantage for Spring users is auto-configuration; LangChain4j requires explicit bean wiring                                                                |

**Three confirmed gaps, and how this repo works around each:**

1. **Text-to-speech** — no LangChain4j abstraction exists at all. `llm-audio`'s `AudioService`
   calls the official `com.openai:openai-java` SDK directly for `audio().speech().create(...)`.
2. **Stability AI image generation** — zero integration, even in community modules.
   `llm-image`'s `LocalImageBackend` switched to OpenAI Dall-E via LangChain4j's `ImageModel`.
3. **RAG answer/fact-check evaluation** — no evaluator class anywhere. `AnswerEvaluator` now
   delegates to a hand-written `FaithfulnessJudge` (`AiServices` LLM-as-judge pattern).

---

### PostgreSQL 18

**What it is.**

- PostgreSQL is an open-source relational database; version 18 stores data in a versioned sub-directory inside the data
  volume, which is why the Docker volume mounts the parent path `/var/lib/postgresql`

**How it's used here.** A single Postgres instance (from the root `docker-compose.yml`) hosts one
database per module, each with its own Flyway history table so migrations never collide:

| Database          | Module           | Tables                                                                                                                                                                                                                                                    |
|-------------------|------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `spring_ai`       | `llm-chat-agent` | `chat_memory` (LangChain4j `ChatMemoryStore` rows, V6), `ingested_documents` (document-ingestion tracking, V7), `contacts` (weather/contacts tool seed data), `text2sql_customers / products / orders / order_items` (demo e-commerce schema), `api_keys` |
| `spring_ai_audio` | `llm-audio`      | `api_keys`                                                                                                                                                                                                                                                |
| `spring_ai_image` | `llm-image`      | `api_keys`                                                                                                                                                                                                                                                |

The `spring_ai_audio` and `spring_ai_image` databases are created on first container start by
`observability/init-db/01-create-module-databases.sql` (mounted into
`/docker-entrypoint-initdb.d`) — drop the `postgres_data` volume to re-run it against a fresh
instance. Each module's `api_keys` table is independent, so a key minted for one module doesn't
authenticate against another. Database/table names kept their original `spring_ai*` naming from
the source project — renaming infrastructure identifiers was out of scope for this migration.

- `JdbcTemplate` (no ORM) is used for all custom SQL: key lookups, contacts queries, text-to-SQL execution, chat-memory
  persistence, document-ingestion tracking, and schema introspection at runtime
- The `pgcrypto` extension is enabled in migration V5 to hash the development seed key inline

---

### Flyway

**What it is.**

- Flyway is a database-migration tool that tracks which SQL scripts have been applied via a version-history table and
  runs any pending ones at application startup
- Prevents schema drift between environments by making migrations automatic and auditable

**How it's used here.**

- Seven versioned scripts (`V1` through `V7`) under `src/main/resources/db/migration` set up all tables and seed data —
  `V6` and `V7` are new, backing `JdbcChatMemoryStore` and `DocumentIngestionRunner` respectively (LangChain4j ships
  neither schema itself)
- A separate history table (`flyway_schema_history_chat`) is used — distinct from the gateway and RAG services — so all
  three sibling services can share the same Postgres instance without migration-history conflicts
- `baseline-on-migrate: true` allows Flyway to adopt an already-initialised schema on first run without failing
- Spring Boot 4 requires the `spring-boot-flyway` module explicitly on the classpath — `flyway-core` alone no longer
  triggers `FlywayAutoConfiguration`

---

### Redis

**What it is.**

- Redis is an in-memory data structure store used here in two distinct roles: as a persistent key-value / list store
  with AOF durability, and as a vector database via the RediSearch module

**How it's used here.**

- **Embedding store**: `langchain4j-community-redis`'s `RedisEmbeddingStore` is built directly in
  `RagConfig` against a `JedisPooled` client (host/port/db/auth read from `spring.data.redis.*`
  properties via plain `@Value` injection — there's no Spring Boot starter wiring this
  automatically, unlike Spring AI's `spring-ai-starter-vector-store-redis`) and creates its vector
  index (`FT.CREATE`) on construction
- **Connection**: the `JedisPooled` bean in `RagConfig` replaces the old `RedisConfig`'s
  `JedisConnectionFactory` (a Spring Data Redis type, no longer on the classpath) — same
  host/port/database/username/password properties, just consumed directly by LangChain4j's Redis
  module instead of through Spring Data Redis
- **Docker**: Redis is started with `appendonly yes` (AOF persistence), a 512 MB memory cap, and an `allkeys-lru`
  eviction policy so vector data survives restarts and old entries are evicted gracefully under memory pressure
- **RedisInsight**: A companion `redis/redisinsight` container (port 5540) provides a browser UI for inspecting
  embedding-store contents during development

---

### Spring Security

**What it is.**

- Spring Security is the standard authentication and authorisation framework for Spring applications
- Works through a chain of servlet filters that intercept every request before it reaches a controller

**How it's used here.** The project implements a custom, database-backed API-key scheme instead of session or JWT:

1. **`ApiKeyAuthFilter`** reads the `X-API-Key` header, calls `ApiKeyService.isValid`, and if valid sets a
   `PreAuthenticatedAuthenticationToken` in the `SecurityContextHolder`; also calls `touchLastUsed` to stamp the row
2. **`ApiKeyService`** hashes the raw key with SHA-256 (using `MessageDigest`) and checks the `api_keys` table via
   `JdbcTemplate` — raw keys are never stored
3. **`RateLimitFilter`** applies a token-bucket rate limiter (120 requests/minute burst) per API key (or client IP when
   no key is present); the bucket is an in-memory `ConcurrentHashMap` of hand-rolled `Bucket` objects — no Resilience4j
   or Bucket4j dependency; returns `429` with a JSON `ApiError` on exhaustion
4. **`SecurityConfig`** declares which paths are open (actuator, static HTML, `/error`) and which require
   authentication; also configures CORS (`CORS_ALLOWED_ORIGINS`) and security headers
5. **`RestAuthenticationEntryPoint`** returns a structured JSON `{"status":401,...}` error rather than the default HTML
   challenge page

- Auth can be fully disabled for local development via `API_AUTH_ENABLED=false`
- Not used at all in `llm-playground` — that module has no security dependency, no `api_keys` table, and every endpoint
  is open

---

### Micrometer + Prometheus

**What it is.**

- Micrometer is the metrics-instrumentation facade for the JVM — analogous to SLF4J for logging
- Prometheus is a time-series metrics database that scrapes HTTP endpoints on a configurable interval

**How it's used here.**

- `spring-boot-starter-actuator` exposes `/ai/actuator/prometheus` as a Prometheus-format text scrape target
- `micrometer-registry-prometheus` registers the Prometheus `MeterRegistry` with Spring Boot
- `ObservabilityConfig` registers two AOP aspects: `TimedAspect` (activates `@Timed` on service methods to create
  histograms) and `ObservedAspect` (activates `@Observed` to open tracing spans)
- `micrometer-jvm-extras` adds process-level native memory and thread-count metrics (`process_memory_*`,
  `process_threads`)
- HTTP SLO buckets are configured at 50ms, 100ms, 200ms, 300ms, 500ms, 1s, 2s, and 5s for the `http.server.requests`
  histogram, enabling percentile and SLO dashboards in Grafana
- Prometheus scrapes the app every 10 seconds via `observability/prometheus.yml`

---

### Grafana

**What it is.**

- Grafana is a dashboarding and visualisation platform that can query Prometheus (metrics), Tempo (traces), and Loki (
  logs) from a single UI
- Supports alerting, annotations, and templated dashboards shareable as JSON

**How it's used here.**

- A `grafana/grafana` container (port 3000, admin/admin) is provisioned at startup via files under
  `observability/grafana/provisioning/`
- Prometheus, Tempo, and Loki are auto-configured as data sources — no manual datasource setup required
- The pre-built **LLM Chat** dashboard is loaded automatically so the full observability picture is available
  immediately after `docker compose up`
- Grafana depends on all three backend services in the Docker Compose definition, so startup ordering is correct

---

### Grafana Tempo

**What it is.**

- Tempo is a distributed tracing backend that stores and retrieves OpenTelemetry (OTLP) traces
- Designed to be cost-efficient by storing traces on local or object storage without a separate index

**How it's used here.**

- The app exports traces over OTLP HTTP to `http://localhost:4318/v1/traces` at 100% sampling, configurable via
  `OTEL_EXPORTER_OTLP_ENDPOINT`
- `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp` wire Micrometer's observation API into OpenTelemetry'
  s SDK, which ships spans to Tempo
- `traceId` and `spanId` from the MDC are included in every log line (both console and JSON) so log lines in Loki can be
  correlated directly to traces in Tempo
- Tempo is configured in `observability/tempo.yml` with OTLP gRPC (port 4317) and HTTP (port 4318) receivers and 24-hour
  local trace retention

---

### Grafana Loki

**What it is.**

- Loki is a log aggregation system designed to work like Prometheus: it indexes only metadata labels rather than full
  log content, making it highly storage-efficient compared to Elasticsearch

**How it's used here.**

- `logback-spring.xml` configures two appenders: `ASYNC_CONSOLE` (colour-formatted, buffered through a 512-message
  queue) and `JSON_FILE` (Logstash JSON format, rolling daily, gzip-compressed, 30-day retention, max 100 MB per file)
- The `logstash-logback-encoder` library serialises each log event as a JSON object embedding `traceId` and `spanId`
  fields so that Loki log entries can be linked directly to Tempo traces in Grafana
- The JSON files under `logs/` are the source Loki (or a log-shipper sidecar) would tail and push into the aggregation
  backend

---

### Spring WebFlux / Project Reactor (`Flux`)

**What it is.**

- Project Reactor is a reactive-streams library for the JVM; `Flux<T>` represents an asynchronous sequence of 0–N items
- Spring WebFlux uses it for non-blocking HTTP handling, allowing a single thread to serve many concurrent in-flight
  requests

**How it's used here.**

- `spring-boot-starter-webflux` is now a **direct** dependency in `llm-chat-agent`/`llm-audio`/`llm-image`
  (it used to arrive transitively via Spring AI's RAG/vector-store starters) — needed purely for
  `WebClient`, `Flux`, and the reactive SSE codec (`ServerSentEvent`); the servlet stack still wins
  since `spring-boot-starter-web` is also present, so this doesn't turn the app into a WebFlux server
- The streaming chat endpoint (`/chat/stream`) returns a `Flux<ServerSentEvent<String>>` that `ChatBackend`
  implementations produce
- In `LocalChatBackend`, a
  `Flux.create(sink -> chatAssistant.chatStream(...).onPartialResponse(...).onRetrieved(...).onCompleteResponse(...).onError(...).start())`
  bridges LangChain4j's callback-based `TokenStream` into a reactive `Flux`
- In `GatewayChatBackend`, `WebClient` (Spring's reactive HTTP client) connects to the gateway's SSE stream and returns
  the same `Flux<ServerSentEvent<String>>`
- The controller maps this `Flux` to `MediaType.TEXT_EVENT_STREAM_VALUE` so browsers receive a true Server-Sent Events
  response
- `WebClient` is also used for all non-streaming gateway calls, where `.block()` converts the reactive result to a
  blocking call within a configured timeout

---

### LangChain4j Document Loading (Apache PDFBox)

**What it is.**

- `langchain4j-document-parser-apache-pdfbox` wraps Apache PDFBox into LangChain4j's
  `DocumentParser` interface — `parse(InputStream): Document`. LangChain4j ships separate parser
  artifacts per format (PDFBox, Apache Tika, Apache POI, …) rather than one bundled reader, and a
  separate `DocumentSplitters`/`DocumentSplitter` API for chunking, composed together by
  `EmbeddingStoreIngestor`

**How it's used here.**

- `DocumentIngestionRunner` parses the two corporate PDFs (`AtlasCorp-TravelPolicy.pdf`,
  `AtlasCorp_Events_Holidays.pdf`) with `ApachePdfBoxDocumentParser`, attaches `fileName`/`source`/
  `identity` metadata, splits with `DocumentSplitters.recursive(500, 100)`, and ingests via
  `EmbeddingStoreIngestor` into the Redis embedding store — gated by a Postgres
  `ingested_documents` tracking table so restarts don't re-embed (and duplicate) the same content
- Only the PDFBox parser is pulled in (`langchain4j-document-parser-apache-tika` and a Markdown
  equivalent were dropped) because both source documents are PDFs and neither Tika nor Markdown
  parsing was actually exercised by any class in the original Spring AI version either
- `ApachePdfBoxDocumentParser` extracts whole-document text, not per-page — there's no
  page-aware reading mode here the way Spring AI's PDF reader had, so `Citation.page` is always
  `null` in this implementation (kept nullable rather than removed, see `Citation`'s javadoc)

---

### JDBC Chat Memory (LangChain4j `ChatMemoryStore`)

**What it is.**

- LangChain4j's `dev.langchain4j.store.memory.chat.ChatMemoryStore` is a three-method interface
  (`getMessages`/`updateMessages`/`deleteMessages`) for pluggable conversation persistence — the
  default implementation is in-memory only; **no JDBC, Redis, or any other persistent
  implementation ships in LangChain4j core** (a `RedisChatMemoryStore` does exist in
  `langchain4j-community-redis`, but this project keeps chat memory on Postgres, separate from the
  Redis-backed embedding store, matching the original architecture's separation of concerns)

**How it's used here.**

- `JdbcChatMemoryStore` implements the three methods directly against a `chat_memory` table
  (`db/migration/V6__create_chat_memory.sql`): one row per conversation, the full message list
  serialized to JSON via LangChain4j's own `ChatMessageSerializer`/`ChatMessageDeserializer`
- `AIConfig.chatMemoryProvider()` wraps it in a `MessageWindowChatMemory` capped at 50 messages,
  built fresh per `memoryId` (LangChain4j's per-conversation memory is request-scoped by design,
  unlike Spring AI's single shared `ChatMemory` bean keyed by a parameter)
- This gives the service stateful multi-turn memory across HTTP requests with no in-process state — memory survives
  restarts automatically, the same guarantee the Spring AI version had

---

### Lombok

**What it is.**

- Lombok is a Java annotation processor that generates boilerplate code (constructors, getters, `toString`,
  `equals/hashCode`, builders, loggers) at compile time
- Reduces source verbosity without adding runtime overhead — all generated code is plain Java bytecode

**How it's used here.**

- `@RequiredArgsConstructor` on service and component classes generates the constructor used by Spring's constructor
  injection — no `@Autowired` annotations needed
- `@Slf4j` injects a `log` field backed by SLF4J/Logback into every annotated class
- `@AllArgsConstructor` appears on `AudioService` where all fields need explicit initialisation
- **Caution discovered during this migration**: `@Transactional` on a bean LangChain4j's
  `AiServices` tool scanner inspects forces Spring to wrap it in a CGLIB subclass proxy, and the
  scanner reflects on the proxy's own declared methods — which don't carry the original class's
  `@Tool` annotations — so a `@Transactional`-proxied tool bean fails to register at all. `ContactsTool`
  dropped its `@Transactional(readOnly = true)` for exactly this reason (a single `JdbcTemplate`
  query doesn't need an explicit transaction boundary anyway)
- Lombok is excluded from the final fat-jar via `spring-boot-maven-plugin`'s exclude list because it is a
  compile-time-only tool with no runtime dependency

---

### Testcontainers

**What it is.**

- Testcontainers is a Java library that starts real Docker containers during JUnit tests and cleans them up afterwards
- `@ServiceConnection` (a Spring Boot 3.1+ annotation) wires the container's dynamic port and credentials directly into
  the application context with no manual property overriding

**How it's used here.**

- `TestcontainersConfiguration` defines a `@ServiceConnection PostgreSQLContainer<>("postgres:18")` bean
- When `LLMApplicationTests` loads the context, Spring Boot auto-overrides the datasource URL with the container's
  random mapped port
- There's **no Redis container** here anymore: `@ServiceConnection`'s generic-container factory
  targets Spring Data Redis's `RedisConnectionDetails`, which isn't on the classpath (LangChain4j's
  Redis support doesn't go through Spring Data Redis at all). `LLMApplicationTests` instead
  `@MockitoBean`-replaces the `EmbeddingStore` bean directly, sidestepping both that wiring gap and
  the fact that a real `RedisEmbeddingStore` would need the RediSearch module (`redis/redis-stack-server`)
  on construction, not the plain `redis:7-alpine` image used elsewhere in this repo
- Flyway migrations and all JDBC queries in tests run against a real Postgres 18 instance
- The suite is fully self-contained and runs in CI with only Docker available — no locally provisioned database required

---

### JaCoCo

**What it is.**

- JaCoCo (Java Code Coverage) is a bytecode-instrumentation tool that measures which lines, branches, and instructions
  are exercised by the test suite
- Produces HTML, XML, and CSV reports consumed by CI pipelines and IDEs

**How it's used here.**

- The `jacoco-maven-plugin` is configured with two executions: `prepare-agent` (attaches the JaCoCo agent before tests
  run) and `report` (bound to the `verify` phase, producing HTML/XML reports under `target/site/jacoco`)
- Running `./mvnw verify` compiles, runs all tests, and generates the full coverage report in one step

---

### Git Commit ID Maven Plugin

**What it is.**

- The `git-commit-id-maven-plugin` reads Git metadata (branch, commit hash, timestamp, dirty flag) at build time and
  writes it to `git.properties` on the classpath
- Makes every built artifact self-describing — the running application knows exactly which commit it was built from

**How it's used here.**

- The plugin runs during the `initialize` phase and generates `target/classes/git.properties`
- Spring Boot Actuator's `/ai/actuator/info` endpoint automatically exposes these properties (enabled by
  `management.info.git.mode: full`)
- Every running instance reports its exact commit, branch, and dirty-flag state — invaluable for verifying deployments
  and correlating incidents to code changes

---

### Docker Compose

**What it is.**

- Docker Compose is a tool for defining and running multi-container applications from a single YAML file
- Health checks between services enable controlled startup ordering without manual delays

**How it's used here.**

- `docker-compose.yml` defines seven services: `postgres` (port 5432), `redis` (port 6379), `redisinsight` (port 5540),
  `tempo` (ports 3200/4317/4318), `loki` (port 3100), `prometheus` (port 9090), and `grafana` (port 3000)
- All services have health checks so `docker compose up -d` waits for each service to be genuinely ready before marking
  it as started
- Named volumes (`postgres_data`, `redis_data`, etc.) provide persistence of data across container restarts
- The application itself does **not** run in Compose — it starts separately on the host via `./mvnw spring-boot:run` and
  reaches containers on `localhost`
- `spring.docker.compose.enabled=false` prevents Spring Boot's built-in Compose integration from re-managing the
  already-running containers
