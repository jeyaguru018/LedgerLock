# LedgerLock

**A concurrency-safe financial settlement engine that guarantees every transaction happens exactly once.**

> ⚠️ **This is a demonstration system built for educational and portfolio purposes. No real currency is transacted.**

---

## The Problem

Any system that moves money — wallets, marketplaces, payroll — faces one unforgiving requirement: a transaction must never be applied twice, and the books must always balance. Network retries, duplicate clicks, service crashes mid-transaction, and concurrent requests hitting the same account simultaneously are all routine in production. A naive implementation can double-charge a user, silently lose money, or let two simultaneous withdrawals overdraw an account.

LedgerLock is a backend engine built specifically to make that class of bug structurally impossible, not just handled by convention.

---

## What It Guarantees

- **Idempotency** — a request retried with the same idempotency key is never applied twice. A legitimate retry (e.g. after a network timeout) safely returns the original cached response instead of erroring or double-processing.
- **Double-entry integrity** — every transaction generates a matching debit and credit; the ledger is always mathematically balanced.
- **Concurrency safety** — optimistic locking (`@Version`) prevents two simultaneous writes to the same account from silently overwriting each other. Legitimate concurrent transactions automatically retry against fresh data instead of failing outright.
- **Zero partial state** — every transfer is wrapped in a single atomic database transaction. A failure at any point rolls back completely; there is no possibility of a debit without its matching credit.

These aren't just design goals — each one is backed by an automated concurrency stress test (see [Testing](#testing) below) that proves the guarantee under real multithreaded load, not just in theory.

---

## Architecture

```
Client (Browser)
    │
    ▼
┌─────────────────────────────────────────┐
│         Spring Boot REST API             │
│  ┌─────────────────────────────────┐    │
│  │  JWT Auth Filter (stateless)     │    │
│  ├─────────────────────────────────┤    │
│  │  Rate Limiter (Bucket4j)         │    │
│  │  10 req/min authenticated        │    │
│  │  5 req/min unauthenticated (IP)  │    │
│  ├─────────────────────────────────┤    │
│  │  Transaction Service              │    │
│  │  - Idempotency check              │    │
│  │  - Optimistic lock + retry        │    │
│  │  - Double-entry ledger writes     │    │
│  └─────────────────────────────────┘    │
└─────────────────────────────────────────┘
    │
    ▼
PostgreSQL (accounts, transactions, ledger_entries,
             idempotency_keys, users, refresh_tokens)
```

### Key Engineering Decisions

**Idempotency via a three-way branch, not a simple check-then-insert.**
A naive "check if key exists, then insert" has its own race condition — two threads can both pass the check simultaneously. Instead, the very first operation is an atomic `INSERT` into a table where the idempotency key is the primary key. The database's own unique constraint is the single source of truth for resolving the race — not application-level logic.

**`@Retryable` and `@Transactional` are deliberately on separate methods.**
Combining both annotations on one method is a well-known Spring pitfall: the transaction proxy can end up wrapping the retry proxy, trapping every retry attempt inside one already-failed transaction. LedgerLock uses self-injection so the outer method (`@Retryable`) calls an inner method (`@Transactional`) through the Spring proxy — guaranteeing every retry attempt opens a genuinely fresh transaction and re-reads the account's current version from the database.

**Authorization is derived entirely from the signed JWT, never from the request body.**
A client can put anything in a transfer request payload. Account ownership is verified server-side against the authenticated user extracted from the token's signature — a tampered payload cannot move funds out of an account the requester doesn't own.

**Account-not-found and account-not-owned return the identical error.**
If these returned different responses, an attacker could enumerate valid account numbers by observing which error they got. Both cases return the same generic "not found or access denied" response.

---

## Tech Stack

- **Backend:** Java 17, Spring Boot 3
- **Database:** PostgreSQL, Flyway migrations
- **Security:** Spring Security, JWT (access + rotating refresh tokens), BCrypt
- **Resilience:** Spring Retry, Bucket4j (rate limiting)
- **Testing:** JUnit 5, Spring Boot Test, custom concurrency stress harness (`ExecutorService`-based)
- **Frontend:** HTML, CSS, vanilla JavaScript

---

## Testing

Run the full suite:

```bash
mvn clean test
```

The test suite includes a dedicated concurrency stress harness (`ConcurrencyStressTest.java`) that:

1. Fires 100 concurrent identical requests (same idempotency key) at the same account and verifies only one is ever applied.
2. Fires many concurrent *different* transactions against the same account and verifies the final balance is mathematically correct with zero lost updates.
3. Attempts to overdraw an account under concurrent load and verifies it's cleanly rejected every time, with the balance never going negative.

---

## Running Locally

**Prerequisites:** Java 17, Maven, PostgreSQL running locally.

```bash
# 1. Create a local database
createdb ledgerlock

# 2. Set required environment variables (or use application.yml defaults for local dev)
export JWT_SECRET=$(openssl rand -hex 32)

# 3. Run
mvn spring-boot:run
```

The API will be available at `http://localhost:8080`. API documentation is available at `/swagger-ui.html`.

---

## Deployment

This project is configured for a split-origin deployment:

- **Backend:** Dockerized, deployed on Render (see `render.yaml` and `Dockerfile`)
- **Frontend:** Static files deployed separately (e.g. Vercel/Netlify)

Required production environment variables: `JWT_SECRET`, `ADMIN_EMAIL`, `APP_CORS_ALLOWED_ORIGINS`, plus database credentials (wired automatically via Render's Blueprint when using a managed PostgreSQL instance).

---

## What I'd Improve Next

Being direct about the current scope, rather than overstating it:

- **Refresh token grace period** — tokens currently rotate strictly (old token deleted immediately on use). In a multi-tab scenario, this can log out a second tab if both refresh simultaneously. A production fix would use a short grace-period window instead of immediate deletion.
- **Optimistic-lock reuse detection as a breach signal** — a reused, already-rotated refresh token currently just fails safely. The next iteration would treat that specific failure as a compromise signal and proactively revoke all of that user's active sessions.
- **Full RBAC** — the admin-only audit endpoint currently checks against a single configured admin email rather than a proper role system, to keep the scope focused on the core ledger problem.

---

## License

This project is for educational and portfolio purposes.
