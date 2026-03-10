# URL Shortener Service

A full-stack URL shortener with redirection and click-level analytics.

---

## Tech Stack

| Layer      | Technology                                   |
|------------|----------------------------------------------|
| Backend    | Dropwizard 5.0.1 · Java 17 · Maven          |
| Database   | PostgreSQL 15                                |
| Migrations | Flyway 10 (runs automatically at startup)    |
| ORM / SQL  | JDBI 3 (SQL Object API)                      |
| API Spec   | OpenAPI 3.0 (`src/main/resources/openapi.yaml`) |
| Frontend   | Next.js 16 · TypeScript · Yarn               |
| Packaging  | Docker · Docker Compose                      |

---

## Project Structure

```
url-shortner-service/
├── src/main/
│   ├── java/com/urlshortener/
│   │   ├── UrlShortenerApplication.java    # Entry point + wiring
│   │   ├── UrlShortenerConfiguration.java  # Config binding
│   │   ├── filter/CorsFilter.java          # JAX-RS CORS filter
│   │   ├── model/                          # ShortUrl, ClickEvent, AliasType
│   │   │   └── mapper/                     # JDBI row mappers
│   │   ├── repository/                     # UrlRepository, ClickEventRepository
│   │   ├── service/UrlService.java         # Business logic
│   │   └── resource/UrlResource.java       # REST endpoints
│   ├── resources/
│   │   ├── db/migration/                   # Flyway SQL (V1, V2)
│   │   └── openapi.yaml                    # OpenAPI 3.0 spec
│   └── config/config.yml                   # Dropwizard runtime config
├── frontend/                               # Next.js 16 SPA
│   └── src/
│       ├── app/page.tsx                    # Main UI page
│       ├── lib/api.ts                      # API client
│       └── types/api.ts                    # TypeScript types
├── Dockerfile                              # Backend image (multi-stage)
├── docker-compose.yml                      # PostgreSQL 15 + backend
└── pom.xml
```

---

## Running the Application

### 1. Run the Backend

**Command:**

```bash
# From the repo root
docker compose up --build
```

| URL | Purpose |
|-----|---------|
| http://localhost:8080 | REST API |
| http://localhost:8081 | Admin / health |

> **Tip:** You can use the included `test.http` file to test all endpoints immediately (requires VS Code REST Client).

```bash
# Stop services (keeps DB volume)
docker compose down

# Stop and wipe database
docker compose down -v

# connect to database from terminal
docker exec -it url-shortener-postgres psql -U urlshortener -d urlshortener
```

---

### Frontend — Next.js dev server

Run this in a **separate terminal** while the backend is running.

```bash
cd frontend

# First time only — install dependencies
yarn install

# Start hot-reload dev server on port 3000
yarn dev
```

Open **http://localhost:3000** in your browser.

The backend URL is configured in `frontend/.env.local`:
```env
NEXT_PUBLIC_API_URL=http://localhost:8080
```

---

## REST API Reference

Base URL: `http://localhost:8080`

> The `test.http` file at the project root contains ready-to-run requests for all backend endpoints (requires VS Code REST Client).

---

## Configuration Reference

`src/main/config/config.yml` — all `${VAR:-default}` tokens are substituted at startup:

| Key | Env var | Default | Description |
|-----|---------|---------|-------------|
| `database.url` | `DB_URL` | `jdbc:postgresql://localhost:5432/urlshortener` | JDBC connection string |
| `database.user` | `DB_USER` | `urlshortener` | DB username |
| `database.password` | `DB_PASSWORD` | `password` | DB password |
| `defaultDomain` | `DEFAULT_DOMAIN` | `localhost:8080` | Domain used when caller omits `domain` in the request |

---

## Flyway Migrations

Applied automatically when the backend starts.

| Version | File | Description |
|---------|------|-------------|
| V1 | `V1__create_short_urls_table.sql` | Creates `short_urls` table |
| V2 | `V2__create_click_events_table.sql` | Creates `click_events` table |

Add new migrations by creating `V{N}__{description}.sql` in `src/main/resources/db/migration/`.

---


## Architectural Notes

### Frontend
Next.js was chosen for the frontend due to familiarity and its advantages as a single-page application framework, enabling faster navigation and a smoother user experience.

### API Design
REST is used to keep the architecture simple for this small-scale service. GraphQL could be considered in the future if more complex analytics or multi-dataset queries are required. For URL creation and redirects, REST still seems like an ideal choice due to simplicity.

### Programming Language
Java was chosen due to familiarity with the syntax and its proven scalability for production-grade applications.

### Microservice Framework
Two options were evaluated — Spring Boot and Dropwizard. Dropwizard was chosen because of recent experience, allowing faster development.

### Database Choice
A relational database was selected due to the small, structured nature of the entities and ACID properties which in this case will ensure no alias collisions. PostgreSQL is known for its scalability and performance which is making it a suitable choice for this service.

### Duplicate Alias Handling
To speed up alias uniqueness checks when creating short URLs, an in-memory HashSet is currently used. While suitable for a single-instance local dev environment, it does not scale well across multiple instances. In production, a distributed cache such as Redis would be needed.

### Click Event Storage
Click events are stored directly in the database for this small-scale service. For millions of users, a low-latency, high-throughput streaming service such as Kafka or Kinesis could be used to publish events asynchronously. Consumers would then parse and store these events in an analytics datastore like PostgreSQL or a more sophisticated system such as Snowflake with RBAC support. This asynchronous approach ensures that redirects remain fast and unaffected by analytics processing.

### Scaling & Load Balancing
As user traffic grows, multiple instances of the service can be deployed behind a load balancer to efficiently distribute requests and improve reliability. Redirect requests typically far exceed short URL creation requests. To handle this load, a read replica of the primary database can be introduced, with all writes (e.g., creating short URLs) directed to the primary. Redirect handling could also be moved to a separate service that reads exclusively from the replica, allowing independent scaling. Even if redirects remain in the same service, reads can be routed to the replica while writes continue to the primary, improving performance and scalability. To further reduce load on the read replica, frequently accessed URLs can be cached using an LRU eviction policy.

### Alias Generation
`SecureRandom` is not the best choice for large-scale systems for the following reasons:

- **Collision risk** — random generation can produce duplicate aliases, requiring a database unique constraint and retry logic.
- **No collision handling in code** — the method generates aliases but does not check or retry if the alias already exists.
- **Non-deterministic mapping** — the same long URL generates different aliases each time instead of reusing an existing one.
- **Performance overhead** — random generation combined with database uniqueness checks can become inefficient at very high scale.
- **Fixed alias length** — always generating 8-character aliases reduces flexibility and may create unnecessarily long URLs for small datasets.
- **SecureRandom overhead** — `SecureRandom` is slower than necessary since cryptographic security is not required here.
- **Enumeration risk** — attackers may attempt to brute-force valid short URLs due to the predictable format and finite keyspace.

This is a critical component of the system and will need dedicated research to determine the best generation strategy.

### Security
Currently, the application does not implement user management or rate limiting due to time constraints. To protect the service against abuse, such as bot attacks, IP-based rate limiting and user authentication could be added. Additionally, analytics endpoints could require authentication, as the data may be considered proprietary for the links each user owns.

---

## Future Enhancements

### Functional

- **QR Code & Social Sharing** — provide QR codes and built-in social media sharing options to increase link visibility and improve user engagement.
- **User Management** — introduce user accounts with optional paid subscriptions to enable personalized and secure experiences.
- **Custom Domains** — allow branded short links (e.g., `youtu.be/videoID`) to improve brand recognition, trust, and click-through rates.
- **Daily Reporting & Analytics** — provide insights into link performance and ROI.
- **Link Expiration** — allow users to set expiration dates for links for better control and security.
- **Edit URLs** — allow editing the long URL for an alias after creation so that changes do not require resharing a new short URL.
- **Automatic Link Expiration** — support automatic expiration based on a user-defined timeframe or inactivity (e.g., X months or years). When an expired link is accessed, display an error page. Expired links can either be moved to a historical datastore for auditing/analytics or deleted entirely.
- **UI Improvements** — revamp the UI to better utilise available space and introduce areas for marketing or advertisement banners.

### Technical

- **Automated OpenAPI Generation** — automatically generate the OpenAPI specification during the Maven build and commit the generated file to the repository. This ensures that any changes to backend DTOs are automatically reflected in the API contract.
- **TypeScript Code Generation for UI** — generate TypeScript types from the OpenAPI specification and publish them as a package that the frontend can consume. This removes the need for the UI to manually recreate types and keeps the backend as the single source of truth, ensuring a consistent contract with minimal maintenance.
- **Explore better alias generation** — research alternatives to `SecureRandom` (e.g., hash-based or counter-based schemes) to improve performance and reduce collision handling complexity.
- **Integration test coverage** — currently there are integration tests covering short URL creation, successful redirect, and daily click aggregation, plus unit tests for frontend and backend. Additional coverage for validations and edge cases would further reduce regression risk.