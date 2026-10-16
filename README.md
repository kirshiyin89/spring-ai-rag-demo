# FinFlow AI Assistant --- Local PostgreSQL and pgvector Notes

This guide collects the commands I use to start the local PostgreSQL
database, connect to it, inspect the RAG vector store, and clear test
data.

## 1. Start PostgreSQL with Docker Compose

Run these commands from the project root, where your `compose.yaml` or
`../docker-compose.yml` file lives:

``` bash
docker compose up -d
docker compose ps
```

Check the database logs if PostgreSQL does not start:

``` bash
docker compose logs -f
```

To stop the services:

``` bash
docker compose down
```

`docker compose down` keeps the database volume unless your Compose
configuration says otherwise. **Do not add `-v` unless you intend to
delete the persisted database data.**

If the database service is not named `postgres`, get its name from
`docker compose ps` and replace `postgres` in the commands below.

## 2. Database connection details

The current local Spring configuration in `application.yaml` uses:

Setting Value
  ---------- ---------------
Host       `localhost`
Port       `5432`
Database   `ragdb`
Username   `raguser`
Password   `ragpassword`

These credentials are for local development only. Do not use them in a
shared or production environment, and do not commit real secrets to Git.

## 3. Log in to PostgreSQL

### Option A: Open `psql` inside the database container

``` bash
docker compose exec postgres psql -U raguser -d ragdb
```

If your Compose service has another name, replace `postgres` with that
service name.

### Option B: Connect from your host

If you have the PostgreSQL command-line client installed:

``` bash
psql -h localhost -p 5432 -U raguser -d ragdb
```

Enter `ragpassword` when prompted.

## 4. Useful `psql` commands

Run these after logging in:

``` sql
-- List tables
\dt

-- Inspect the Spring AI vector table
\d vector_store

-- Show the installed extensions
\dx

-- Exit psql
\q
```

Spring AI's pgvector store commonly uses a table named `vector_store`.
If `\dt` shows a different name, inspect that table instead.

## 5. Inspect the vector store

``` sql
-- Count indexed chunks
SELECT COUNT(*) FROM vector_store;

-- See a few stored chunks and their metadata
SELECT id, content, metadata
FROM vector_store
LIMIT 10;

-- Count chunks grouped by source filename
SELECT metadata->>'filename' AS filename, COUNT(*) AS chunks
FROM vector_store
GROUP BY metadata->>'filename'
ORDER BY filename;

-- Find chunks belonging to one document
SELECT id, content, metadata
FROM vector_store
WHERE metadata->>'filename' = 'gitlab-access.md';
```

The exact columns depend on the Spring AI pgvector schema configured by
the application. If a query fails, inspect the table with
`\d vector_store` and adjust it to the columns shown there.

## 6. Clear vector data

**Warning: these commands delete indexed data. Use them only when you
intend to clear the local vector store.**

### Delete all vectors but keep the table

``` sql
TRUNCATE TABLE vector_store;
```

### Delete vectors for one document

``` sql
DELETE FROM vector_store
WHERE metadata->>'filename' = 'gitlab-access.md';
```

### Verify the result

``` sql
SELECT COUNT(*) FROM vector_store;
```

`TRUNCATE` clears every row from `vector_store`. It does not drop the
table or remove the pgvector extension. If the table also contains
vectors from other sources, they will be deleted too.

## 7. Check the pgvector extension

``` sql
SELECT extname
FROM pg_extension
WHERE extname = 'vector';
```

If this returns `vector`, the extension exists in the current database.

## 8. Current RAG workflow

1. Start PostgreSQL: `docker compose up -d`.

2. Start the Spring Boot application.

3. Load the Markdown documents through the current development
   endpoint:

   ``` http
   curl -i -u "raguser:your-database-password" \

-X POST http://localhost:8080/api/chat/load-vectors
```

4. Check the indexed chunk count:

   ``` sql
   SELECT COUNT(*) FROM vector_store;
   ```

5. Test the chatbot:

   ``` http
   GET http://localhost:8080/api/chat/rag?conversationId=alice&question=What%20does%20FinFlow%20Technologies%20do%3F
   ```

The ingestion endpoint currently adds chunks each time it runs. Until
ingestion becomes idempotent, repeated calls can create duplicate
vectors. Avoid reloading the same documents repeatedly unless you intend
to test ingestion behavior.

The `GET` ingestion endpoint is a development convenience; a later
improvement is to switch it to a protected `POST` endpoint.

## 9. Reset reminders

- `docker compose down`: stops and removes containers/networks created
  by Compose; named volumes usually remain.
- `docker compose down -v`: also removes Compose-managed volumes and
  can permanently delete the local database data.
- `TRUNCATE TABLE vector_store`: clears indexed chunks but keeps the
  table.
- `DROP TABLE vector_store`: removes the table itself. Avoid this
  unless you deliberately want to recreate the schema.
