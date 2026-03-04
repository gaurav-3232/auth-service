# Auth Service

![CI/CD](https://github.com/gaurav-3232/auth-service/actions/workflows/ci-cd.yml/badge.svg)

Production-quality Authentication & Authorization microservice built with **Spring Boot 3**, **JWT**, **PostgreSQL**, and **RBAC**.

---

## Features

- **Register / Login / Logout** with JWT access + refresh tokens
- **Refresh token rotation** with reuse detection (compromised token revokes all sessions)
- **Role-based access control** (USER, ADMIN)
- **BCrypt** password hashing (cost factor 12)
- **SHA-256** hashed refresh tokens stored in DB
- **RFC 7807** Problem Details error responses
- **Rate limiting** on login and refresh endpoints (Bucket4j, per-IP)
- **Structured JSON logging** in production with correlation/request IDs
- **Spring Actuator** with liveness and readiness probes
- **Secure HTTP headers** (HSTS, CSP, X-Frame-Options, etc.)
- **Flyway** database migrations
- **OpenAPI / Swagger UI** documentation
- **Testcontainers** integration tests
- **Docker Compose** for local development
- **GitHub Actions** CI/CD pipeline to GHCR + Render
- **Admin bootstrap** via environment variable (first-run only)

---

## Tech Stack

| Layer         | Technology                       |
| ------------- | -------------------------------- |
| Runtime       | Java 21, Spring Boot 3.3         |
| Security      | Spring Security, jjwt 0.12       |
| Database      | PostgreSQL 16, Hibernate/JPA     |
| Migrations    | Flyway                           |
| Rate Limiting | Bucket4j                         |
| Observability | Actuator, Logstash JSON encoder  |
| Docs          | springdoc-openapi (Swagger)      |
| Testing       | JUnit 5, Mockito, Testcontainers |
| CI/CD         | GitHub Actions, GHCR             |
| Deployment    | Render (primary), AWS ECS (docs) |

---

## Project Structure

```
auth-service/
├── .github/workflows/ci-cd.yml    # GitHub Actions pipeline
├── Dockerfile                      # Multi-stage, hardened, non-root
├── docker-compose.yml              # Local dev (Postgres + API)
├── render.yaml                     # Render deployment blueprint
├── pom.xml
└── src/main/java/com/authservice/
    ├── config/           # Security, CORS, rate limiting, request ID,
    │                     #   security headers, admin bootstrap
    ├── controller/       # Auth, User, Admin REST controllers
    ├── domain/entity/    # JPA entities + enums
    ├── dto/request/      # Validated request DTOs
    ├── dto/response/     # Response DTOs
    ├── exception/        # Global error handler, ProblemDetail
    ├── repository/       # Spring Data JPA repositories
    ├── security/         # JWT provider, filter, entry point, hash util
    └── service/          # AuthService, UserService
```

---

## Local Development

### Prerequisites

- **Docker Desktop** (only requirement — Java runs inside Docker)

### Quick Start

```bash
git clone https://github.com/gaurav-3232/auth-service.git
cd auth-service

# Start Postgres + API (change port if 5432 is in use)
docker compose up --build -d

# Wait ~30s, then verify:
curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

Swagger UI: **http://localhost:8080/swagger-ui.html**

### Local Admin User

Docker Compose automatically bootstraps an admin user:

- Email: `admin@localhost.com`
- Password: `AdminPass123!`

### Run Natively (faster dev cycle)

```bash
docker compose up postgres -d
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

### Run Tests

```bash
./mvnw test          # unit tests only
./mvnw verify        # unit + integration (needs Docker for Testcontainers)
```

### Shut Down

```bash
docker compose down       # keep data
docker compose down -v    # delete database volume
```

---

## API Reference

### Auth Endpoints (public)

**Register:**

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@example.com","password":"SecurePass123!","name":"Alice"}' | jq .
```

**Login:**

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@example.com","password":"SecurePass123!"}' | jq .
```

**Refresh Token:**

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"<REFRESH_TOKEN>"}' | jq .
```

**Logout:**

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/logout \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"<REFRESH_TOKEN>"}' | jq .
```

### User Endpoints (authenticated)

**Get Current User:**

```bash
curl -s http://localhost:8080/api/v1/users/me \
  -H "Authorization: Bearer <ACCESS_TOKEN>" | jq .
```

### Admin Endpoints (ADMIN role only)

**List Users (paged):**

```bash
curl -s "http://localhost:8080/api/v1/admin/users?page=0&size=10" \
  -H "Authorization: Bearer <ADMIN_TOKEN>" | jq .
```

**Update Role:**

```bash
curl -s -X PATCH http://localhost:8080/api/v1/admin/users/<USER_ID>/role \
  -H "Authorization: Bearer <ADMIN_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"role":"ADMIN"}' | jq .
```

**Deactivate User:**

```bash
curl -s -X PATCH http://localhost:8080/api/v1/admin/users/<USER_ID>/deactivate \
  -H "Authorization: Bearer <ADMIN_TOKEN>" | jq .
```

### Observability

```bash
curl http://localhost:8080/actuator/health           # overall
curl http://localhost:8080/actuator/health/liveness   # k8s/container liveness
curl http://localhost:8080/actuator/health/readiness  # k8s/container readiness
curl http://localhost:8080/actuator/info              # app info
```

---

## Security Design

| Aspect           | Implementation                                                |
| ---------------- | ------------------------------------------------------------- |
| Password storage | BCrypt (cost 12)                                              |
| Access token     | JWT (HS256/HS512), 15 min TTL                                 |
| Refresh token    | Random UUID, SHA-256 hashed in DB, 7 day TTL                  |
| Token rotation   | Old token revoked on use, linked via `replaced_by_token_id`   |
| Reuse detection  | Revoked token reuse -> all user tokens revoked                |
| User enumeration | Login returns identical error for wrong email/password        |
| Rate limiting    | 10 login / 20 refresh requests per IP per minute              |
| HTTP headers     | HSTS, CSP, X-Frame-Options DENY, nosniff, etc.                |
| Correlation      | X-Request-Id on every request (auto-generated or client-sent) |
| Deactivation     | Blocked from login/refresh, all tokens revoked                |

---

## Deployment

### Option A: Render (Implemented + Wired)

#### Step 1: Push to GitHub

```bash
git init && git add -A && git commit -m "initial commit"
gh repo create auth-service --public --source=. --push
```

#### Step 2: Deploy via Render Dashboard

1. Go to **https://dashboard.render.com** and sign in.
2. Click **New > Blueprint** and connect your GitHub repo.
3. Render reads `render.yaml` and creates:
   - A **Web Service** (Docker) for the API
   - A **PostgreSQL** database
4. In the Render dashboard, set these environment variables for the web service:
   - `CORS_ORIGINS` = your frontend URL (e.g. `https://myapp.vercel.app`)
   - `ADMIN_BOOTSTRAP_EMAIL` = your admin email (one-time, remove after first deploy)
   - `ADMIN_BOOTSTRAP_PASSWORD` = your admin password (one-time, remove after first deploy)
5. Render auto-generates `JWT_SECRET` and wires `DB_URL`/`DB_USERNAME`/`DB_PASSWORD` from the database.

#### Step 3: Set up CI/CD Deploy Hook

1. In Render dashboard, go to your web service > **Settings > Deploy Hook**.
2. Copy the hook URL.
3. In your GitHub repo: **Settings > Secrets > Actions** — add `RENDER_DEPLOY_HOOK_URL` with the copied URL.
4. Every push to `main` now triggers: test -> build image -> push GHCR -> deploy Render.

#### Step 4: Verify

```bash
# Replace with your Render URL
export API_URL=https://auth-service-xxxx.onrender.com

curl -s $API_URL/actuator/health | jq .

curl -s -X POST $API_URL/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@yourdomain.com","password":"YourAdminPass!"}' | jq .
```

#### Step 5: Remove bootstrap env vars

After confirming the admin user exists, **delete** `ADMIN_BOOTSTRAP_EMAIL` and `ADMIN_BOOTSTRAP_PASSWORD` from Render environment variables.

---

### Option B: AWS ECS Fargate (Documented Steps)

#### Prerequisites

- AWS CLI configured, ECR repository created
- RDS PostgreSQL instance (or Aurora Serverless)
- VPC with private subnets

#### Step 1: Push Image to ECR

```bash
aws ecr get-login-password --region us-east-1 | \
  docker login --username AWS --password-stdin 123456789.dkr.ecr.us-east-1.amazonaws.com

docker build -t auth-service .
docker tag auth-service:latest 123456789.dkr.ecr.us-east-1.amazonaws.com/auth-service:latest
docker push 123456789.dkr.ecr.us-east-1.amazonaws.com/auth-service:latest
```

#### Step 2: Create ECS Task Definition

```json
{
  "family": "auth-service",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "512",
  "memory": "1024",
  "containerDefinitions": [
    {
      "name": "auth-service",
      "image": "123456789.dkr.ecr.us-east-1.amazonaws.com/auth-service:latest",
      "portMappings": [{ "containerPort": 8080 }],
      "healthCheck": {
        "command": [
          "CMD-SHELL",
          "curl -f http://localhost:8080/actuator/health/liveness || exit 1"
        ],
        "interval": 30,
        "timeout": 5,
        "retries": 3,
        "startPeriod": 60
      },
      "environment": [
        { "name": "SPRING_PROFILES_ACTIVE", "value": "prod" },
        { "name": "CORS_ORIGINS", "value": "https://your-frontend.com" }
      ],
      "secrets": [
        {
          "name": "DB_URL",
          "valueFrom": "arn:aws:ssm:us-east-1:123456789:parameter/auth-service/db-url"
        },
        {
          "name": "DB_USERNAME",
          "valueFrom": "arn:aws:ssm:us-east-1:123456789:parameter/auth-service/db-user"
        },
        {
          "name": "DB_PASSWORD",
          "valueFrom": "arn:aws:ssm:us-east-1:123456789:parameter/auth-service/db-pass"
        },
        {
          "name": "JWT_SECRET",
          "valueFrom": "arn:aws:ssm:us-east-1:123456789:parameter/auth-service/jwt-secret"
        }
      ],
      "logConfiguration": {
        "logDriver": "awslogs",
        "options": {
          "awslogs-group": "/ecs/auth-service",
          "awslogs-region": "us-east-1",
          "awslogs-stream-prefix": "ecs"
        }
      }
    }
  ]
}
```

#### Step 3: Create ECS Service

```bash
aws ecs create-service \
  --cluster production \
  --service-name auth-service \
  --task-definition auth-service:1 \
  --desired-count 2 \
  --launch-type FARGATE \
  --network-configuration "awsvpcConfiguration={subnets=[subnet-xxx],securityGroups=[sg-xxx],assignPublicIp=DISABLED}" \
  --load-balancers "targetGroupArn=arn:aws:elasticloadbalancing:...,containerName=auth-service,containerPort=8080"
```

#### Step 4: Database Migration Strategy

Flyway runs automatically on application startup. For ECS:

- Migrations run when the first container starts.
- Additional containers wait for the Flyway lock to release.
- For zero-downtime deploys, ensure migrations are backward-compatible.

---

## Environment Variables Reference

| Variable                   | Required | Default                 | Description                          |
| -------------------------- | -------- | ----------------------- | ------------------------------------ |
| `DB_URL`                   | Yes\*    | `jdbc:postgresql://...` | JDBC connection string               |
| `DB_USERNAME`              | Yes\*    | `authuser`              | Database username                    |
| `DB_PASSWORD`              | Yes\*    | `authpass`              | Database password                    |
| `JWT_SECRET`               | Yes\*    | (dev key)               | HMAC signing key (min 256 bits)      |
| `JWT_ACCESS_EXPIRY_MS`     | No       | `900000` (15 min)       | Access token TTL                     |
| `JWT_REFRESH_EXPIRY_MS`    | No       | `604800000` (7 days)    | Refresh token TTL                    |
| `CORS_ORIGINS`             | Yes\*    | `http://localhost:3000` | Comma-separated allowed origins      |
| `PORT`                     | No       | `8080`                  | Server port                          |
| `SPRING_PROFILES_ACTIVE`   | No       | (default)               | `local` or `prod`                    |
| `RATE_LIMIT_LOGIN`         | No       | `10`                    | Login attempts per IP per minute     |
| `RATE_LIMIT_REFRESH`       | No       | `20`                    | Refresh attempts per IP per minute   |
| `ADMIN_BOOTSTRAP_EMAIL`    | No       | (empty)                 | First admin user email (one-time)    |
| `ADMIN_BOOTSTRAP_PASSWORD` | No       | (empty)                 | First admin user password (one-time) |
| `DB_POOL_SIZE`             | No       | `10`                    | HikariCP max pool size               |

\*Required in production — defaults are for local dev only.

---

## Troubleshooting

### Database Connection Failed

```
org.postgresql.util.PSQLException: Connection to localhost:5432 refused
```

**Fix:** Ensure Postgres is running. Locally: `docker compose up postgres -d`. In production: check `DB_URL` points to the correct host and the security group/firewall allows the connection.

### CORS Errors

```
Access to XMLHttpRequest blocked by CORS policy
```

**Fix:** Set `CORS_ORIGINS` to your exact frontend URL including protocol: `https://myapp.vercel.app` (not `https://myapp.vercel.app/`). Multiple origins: comma-separated, no spaces.

### JWT Token Invalid After Redeployment

```
{"detail":"Authentication is required to access this resource"}
```

**Cause:** `JWT_SECRET` changed between deploys, invalidating all issued tokens.
**Fix:** Use a persistent secret (e.g. Render's `generateValue: true` is stable). If the secret must change, users need to re-authenticate.

### Flyway Migration Checksum Mismatch

```
FlywayException: Validate failed: Migration checksum mismatch
```

**Cause:** A previously applied migration file was modified.
**Fix:** Never edit applied migrations. Create a new `V3__fix_xxx.sql` instead. If local dev, drop the DB: `docker compose down -v && docker compose up -d`.

### Rate Limit Hit (429)

```json
{ "title": "Too Many Requests", "status": 429 }
```

**Fix:** Wait 60 seconds. If testing, increase `RATE_LIMIT_LOGIN` / `RATE_LIMIT_REFRESH` env vars. In production, this protects against brute-force attacks — do not disable.

### Render Free Tier Cold Start

Render free/starter tier spins down after inactivity. First request after idle may take 30-60 seconds.
**Fix:** Upgrade to a paid plan, or use an external health pinger (e.g. UptimeRobot) to keep it warm.

---

## License

MIT
