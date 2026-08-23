# DocQuery — AI-Powered Document Q&A API

Upload a PDF, ask it questions in plain English, get back an answer **and the exact
passage it came from** — so you can verify it instead of blindly trusting it.

Built with **Spring Boot 3, Spring Security (JWT), MySQL, Redis, and a
Retrieval-Augmented Generation (RAG) pipeline** on top of the OpenAI API.

```
┌─────────────┐      1. upload PDF       ┌──────────────────┐
│   Browser   │ ───────────────────────► │   Spring Boot     │
│  (index.html)│                          │   REST API        │
└─────────────┘                          └────────┬──────────┘
                                                    │ extract text (PDFBox)
                                                    │ chunk + embed (OpenAI)
                                                    ▼
                                          ┌───────────────────┐
                                          │  MySQL  (chunks +  │
                                          │  embeddings stored)│
                                          └───────────────────┘

┌─────────────┐   2. ask a question      ┌──────────────────┐
│   Browser   │ ───────────────────────► │  Spring Boot API  │
└─────────────┘                          └────────┬──────────┘
                                                    │ check Redis cache first
                                                    │ else: embed question →
                                                    │ cosine-similarity search →
                                                    │ top chunks → OpenAI chat →
                                                    │ cache the answer
                                                    ▼
                                          ┌───────────────────┐
                                          │  Redis (cache) +   │
                                          │  MySQL (Q&A log)   │
                                          └───────────────────┘
```

---

## What's inside

- **JWT authentication** — Spring Security, BCrypt-hashed passwords, stateless sessions
- **PDF ingestion** — Apache PDFBox extracts text, a custom chunker splits it into overlapping ~350-word chunks
- **RAG pipeline** — each chunk is embedded via OpenAI's embeddings API; questions are embedded and matched against chunks with cosine similarity; only the top matches are sent to the LLM as context
- **Grounded answers** — the LLM is instructed to answer only from the retrieved excerpts, and the API refuses to answer if nothing relevant was found (no hallucinated guesses)
- **Redis caching** — repeated questions on the same document skip the LLM call entirely
- **Clean REST API** — documented below, easy to test with curl or Postman
- **Polished single-page frontend** — no build step, plain HTML/CSS/JS, served directly by Spring Boot
- **Dockerized** — one command spins up the app, MySQL, and Redis together

---

## Project structure

```
docquery/
├── backend/
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/
│       ├── java/com/docquery/
│       │   ├── DocQueryApplication.java
│       │   ├── config/          # Security + Redis config
│       │   ├── security/        # JWT util + filter
│       │   ├── model/           # JPA entities
│       │   ├── repository/      # Spring Data repositories
│       │   ├── dto/             # Request/response objects
│       │   ├── service/         # Business logic (the RAG pipeline lives here)
│       │   ├── controller/      # REST endpoints
│       │   └── exception/       # Global error handling
│       └── resources/
│           ├── application.properties
│           └── static/          # Frontend (index.html, css/, js/)
├── docker-compose.yml
├── .env.example
└── README.md
```

---

## 1. Prerequisites

| Tool | Why | Check |
|---|---|---|
| Java 17+ | Run Spring Boot | `java -version` |
| Maven 3.9+ | Build the backend | `mvn -version` |
| Docker + Docker Compose | Easiest way to run everything | `docker --version` |
| An OpenAI API key | Powers embeddings + answers | see below |

**Getting an OpenAI API key** (takes 2 minutes):
1. Go to https://platform.openai.com/api-keys
2. Sign up / log in, click **Create new secret key**
3. Copy it — you won't be able to see it again
4. Add a small amount of credit at https://platform.openai.com/settings/organization/billing (this project is cheap to run: embeddings and `gpt-4o-mini` cost fractions of a cent per question)

> Don't want to use OpenAI? Any OpenAI-compatible provider works — just change
> `OPENAI_BASE_URL` in your `.env`. Groq, Together AI, and OpenRouter all offer
> OpenAI-compatible endpoints, and several have free tiers.

---

## 2. Run it — the fast way (Docker)

```bash
# 1. Clone / unzip the project, then from the project root:
cd docquery

# 2. Create your .env file
cp .env.example .env

# 3. Edit .env and paste your real OpenAI API key
#    (open it in any editor and replace OPENAI_API_KEY=sk-your-key-here)

# 4. Build and start everything (app + MySQL + Redis)
docker compose up --build
```

Wait for the logs to show `Started DocQueryApplication`, then open:

**http://localhost:8080**

That's it — sign up, upload a PDF, start asking questions.

To stop: `Ctrl+C`, then `docker compose down` (add `-v` to also wipe the database).

---

## 3. Run it — without Docker (local Maven + local MySQL/Redis)

Use this if you want to run the app directly from your IDE (IntelliJ, VS Code) for development.

**a) Install and start MySQL and Redis locally**, or run just those two via Docker:
```bash
docker run -d --name docquery-mysql -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=docquery -p 3306:3306 mysql:8.0
docker run -d --name docquery-redis -p 6379:6379 redis:7-alpine
```

**b) Set environment variables** (or edit `backend/src/main/resources/application.properties` directly):
```bash
export DB_USER=root
export DB_PASSWORD=root
export OPENAI_API_KEY=sk-your-key-here
export JWT_SECRET=some-long-random-string-at-least-32-chars
```

**c) Run the app:**
```bash
cd backend
mvn spring-boot:run
```

Open **http://localhost:8080**.

### Opening it in an IDE
- **IntelliJ IDEA**: `File → Open` → select the `backend` folder → let Maven import → run `DocQueryApplication.java` (set the env vars above in the Run Configuration's "Environment variables" field)
- **VS Code**: install the "Extension Pack for Java" and "Spring Boot Extension Pack" → open the `backend` folder → run from `DocQueryApplication.java`

---

## 4. API reference

All `/api/**` routes except `/api/auth/**` require a header:
`Authorization: Bearer <token>`

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/auth/register` | `{ name, email, password }` → `{ token, name, email }` |
| POST | `/api/auth/login` | `{ email, password }` → `{ token, name, email }` |
| GET | `/api/documents` | List your uploaded documents |
| POST | `/api/documents/upload` | `multipart/form-data`, field `file` (PDF) |
| GET | `/api/documents/{id}` | Get one document's status |
| POST | `/api/documents/{id}/ask` | `{ question }` → `{ answer, sourceSnippet, fromCache }` |

### Try it with curl

```bash
# Register
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"name":"Farhan","email":"farhan@example.com","password":"secret123"}'

# Save the token from the response, then:
TOKEN="paste-token-here"

# Upload a document
curl -X POST http://localhost:8080/api/documents/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@/path/to/your/document.pdf"

# Ask a question (use the id returned above)
curl -X POST http://localhost:8080/api/documents/1/ask \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"question":"What is the notice period for termination?"}'
```

---

## 5. Deploying it live (Render — free tier)

Having a **live demo link** on your resume matters more than the code itself for
getting an interviewer's attention. Render's free tier works well for this.

1. Push this project to a GitHub repo
2. Go to https://render.com → **New +** → **Blueprint** (or create services manually):
   - **Web Service**: point at `backend/Dockerfile`, add env vars (`DB_HOST`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `OPENAI_API_KEY`, etc. — same names as `.env.example`)
   - **MySQL**: Render offers managed MySQL/Postgres — or use a free tier from [Aiven](https://aiven.io) or [Railway](https://railway.app)
   - **Redis**: Render has a free Redis add-on, or use [Upstash](https://upstash.com) (generous free tier, works great for this)
3. Set `DB_HOST`, `REDIS_HOST` etc. to the hostnames Render/your provider gives you
4. Deploy — Render builds the Dockerfile automatically
5. Once live, note your URL (e.g. `https://docquery.onrender.com`) for your resume

**Railway** (https://railway.app) is an equally good alternative — it can spin up
MySQL and Redis as one-click add-ons alongside your app in the same project,
which is often simpler than wiring up three separate providers.

> Free tiers on both platforms spin down when idle and take ~30s to wake up on
> the first request after inactivity — completely normal, and worth mentioning
> if an interviewer notices the first load is slow.

---

## 6. Design decisions worth knowing before your interview

These are the things interviewers actually ask about — have a one-sentence answer ready for each:

**Why chunk into ~350 words with overlap, not whole pages or single sentences?**
Whole pages dilute relevance (too much irrelevant text sent to the LLM, more
tokens/cost). Single sentences lose context. ~350 words with a 60-word overlap
keeps chunks topically coherent while making sure an answer that straddles a
chunk boundary isn't lost.

**Why in-memory cosine similarity instead of a real vector database?**
At this scale (a handful of documents, a few hundred chunks each) it's fast
and has zero extra infrastructure. It's a deliberate scope decision, not a gap
in knowledge — `VectorSearchService` is written as its own interface-like
service specifically so it could be swapped for pgvector, Pinecone, or Qdrant
without touching the rest of the app if the dataset grew.

**Why cache at the question level instead of caching embeddings?**
The LLM call is the expensive, slow part — caching the final answer means a
repeated question skips both the embedding call *and* the LLM call entirely.
Caching only embeddings would still leave the (slower, costlier) LLM call on
every request.

**What happens if the document doesn't contain the answer?**
The retrieval step checks a similarity threshold before ever calling the LLM.
If nothing scores high enough, the API returns a direct "not found in this
document" response instead of asking the LLM to answer anyway — which is how
you avoid hallucinated answers presented as fact.

**Why JWT instead of session cookies?**
Stateless — no server-side session storage needed, and it's the standard
approach for an API meant to be consumed by any client (this frontend, a
mobile app, another service).

---

## 7. Troubleshooting

| Problem | Fix |
|---|---|
| `docker compose up` fails on MySQL health check | Give it another minute — first-time MySQL init can take 20-30s; the app waits for it automatically |
| Upload succeeds but document status stays `PROCESSING` forever | Check `docker compose logs app` — almost always an invalid/missing `OPENAI_API_KEY` or no billing credit on the OpenAI account |
| `401 Unauthorized` on every request | Your token expired (default 24h) or wasn't sent — log in again |
| PDF upload fails with "No extractable text found" | The PDF is scanned/image-only with no real text layer — this project doesn't do OCR (a good "future work" line for your README/interview) |
| Port 8080 already in use | Change `SERVER_PORT` in `.env` / `application.properties`, or stop whatever else is using it |

---

## 8. Suggested resume line

```
DocQuery — AI Document Q&A API | Spring Boot, Spring Security, MySQL, Redis, RAG/LLM
Built a RAG-based document Q&A service with JWT auth, PDF chunking + embedding-based
retrieval, and Redis-cached LLM responses to cut repeat-query cost and latency;
containerized with Docker and deployed live.
```

---

## What to build next (good talking points if asked "what would you improve?")

- Swap in-memory cosine search for pgvector once document count grows
- Add OCR (Tesseract) for scanned PDFs
- Stream LLM responses token-by-token instead of waiting for the full answer
- Support multi-document Q&A ("search across all my documents")
- Rate-limit the `/ask` endpoint per user to control OpenAI cost
