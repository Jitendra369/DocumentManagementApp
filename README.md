# Document Management System

A REST API for uploading, listing, searching, viewing, downloading and soft-deleting documents.
File contents are stored in **Amazon S3**. Metadata is stored in **PostgreSQL**.

Built with Spring Boot 4, Java 17, AWS SDK v2, Flyway and Apache Tika.

---

## Features

- **Upload** files (multipart) with size and *real* content-type validation (Tika inspects the bytes, not the file extension)
- **List and search** your documents by name, with pagination
- **View / download** through short-lived **pre-signed S3 URLs**. The bucket stays private and files never stream through the app
- **Soft delete and restore**
- **Per-user isolation** via the `X-User-Id` header: other users get `404` for documents they don't own
- **Encryption at rest** (S3 SSE `AES256`)
- Schema managed by **Flyway**; Hibernate only validates it
- Runs locally against **LocalStack** (fake S3) with no AWS account needed

## Tech stack

| Layer      | Technology                                 |
|------------|--------------------------------------------|
| Language   | Java 17                                    |
| Framework  | Spring Boot 4 (Web MVC, Data JPA, Validation, Actuator) |
| Storage    | Amazon S3 (AWS SDK v2) / LocalStack locally |
| Database   | PostgreSQL + Flyway migrations             |
| File types | Apache Tika                                |
| Build      | Maven (wrapper included)                   |

---

## Project structure

```
src/main/java/com/doc_manag
├── controller/DocumentController      REST endpoints (thin)
├── service/DocumentService            business rules: validate -> S3 -> DB, ownership checks
├── service/FileValidationService      size + real content-type check (Tika)
├── storage/StorageService             storage abstraction
├── storage/S3StorageService           the only class that talks to the AWS SDK
├── repository/DocumentRepository      Spring Data JPA queries
├── entity/Document                    metadata row (file bytes live in S3)
├── config/S3Config, S3Properties      S3Client + S3Presigner beans, app.s3.* settings
├── config/DotEnvLoader                loads .env from any working directory
└── exception/GlobalExceptionHandler   consistent JSON error responses

src/main/resources
├── application.yml                    shared settings (default profile: local)
├── application-local.yml              local PostgreSQL + LocalStack
├── application-docker.yml             app running inside docker compose
├── application-aws.yml                real AWS (everything from env vars)
└── db/migration/V1__create_documents_table.sql
```

---

## Getting started (local)

### Prerequisites
- JDK 17+
- Docker (for LocalStack, and optionally PostgreSQL)
- A PostgreSQL database for the app. Use an **empty** database: Flyway won't migrate a schema that already contains other tables.

### 1. Configure environment variables

```bash
cp .env.example .env
```

Edit `.env` and set your database URL, username and password. `.env` is git-ignored and must never be committed.
The app finds `.env` even if your IDE starts it from another folder. Real environment variables always override `.env`.

### 2. Start LocalStack (fake S3) and, optionally, PostgreSQL

```bash
docker compose up -d localstack          # S3 on http://localhost:4566
docker compose up -d postgres            # optional: PostgreSQL on localhost:5433
```

If you use the Docker PostgreSQL, set `DB_URL=jdbc:postgresql://localhost:5433/dms` in `.env` and the matching URL in `application-local.yml`.

### 3. Run the app

```bash
./mvnw spring-boot:run            # Windows: mvnw.cmd spring-boot:run
```

On startup Flyway creates the `documents` table, and the app creates the S3 bucket in LocalStack.
The API is then available at `http://localhost:8080`.

---

## Configuration

Profiles: `local` (default), `docker`, `aws`. Select one with `SPRING_PROFILES_ACTIVE`.

| Variable                | Used by          | Description                                      |
|-------------------------|------------------|--------------------------------------------------|
| `DB_URL`                | aws              | JDBC URL, e.g. `jdbc:postgresql://host:5432/dms?sslmode=require` |
| `DB_USERNAME`           | all              | Database user                                    |
| `DB_PASSWORD`           | all              | Database password                                |
| `S3_BUCKET`             | aws              | S3 bucket name                                   |
| `AWS_REGION`            | all              | AWS region (default `ap-south-1`)                |
| `AWS_ACCESS_KEY_ID`     | optional         | Static AWS key. Leave unset to use an IAM role or `~/.aws/credentials` |
| `AWS_SECRET_ACCESS_KEY` | optional         | Static AWS secret                                |

See [`.env.example`](.env.example) for a full template.

### Running against real AWS S3

1. Create a private bucket (keep "Block all public access" on).
2. Give the app's IAM user or role this policy (replace `YOUR-BUCKET`):

   ```json
   {
     "Version": "2012-10-17",
     "Statement": [
       { "Effect": "Allow", "Action": "s3:ListBucket", "Resource": "arn:aws:s3:::YOUR-BUCKET" },
       { "Effect": "Allow", "Action": ["s3:PutObject", "s3:GetObject", "s3:DeleteObject"],
         "Resource": "arn:aws:s3:::YOUR-BUCKET/*" }
     ]
   }
   ```
   `s3:ListBucket` is needed so a missing object returns `404` instead of `403`.
3. Run with `SPRING_PROFILES_ACTIVE=aws` and set `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `S3_BUCKET` and `AWS_REGION`.
   Prefer an IAM role (or `aws configure` locally) over putting access keys in `.env`.

---

## API

Every request needs an `X-User-Id` header (a stand-in for real authentication).

| Method | Endpoint                          | Description                                |
|--------|-----------------------------------|--------------------------------------------|
| POST   | `/api/documents`                  | Upload a file (multipart field `file`)     |
| GET    | `/api/documents?page=0&size=20`   | List your documents                        |
| GET    | `/api/documents/search?name=...`  | Search by file name                        |
| GET    | `/api/documents/{id}`             | Get metadata                               |
| GET    | `/api/documents/{id}/view`        | Pre-signed URL that opens inline           |
| GET    | `/api/documents/{id}/download`    | Pre-signed URL that downloads as attachment |
| DELETE | `/api/documents/{id}`             | Soft delete                                |
| POST   | `/api/documents/{id}/restore`     | Restore a soft-deleted document            |

Allowed types: PDF, PNG, JPEG, plain text, DOCX, XLSX. Maximum size: 25 MB.

### Examples

```bash
# Upload
curl -H "X-User-Id: ravi" -F "file=@invoice.pdf" http://localhost:8080/api/documents

# List / search
curl -H "X-User-Id: ravi" "http://localhost:8080/api/documents?page=0&size=20"
curl -H "X-User-Id: ravi" "http://localhost:8080/api/documents/search?name=invoice"

# View / download -> {"url": "...", "expiresAt": "..."}; open the url in a browser
curl -H "X-User-Id: ravi" http://localhost:8080/api/documents/{id}/view
curl -H "X-User-Id: ravi" http://localhost:8080/api/documents/{id}/download

# Soft delete / restore
curl -X DELETE -H "X-User-Id: ravi" http://localhost:8080/api/documents/{id}
curl -X POST   -H "X-User-Id: ravi" http://localhost:8080/api/documents/{id}/restore

# Another user can't see ravi's document -> 404
curl -H "X-User-Id: priya" http://localhost:8080/api/documents/{id}
```

Health check: `GET /actuator/health`

---

## Roadmap

- Scheduled job to permanently delete documents soft-deleted more than 30 days ago
- Pre-signed **upload** URLs for large files (status `PENDING` -> `ACTIVE`)
- Spring Security / JWT instead of the `X-User-Id` header
- Integration tests with Testcontainers (PostgreSQL + LocalStack)
- `Dockerfile` for the `app` service in `docker-compose.yml`
