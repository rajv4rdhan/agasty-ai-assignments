# RAG Backend System - Technical Documentation

## Architecture

### System Overview

The system is built using Spring Boot 4.x with Spring AI 2.x, implementing a Retrieval-Augmented Generation (RAG) pattern. The architecture follows a layered design:

**Presentation Layer**
- REST controllers (`ChatController`, `DocumentController`)
- SSE streaming endpoints for real-time responses

**Service Layer**
- `DocumentService`: Document management and deletion
- `ChatService`: Standard chat with retrieval
- `EnhancedChatService`: Advanced features (hybrid search, reranking, query rewriting)
- `StreamingChatService`: Real-time streaming responses

**Data Access Layer**
- Spring Data JPA repositories
- Native SQL queries for vector operations
- PostgreSQL full-text search integration

**Core Components**
- `DocumentIngestionService`: Async document processing
- `ChunkingService`: Text segmentation
- `EmbeddingService`: Vector generation via Gemini
- `RetrievalService`: Vector similarity search
- `HybridRetrievalService`: Combined vector + full-text search
- `RerankingService`: LLM-based relevance scoring
- `QueryRewritingService`: Follow-up question handling

### Database Schema

```sql
-- Documents table
documents (
  id BIGSERIAL PRIMARY KEY,
  tenant_id VARCHAR(255) NOT NULL,
  title VARCHAR(500),
  file_name VARCHAR(500),
  file_type VARCHAR(50),
  file_size_bytes BIGINT,
  content_hash VARCHAR(64) UNIQUE,
  category VARCHAR(100),
  status VARCHAR(20),
  error_message TEXT,
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  INDEX idx_tenant_category (tenant_id, category)
)

-- Document chunks with embeddings
document_chunks (
  id BIGSERIAL PRIMARY KEY,
  tenant_id VARCHAR(255) NOT NULL,
  document_id BIGINT REFERENCES documents(id),
  chunk_index INTEGER,
  content TEXT,
  page_number INTEGER,
  embedding VECTOR(768),  -- pgvector extension
  created_at TIMESTAMP,
  INDEX idx_tenant (tenant_id),
  INDEX idx_document (document_id)
)

-- Conversations
conversations (
  id BIGSERIAL PRIMARY KEY,
  tenant_id VARCHAR(255) NOT NULL,
  title VARCHAR(500),
  created_at TIMESTAMP,
  updated_at TIMESTAMP
)

-- Messages
messages (
  id BIGSERIAL PRIMARY KEY,
  conversation_id BIGINT REFERENCES conversations(id),
  role VARCHAR(50),
  content TEXT,
  created_at TIMESTAMP
)

-- Citations
message_citations (
  id BIGSERIAL PRIMARY KEY,
  message_id BIGINT REFERENCES messages(id),
  document_id BIGINT REFERENCES documents(id),
  document_title VARCHAR(500),
  page_number INTEGER,
  similarity_score DOUBLE PRECISION,
  snippet TEXT,
  created_at TIMESTAMP
)
```

### Tenant Isolation Design

Tenant isolation is enforced at multiple levels:

**1. Context-based Isolation**
- `TenantContext` ThreadLocal stores tenant ID per request
- `TenantInterceptor` extracts X-Tenant-Id header and sets context
- All queries automatically filter by tenant_id

**2. Database-level Isolation**
- Every table includes tenant_id column
- All queries use INNER JOIN with tenant_id checks
- Vector search queries include WHERE tenant_id = :tenantId

**3. Service-level Validation**
- Services validate tenant ownership before operations
- DELETE operations verify tenant_id before cascading
- ResourceNotFoundException thrown for cross-tenant access attempts

**4. Integration Testing**
- Dedicated TenantIsolationIntegrationTest
- Verifies retrieval returns only tenant's data
- Tests with multiple tenants simultaneously

---

## How to Run

### Quick Start with Docker (Recommended)

The fastest way to get started:

```bash
# 1. Clone and configure
cp .env.example .env
# Edit .env and add your GEMINI_API_KEY

# 2. Start everything with Docker
docker compose up -d

# 3. Access the application
# API: http://localhost:8080
# Health: http://localhost:8080/actuator/health
```

### Prerequisites

- Java 21
- PostgreSQL 16+ with pgvector extension
- Docker (for Testcontainers)
- Google Cloud Platform account (for Gemini API)

### Database Setup

```bash
# Start PostgreSQL with pgvector
docker-compose up -d

# The application uses Flyway for automatic schema migration
# Tables will be created on first startup
```

### Environment Configuration

Create `.env` file:

```properties
DB_URL=jdbc:postgresql://localhost:5432/ragdb
DB_USER=raguser
DB_PASSWORD=ragpass

GEMINI_PROJECT_ID=your-gcp-project-id
GEMINI_LOCATION=us-central1
GEMINI_MODEL=gemini-1.5-flash
GEMINI_EMBEDDING_MODEL=text-embedding-004
OCR_API_KEY=key
```

### Build and Run

#### Option 1: Docker (Recommended for Production)

```bash
# Start all services (PostgreSQL + Java app)
docker compose up -d

# View logs
docker compose logs -f rag-app

# Stop services
docker compose down
```

#### Option 2: Docker for DB, Local for App (Recommended for Development)

```bash
# Start only PostgreSQL
docker compose -f docker-compose.dev.yml up -d

# Run app locally with 2GB heap
./run.bat  # Windows
./run.sh   # Linux/Mac

# Or from IDE with VM options: -Xms512m -Xmx2048m
```


#### Option 3: Manual Setup (Advanced)

```bash
# Build the application
./mvnw clean package -DskipTests

# Run with proper heap size
set MAVEN_OPTS=-Xms512m -Xmx2048m
./mvnw spring-boot:run

# Or run the JAR
java -Xms512m -Xmx2048m -jar target/rag-0.0.1-SNAPSHOT.jar
```

The application will start on http://localhost:8080

### Run Tests

```bash
# Run all tests
./mvnw test

# Run integration tests only
./mvnw test -Dtest="*IntegrationTest"

# Run evaluation harness
./mvnw test -Dtest="EvaluationHarnessIntegrationTest"
```

---

## API Examples

### Quick Start with Postman

A complete Postman collection is provided for easy API testing:

**Files:**
- `RAG-API.postman_collection.json` - Complete API collection

**Import:**
1. Open Postman
2. Import `RAG-API.postman_collection.json`




## Chunking Strategy

### Implementation

Text is segmented using a sliding window approach:

**Parameters:**
- Chunk Size: 500 characters
- Overlap: 50 characters (10%)

**Process:**
1. Extract text from document (PDF, DOCX, TXT, Markdown)
2. Split into chunks of 500 characters
3. Maintain 50-character overlap between chunks
4. Preserve page numbers for citations

**Rationale:**

**Chunk Size (500 characters):**
- Balances context vs. granularity
- Approximately 100-125 tokens (within embedding model limits)
- Small enough for precise retrieval
- Large enough to maintain semantic coherence
- Typical paragraph length in school documents

**Overlap (50 characters):**
- Prevents information loss at boundaries
- Ensures sentences aren't split awkwardly
- Improves retrieval for queries matching chunk boundaries
- 10% overlap is industry standard

**Trade-offs:**
- Smaller chunks: Better precision, worse context
- Larger chunks: Better context, worse precision
- More overlap: Better boundary handling, more storage
- Less overlap: Less storage, potential information loss

### Code Location

`ChunkingService.java` - Implements the chunking logic

---

## Embedding Model

### Model Details

**Model:** Google Gemini text-embedding-004
**Dimensions:** 768
**Provider:** Google Cloud Vertex AI

### Why This Model

1. **Quality:** State-of-the-art semantic understanding
2. **Multilingual:** Supports multiple languages (important for international schools)
3. **Domain Adaptation:** Generalizes well to educational content
4. **Integration:** Native Spring AI support
5. **Cost:** Reasonable pricing for production use

### Embedding Dimensions: 768

**Why 768 dimensions:**

1. **Standard Size:** Common in transformer models (BERT-base, etc.)
2. **Expressiveness:** Captures nuanced semantic relationships
3. **Performance:** Good balance between quality and computational cost
4. **Storage:** Manageable database size
   - 768 floats × 4 bytes = 3KB per vector
   - For 100,000 chunks: ~300MB of vector data

**Alternatives Considered:**
- 384 dimensions: Faster, less expressive
- 1536 dimensions (OpenAI): More expressive, higher cost
- 768 dimensions: Sweet spot for our use case

### Configuration

```properties
spring.ai.vertex.ai.gemini.embedding.options.model=text-embedding-004
```

---

### Tuning Recommendations

Different use cases may warrant different thresholds:
- Legal/Medical: 0.8+ (require high confidence)
- General knowledge: 0.6-0.7 (balance)
- Exploratory: 0.5-0.6 (maximize recall)

### Configuration

```properties
rag.retrieval.similarity-threshold=0.7
```

---

## Testing Strategy

### Test Pyramid

**Unit Tests:**
- Service logic (ChatService, DocumentService)
- Chunking algorithm
- Utility functions
- Isolated component behavior

**Integration Tests (Testcontainers):**
- Full application context
- Real PostgreSQL with pgvector
- End-to-end workflows
- Four main test suites:
  1. TenantIsolationIntegrationTest
  2. DocumentDeletionIntegrationTest
  3. RefusalIntegrationTest
  4. EvaluationHarnessIntegrationTest

**Evaluation Harness:**
- Automated RAG quality testing
- 8 test categories (factual, multi-hop, follow-up, etc.)
- Metrics: retrieval success, refusal correctness, citation accuracy
- JSON-driven test cases for easy expansion

### Test Coverage

**Core Features:**
- Document upload and processing
- Vector similarity search
- Conversation memory
- Streaming responses
- Cascade deletion

**Quality Metrics:**
- Retrieval success rate
- Refusal correctness
- Citation accuracy
- Tenant isolation
- Answer quality (keyword matching)

### Running Tests

```bash
# All tests
./mvnw test

# Integration tests only
./mvnw test -Dtest="*IntegrationTest"

# Specific test
./mvnw test -Dtest=TenantIsolationIntegrationTest

# With coverage
./mvnw test jacoco:report
```

### Test Data

Test data is managed through:
- Flyway migrations for schema
- `test-init.sql` for pgvector setup
- Test fixtures in integration tests
- `evaluation-cases.json` for evaluation harness

---

## Known Limitations

### 1. LLM API Dependency

**Issue:** All chat requests require LLM API calls
**Impact:** 
- Latency: 1-3 seconds per request
- Cost: Per-token pricing
- Availability: Dependent on Google Cloud

**Mitigation:**
- Implement caching for common queries
- Circuit breaker pattern for failures
- Fallback to cached responses

### 2. Reranking Performance

**Issue:** Reranking makes N additional LLM calls (one per candidate)
**Impact:**
- 15 candidates = 15 API calls
- Adds 5-10 seconds to request
- Increased cost

**Mitigation:**
- Batch reranking (single API call)
- Fine-tuned reranking model
- Async reranking with progressive refinement

### 3. Chunking at Fixed Size

**Issue:** Fixed 500-character chunks may split semantic units
**Impact:**
- Important context split across chunks
- Retrieval may miss complete information

**Mitigation:**
- Sentence-aware chunking
- Semantic segmentation using NLP
- Adaptive chunk sizes based on content

### 4. No Feedback Loop

**Issue:** System doesn't learn from user interactions
**Impact:**
- Can't improve retrieval over time
- No personalization
- Repeated mistakes

**Future Work:**
- Click-through rate tracking
- Thumbs up/down on answers
- A/B testing framework
- Fine-tuning embeddings with feedback

### 5. Single Language Support

**Issue:** Full-text search configured for English only
**Impact:**
- Non-English content has degraded hybrid search
- Query rewriting may struggle with other languages

**Mitigation:**
- Multi-language text search configuration
- Language detection
- Per-tenant language settings

### 6. Memory Limitations

**Issue:** Conversation history limited to last N turns
**Impact:**
- Long conversations lose early context
- No summarization or compression

**Future Work:**
- Conversation summarization
- Hierarchical memory (recent + important)
- Vector memory for semantic retrieval

### 7. No Multi-Modal Support

**Issue:** Only text content is indexed
**Impact:**
- Images, tables, charts are ignored
- Visual information lost

**Future Work:**
- OCR for images
- Table extraction and structured representation
- Multi-modal embeddings

---

## What I Would Build With 2 More Weeks
- **Semantic Caching** – Hash query embeddings to bypass neural inference entirely; 80% hit rate yields 5x faster responses and transforms variable GPU-bound compute into sub-millisecond memory lookups, fundamentally altering the cost-performance curve of the system.

- **Batch Reranking** – Process candidate sets in bulk via vectorized computation to slash reranking API expenditure by 90%; this is not a marginal optimization but a prerequisite for economic viability at scale, as per-query reranking costs grow linearly with retrieval depth.

- **Feedback Loop** – Treat every thumbs up/down and click-through event as a labeled training instance; operationalizing this signal closes the gap between static model weights and evolving user intent, enabling continuous domain adaptation without requiring fresh human-annotated datasets.

- **Observability** – Structured logs with correlation IDs, distributed traces via OpenTelemetry, and SLO-based Prometheus alerts transform the system from a black box into a transparent, debuggable artifact; without this, performance regressions and tail latency pathologies remain invisible until they manifest as user churn.

- **Blue-Green Deployment with Automated Migrations** – Zero-downtime releases and instant rollbacks eliminate the deployment as a failure domain; decoupling schema migrations from application startup prevents lock contention and version skew, ensuring that database evolution never becomes a single point of failure during production updates.


