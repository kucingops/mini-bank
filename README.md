# MiniBank — Distributed Fund Transfer & Fraud Detection

A distributed fund transfer system with real-time fraud detection using a microservices architecture.

## 🏗 Architecture

```
┌─────────────────┐     ┌─────────────────────┐     ┌──────────────────────┐
│  Account Service│     │ Transaction Service  │     │  Fraud & Audit Svc   │
│     :8081       │     │       :8082          │     │       :8083          │
│                 │     │                      │     │                      │
│ • CRUD Accounts │     │ • Transfer Processing│     │ • Fraud Rules Engine │
│ • Balance Cache │     │ • Distributed Lock   │     │ • Audit Trail (ES)   │
│   (Redis)       │     │ • Redis Streams Pub  │     │ • Redis Stream Sub   │
│                 │     │ • EOD Reconciliation │     │ • Full-text Search   │
└────────┬────────┘     └──────────┬───────────┘     └──────────┬───────────┘
         │                         │                            │
    ┌────┴─────────────────────────┴────────────────────────────┴────┐
    │                        Infrastructure                          │
    │  PostgreSQL 16  │  Redis 7 (Cache+Lock+Streams)  │  Elastic 8 │
    └────────────────────────────────────────────────────────────────┘
```

## 🛠 Tech Stack → Requirement Mapping

| Requirement | Implementation |
|---|---|
| **Spring IoC** | Constructor injection across all services (`@Service`, `@Repository`, `@Component`) |
| **Java Stream** | Transaction data aggregation, filtering, DTO mapping |
| **Advance Native SQL** | Window Functions (`SUM() OVER`, `LAG`, `LEAD`), CTE for EOD reconciliation |
| **Containerization & Microservices** | 3 Spring Boot + Docker Compose |
| **Stream Based Application** | Redis Streams (consumer groups, ack) replacing Kafka |
| **Redis Caching & Data Grid** | Distributed Lock (Redisson), balance cache, daily limit cache, rate limiting |
| **Elastic & Non-Relational DB** | Audit trail + full-text search (multi_match, fuzzy) |

## 🚀 Quick Start

### Prerequisites
- Java 17+
- Maven 3.8+
- Docker / Podman

### 1. Start Infrastructure
```bash
docker-compose up -d postgres redis elasticsearch
```

### 2. Build Services
```bash
cd account-service && mvn clean package -DskipTests && cd ..
cd transaction-service && mvn clean package -DskipTests && cd ..
cd fraud-service && mvn clean package -DskipTests && cd ..
```

### 3. Run Services
**Option A: Docker Compose (all-in-one)**
```bash
docker-compose up --build
```

**Option B: Run locally (for development)**
```bash
# Terminal 1
cd account-service && mvn spring-boot:run

# Terminal 2
cd transaction-service && mvn spring-boot:run

# Terminal 3
cd fraud-service && mvn spring-boot:run
```

## 📡 API Endpoints

### Account Service (:8081)
| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/accounts` | Create account |
| GET | `/api/accounts` | List all accounts |
| GET | `/api/accounts/{id}` | Get account by ID |
| GET | `/api/accounts/{id}/balance` | Get balance (cached) |
| GET | `/api/accounts/by-number/{number}` | Find by account number |

### Transaction Service (:8082)
| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/transfers` | Initiate transfer (async) |
| GET | `/api/transfers/{id}` | Get transfer status |
| GET | `/api/transfers/history?accountId=` | Transaction history |
| GET | `/api/reconciliation/daily?date=` | EOD reconciliation report |

### Fraud & Audit Service (:8083)
| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/audit/search?q=` | Full-text audit search |
| GET | `/api/audit/account/{id}/trail` | Account audit trail |
| GET | `/api/audit/flagged` | Flagged transactions |
| GET | `/api/audit` | All audit logs |

## 🔄 Transfer Flow

1. Client → `POST /api/transfers` (Transaction Service)
2. Transaction Service acquires **Redis distributed lock** on both accounts
3. Checks **daily transfer limit** from Redis cache
4. Inserts PENDING transaction → PostgreSQL
5. Publishes `TransferRequested` event → **Redis Stream**
6. Returns `202 Accepted`
7. Fraud Service consumes event, runs **4 fraud rules**
8. Indexes audit log → **Elasticsearch**
9. Publishes `TransferValidated` or `TransferRejected` → Redis Stream
10. Transaction Service consumes result, executes **atomic debit/credit**

## 🔒 Fraud Detection Rules

| Rule | Trigger | Risk Score |
|---|---|---|
| Large Amount | Transfer > IDR 50,000,000 | +30 |
| High Frequency | > 10 transfers/hour | +40 |
| Suspicious Hours | 00:00 - 05:00 | +20 |
| Velocity Check | Same destination recently | +15 |

**Risk Levels:** LOW (<40) → PASS, MEDIUM (40-69) → BLOCK, HIGH (≥70) → BLOCK

## 📊 Example: EOD Reconciliation

```bash
curl http://localhost:8082/api/reconciliation/daily?date=2026-02-23
```

Uses **Advanced Native SQL** with:
- `SUM() OVER (PARTITION BY account_id ORDER BY created_at)` — running balance
- `LAG()` / `LEAD()` — previous/next transaction amount
- `CTE (WITH clause)` — daily average balance calculation

## 📁 Project Structure

```
test-minibank/
├── docker-compose.yml          # All infra + services
├── init-db/
│   ├── 01-schema.sql           # PostgreSQL schema
│   └── 02-seed.sql             # Sample data
├── account-service/            # Account management + Redis cache
├── transaction-service/        # Transfer + EOD reconciliation
└── fraud-service/              # Fraud detection + Elasticsearch audit
```

## 🧪 Testing

```bash
# Create account
curl -X POST http://localhost:8081/api/accounts \
  -H "Content-Type: application/json" \
  -d '{"holderName":"Test User","email":"test@mail.com","initialBalance":100000000,"dailyTransferLimit":50000000}'

# Initiate transfer
curl -X POST http://localhost:8082/api/transfers \
  -H "Content-Type: application/json" \
  -d '{"fromAccountId":"a1111111-1111-1111-1111-111111111111","toAccountId":"a2222222-2222-2222-2222-222222222222","amount":1000000,"description":"January installment payment"}'

# Check transfer status
curl http://localhost:8082/api/transfers/{transaction-id}

# Search audit trail
curl "http://localhost:8083/api/audit/search?q=installment"

# Flagged transactions
curl http://localhost:8083/api/audit/flagged
```
