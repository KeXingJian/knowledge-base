# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
# Build JAR (skip tests)
./mvnw clean package -DskipTests

# Run all tests (requires live services)
./mvnw test

# Run a single test class
./mvnw test -Dtest=ClassName

# Run a single test method
./mvnw test -Dtest=ClassName#methodName

# Start all services (recommended for development)
docker-compose up -d --build

# Stop all services
docker-compose down

# View logs for a specific service
docker-compose logs -f web
docker-compose logs -f ollama

# Run locally without Docker (requires .env.dev vars exported)
./mvnw spring-boot:run
```

After startup, access the app via: `http://localhost` (Nginx proxy) or `http://localhost:8080` (Spring Boot direct).

## Architecture Overview

This is a **RAG (Retrieval-Augmented Generation)** knowledge base — users upload documents, which are chunked and embedded, then a chat interface answers questions using hybrid vector + full-text retrieval.

### Document Ingestion Flow
```
POST /api/documents/batch-upload
  → DocumentController
  → DocumentServiceOptimized
      → SHA-256 dedup (in-memory + DB)
      → MinioService: store raw file
      → DocumentRepository: persist metadata
      → Split into chunks (default 300 chars)
      → Virtual threads (Semaphore-limited to 20): EmbeddingService.embed() via Ollama
      → VectorStoreService: batch insert DocumentChunk with 768-dim vector
      → Redis: track async progress (poll /api/documents/batch-progress/{taskId})
```

### Chat / Query Flow
```
POST /api/conversations/chat
  → ConversationService
      → Load session history from DB + Redis cache
      → SemanticCacheService: check HNSW index + synonym normalization for cached response
      → EmbeddingService.embed(question) → Ollama nomic-embed-text
      → HybridRetriever: pgvector cosine (60%) + tsvector/GIN full-text (40%)
      → RAGService: build prompt with history + retrieved chunks → Ollama qwen2:7b
      → Persist user + assistant messages
      → CacheInvalidationService: evict on document changes
```

### Concurrency Model
`ThreadPoolConfigOptimized` uses **Java 21 Virtual Threads** via `Executors.newVirtualThreadPerTaskExecutor()` with a `Semaphore` to cap Ollama embedding concurrency. The batch document processing pipeline is the primary use of this.

## Key Services

| Service | Role |
|---|---|
| `DocumentServiceOptimized` | Ingestion pipeline, dedup, async progress tracking |
| `HybridRetriever` | Merges vector + full-text search results with weighted scoring |
| `EmbeddingService` | Calls Ollama `nomic-embed-text` for 768-dim vectors |
| `CachedEmbeddingService` | Caffeine + Redis cached wrapper for embeddings |
| `RAGService` | Builds context-augmented prompts, calls Ollama `qwen2:7b` |
| `VectorStoreService` | pgvector batch save/query |
| `MinioService` | Raw file upload/download/URL generation |
| `ConversationService` | Session management, message history, chat orchestration |
| `SemanticCacheService` | HNSW-based semantic caching with synonym normalization |
| `HnswVectorIndexService` | In-memory HNSW index for fast similarity search |
| `SynonymNormalizer` | Normalizes queries for better cache hits |
| `CacheInvalidationService` | Evicts cache entries on document changes |

## Configuration

- `application.yaml` — datasource, JPA (ddl-auto=none, Flyway manages schema), Redis, MinIO, LangChain4j model settings, document processing, retrieval weights
- `conversation-config.yml` — max history messages (10), max conversation days (30), auto-title generation
- `.env.dev` — local development environment variables (all services on localhost)
- `.env` — production Docker Compose environment variables

Key tunable properties:
- `document.processing.chunkSize` (default 300), `batchSize` (10), `maxConcurrentChunks` (20)
- `retrieval.search.topK` (3), `vectorWeight` (0.6), `textWeight` (0.4)
- `cache.semantic.enabled` (true), `similarityThreshold` (0.92), `semanticTtl` (2h)
- `cache.synonym.enabled` (true) — supports custom mappings like "咋用:怎么用"

## Database Schema

Managed by **Flyway** migrations in `src/main/resources/db/migration/` (V1–V5):
- `document` — file metadata, MinIO path, SHA-256 hash for dedup, processing status
- `document_chunk` — text chunks + `vector(768)` column + `tsvector` (auto-updated via DB trigger)
- `conversation` — chat sessions by `session_id`
- `message` — user/assistant messages with role, RAG context, retrieved chunk references

pgvector indexes: IVFFlat + HNSW on the `vector` column; GIN index on the `tsvector` column.

## Infrastructure (Docker Compose)

Six services on `rag-network`:
- `nginx` — reverse proxy serving static `ui/` files, proxying `/api/` to `web:8080`
- `web` — Spring Boot app
- `postgres` — `pgvector/pgvector:pg16`
- `redis` — Redis 7
- `ollama` — self-hosted LLM runtime (pull `qwen2:7b` and `nomic-embed-text` manually after first start)
- `minio` — object storage, console at `http://localhost:9001`

## Tech Stack

- **Backend**: Spring Boot 4.0.2, Java 21, Spring Data JPA, Flyway, LangChain4j 0.36.2, Lombok
- **AI**: Ollama (`qwen2:7b` for chat, `nomic-embed-text` for embeddings)
- **Storage**: PostgreSQL 16 + pgvector, Redis 7, MinIO
- **Caching**: Caffeine (local), Redis (distributed), HNSW (in-memory semantic cache)
- **Frontend**: React/Vite in separate repo (https://github.com/KeXingJian/cosmic-chat), built files in `ui/`

## Project Structure Notes

- Frontend is a separate repository; static built files go in `ui/` directory served by Nginx
- Tests require live services (PostgreSQL, Redis, Ollama) to be running
- Document processing uses virtual threads with semaphore limiting to control Ollama concurrency
- Semantic cache uses HNSW index for O(1) approximate nearest neighbor search
