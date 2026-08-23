# Distributed Real-Time Multiplayer Chess Platform

A production-grade, event-driven, full-stack multiplayer gaming platform built with Spring Boot 3, React 19, STOMP WebSockets, Redis, Apache Kafka, and PostgreSQL. Designed with domain-driven principles, decoupled architecture, and real-time state synchronization.

---

<!-- Badges showcasing CI, coverage, and Docker -->

[![Build Status](https://github.com/2005rishabh/game-platform/actions/workflows/ci.yml/badge.svg)](https://github.com/2005rishabh/game-platform/actions)
[![Codecov](https://codecov.io/gh/2005rishabh/game-platform/branch/main/graph/badge.svg)](https://codecov.io/gh/2005rishabh/game-platform)
[![Docker Pulls](https://img.shields.io/docker/pulls/2005rishabh/game-platform.svg)](https://hub.docker.com/r/2005rishabh/game-platform)
[![Frontend Status](https://img.shields.io/website?down_color=red&down_message=down&up_message=up&url=https%3A%2F%2Fgame-platform-orpin-zeta.vercel.app)](https://game-platform-orpin-zeta.vercel.app)
[![Backend Status](https://img.shields.io/website?down_color=red&down_message=down&up_message=up&url=https%3A%2F%2Fgame-platform-n1k1.onrender.com)](https://game-platform-n1k1.onrender.com)
[![Vercel Deploys](https://img.shields.io/badge/vercel-deploys-blue?logo=vercel)](https://vercel.com)
[![Render Deploys](https://img.shields.io/badge/render-deploys-blue?logo=render)](https://dashboard.render.com)

## Live Demos

- **Frontend (Client):** https://game-platform-orpin-zeta.vercel.app/
- **Backend (API / WebSockets):** https://game-platform-n1k1.onrender.com/

## Table of Contents

- [Project Executive Summary](#1-project-executive-summary)
- [Technology Stack & Component Purpose](#2-technology-stack--component-purpose)
- [System Architecture & Flowcharts](#3-system-architecture--flowcharts)
  - [High-Level Architecture](#31-high-level-architecture-how-everything-is-linked)
  - [Database & Cache Schema Design](#32-database--cache-schema-design-postgresql--redis)
  - [Directory Structure Topology](#33-directory-structure-topology)
  - [Execution Sequence Flows](#34-execution-sequence-flows)
- [Key Platform Features](#4-key-platform-features)
- [Local Setup & Installation](#5-local-setup--installation)
- [WebSocket STOMP API Documentation](#6-websocket-stomp-api-documentation)

## Deployment

This project is deployed on Render (backend) and Vercel (frontend). Below are concise steps and tips to deploy and configure both services.

- Backend (Render)
  1. Build a container or use the provided `backend/Dockerfile`.
  2. In the Render dashboard, create a new Web Service and connect your GitHub repo.
  3. Set the build command (if using Docker, Render will build the image). For Maven-based deploys without Docker use:

     ```bash
     ./mvnw clean package -DskipTests
     java -jar backend/target/*.jar
     ```

  4. Environment variables to set on Render:
     - `SPRING_PROFILES_ACTIVE=prod` (optional)
     - Database/Redis/Kafka connection strings (e.g. `SPRING_DATASOURCE_URL`, `SPRING_REDIS_URL`)
     - `JWT_SECRET` and any other secrets
  5. (Optional) Keep service warm: use the `/health` endpoint added to the app and an external monitor (UptimeRobot or GitHub Actions) to hit `/health` regularly.

- Frontend (Vercel)
  1. In Vercel, import the repo and set the project root to `frontend`.
  2. Set the environment variable `VITE_API_URL` to your backend base URL, for example:

     `https://game-platform-n1k1.onrender.com`

     Important: Vite injects env vars at build-time. Do NOT include brackets or markdown formatting.

  3. Build & Output settings (Vercel):
     - Framework Preset: `Vite` or `Other` with `npm run build`
     - Build Command: `npm run build`
     - Output Directory: `dist`

  4. Deploy. For preview deployments, consider adding `*.vercel.app` or preview domains to Render CORS if you test previews.

## 1. Project Executive Summary

This platform delivers an enterprise-grade multiplayer experience featuring real-time state synchronization, automated matchmaking, event-driven ELO rating adjustments, and database persistence.

### Key Architectural Highlights

- **Persistent STOMP WebSockets**: Global WebSocket context management enabling zero-latency game state synchronization and uninterrupted reconnection across route transitions.
- **Distributed Redis Matchmaking**: High-throughput queue management for instant player pairing with zero self-matching edge cases.
- **Event-Driven ELO Rating Engine**: Asynchronous Kafka event bus processing match outcomes and executing standard Elo rating adjustments (K=32 factor).
- **Multi-Layer Persistence**: PostgreSQL relational persistence combined with in-memory Redis caching for game state queries.
- **JWT Security**: State-less authentication integrated across REST endpoints and WebSocket handshake/channel interceptors.

---

## 2. Technology Stack & Component Purpose

| Layer / Technology          | Technology Used                         | Purpose & Function                                                                    |
| :-------------------------- | :-------------------------------------- | :------------------------------------------------------------------------------------ |
| **Frontend Framework**      | React 19, TypeScript, Vite              | Component-driven UI rendering, type safety, fast client bundling.                     |
| **Chess Engine & UI**       | chess.js, react-chessboard              | Headless rule validation, move evaluation, interactive board interface.               |
| **Audio Engine**            | Web Audio API (Native Synthesizer)      | Synthesized audio feedback for moves, captures, checks, and game over.                |
| **Real-Time Communication** | STOMP over SockJS / WebSockets          | Duplex frame-based communication for matchmaking and live game turns.                 |
| **Backend Framework**       | Spring Boot 3.4, Java 21                | Microservice backend framework with dependency injection and modular packages.        |
| **Security & Auth**         | Spring Security, JJWT (JWT)             | Stateless token authentication, BCrypt password hashing, channel interceptors.        |
| **In-Memory Cache & Queue** | Redis, Spring Data Redis                | High-speed matchmaking queue storage and fast session caching.                        |
| **Event Streaming**         | Apache Kafka, Spring Kafka              | Distributed message broker for asynchronous post-match processing and rating updates. |
| **Relational Database**     | PostgreSQL, Spring Data JPA / Hibernate | Persistent storage for user accounts, historical match logs, and ELO ratings.         |
| **Containerization**        | Docker, Docker Compose                  | Containerized orchestration for Redis, Kafka, Zookeeper, and PostgreSQL.              |

---

## 3. System Architecture & Flowcharts

### 3.1 High-Level Architecture (How Everything Is Linked)

```mermaid
flowchart TB
    subgraph Client ["Client Layer (Browser / React 19)"]
        UI["React SPA (App.tsx)"]
        WC["WebSocketContext / STOMP Client"]
        AUTH_JS["authService.ts (JWT Storage)"]
        GR["GameRoom.tsx"]
        DB_UI["Dashboard.tsx"]
    end

    subgraph Security ["Security & Auth Layer"]
        JWT_FLT["JwtAuthenticationFilter"]
        JWT_INT["JwtChannelInterceptor / JwtHandshakeInterceptor"]
        SEC_CFG["SecurityConfig (BCrypt + JWT Verification)"]
    end

    subgraph WebSocket ["Real-Time Messaging Layer (Spring WebSocket)"]
        WS_CFG["WebSocketConfig (/ws Endpoint)"]
        STOMP_BRK["STOMP Broker (/topic, /app)"]
        GAME_WS_CTRL["GameWebSocketController"]
        MATCH_WS_CTRL["MatchmakingWebSocketController"]
    end

    subgraph Application ["Application Core Services"]
        AUTH_SVC["AuthService"]
        GAME_SVC["GameService"]
        MATCH_SVC["MatchmakingService"]
        ENGINE["ChessGameEngine (chess.js / Java Engine)"]
    end

    subgraph Cache_Queue ["Cache & In-Memory Queue (Redis)"]
        REDIS_Q["RedisMatchmakingQueue"]
        REDIS_CFG["RedisConfig"]
    end

    subgraph Event_Bus ["Event-Driven Messaging (Apache Kafka)"]
        KAFKA_PROD["GameEventPublisher (Producer)"]
        KAFKA_TOPIC["Kafka Topic: game-ended-topic"]
        KAFKA_CONS["GameEventConsumer (Consumer)"]
    end

    subgraph Database ["Persistence Layer (PostgreSQL)"]
        JPA_REPO["JpaGameStateRepository"]
        USER_REPO["UserRepository"]
        PG_DB[("PostgreSQL Database")]
    end

    UI -->|1. Sign In / Register| AUTH_JS
    AUTH_JS -->|HTTP REST + Bearer Token| JWT_FLT
    JWT_FLT --> SEC_CFG
    SEC_CFG --> AUTH_SVC
    AUTH_SVC --> USER_REPO

    WC -->|2. WS Connect + JWT Header| WS_CFG
    WS_CFG --> JWT_INT
    JWT_INT --> STOMP_BRK

    DB_UI -->|3. Matchmaking Join| MATCH_WS_CTRL
    MATCH_WS_CTRL --> MATCH_SVC
    MATCH_SVC <--> REDIS_Q

    GR -->|4. Send Move / Resign / Draw| GAME_WS_CTRL
    GAME_WS_CTRL --> GAME_SVC
    GAME_SVC <--> ENGINE
    GAME_SVC --> JPA_REPO

    GAME_SVC -->|5. Game Ended Event| KAFKA_PROD
    KAFKA_PROD --> KAFKA_TOPIC
    KAFKA_TOPIC --> KAFKA_CONS
    KAFKA_CONS -->|6. Calculate Elo & Save| USER_REPO
    KAFKA_CONS --> JPA_REPO

    JPA_REPO --> PG_DB
    USER_REPO --> PG_DB
```

---

### 3.2 Database & Cache Schema Design (PostgreSQL + Redis)

```mermaid
erDiagram
    users ||--o{ game_sessions : "participates as player1"
    users ||--o{ game_sessions : "participates as player2"

    users {
        bigint id PK "SERIAL Primary Key"
        varchar username UK "Unique Username"
        varchar email UK "Unique Email Address"
        varchar password "BCrypt Hashed Password"
        integer elo_rating "Current ELO Rating (Default 1200)"
        timestamp created_at "Account Creation Timestamp"
    }

    game_sessions {
        uuid game_id PK "UUID Primary Key"
        varchar game_type "CHESS / Checkers"
        varchar status "WAITING_FOR_PLAYERS / IN_PROGRESS / WHITE_WON / BLACK_WON / DRAW / ABANDONED"
        varchar player1_username "White Player Username"
        varchar player2_username "Black Player Username"
        text board_state "Current FEN String"
        timestamp created_at "Match Start Time"
        timestamp updated_at "Last State Update"
    }

    REDIS_CACHE_STRUCTURES {
        string matchmaking_queue_CHESS "Redis Queue storing active waiting players"
        string game_session_UUID "In-memory cache for ultra-fast STOMP broadcast"
    }
```

---

### 3.3 Directory Structure Topology

```mermaid
graph TD
    subgraph Root ["game-platform Project Root"]
        FE["/frontend (Vite + React 19 + TypeScript)"]
        BE["/backend (Spring Boot 3.x + Java 21)"]
    end

    subgraph Frontend_Tree ["Frontend Structure"]
        FE --> FE_SRC["src/"]
        FE_SRC --> FE_CTX["context/ (WebSocketContext.tsx)"]
        FE_SRC --> FE_HOOKS["hooks/ (useWebSocket.ts)"]
        FE_SRC --> FE_PAGES["pages/ (Dashboard.tsx, GameRoom.tsx, Login.tsx)"]
        FE_SRC --> FE_SVC["services/ (socket.ts, authService.ts)"]
        FE_SRC --> FE_UTILS["utils/ (audio.ts)"]
    end

    subgraph Backend_Tree ["Backend Package Structure (com.rishabh.game_platform)"]
        BE --> BE_SRC["src/main/java/com/rishabh/game_platform/"]

        BE_SRC --> PKG_AUTH["auth/ (JWT Auth Controller, Service, UserEntity, UserRepository)"]
        BE_SRC --> PKG_GAME["game/"]
        BE_SRC --> PKG_MATCH["matchmaking/ (MatchmakingService, RedisMatchmakingQueue)"]
        BE_SRC --> PKG_SHARED["shared/ (Config: KafkaConfig, RedisConfig, SecurityConfig, WebSocketConfig)"]

        PKG_GAME --> GAME_API["api/ (GameController, GameWebSocketController, MoveRequest)"]
        PKG_GAME --> GAME_APP["application/ (GameService, GameEventPublisher, GameEventConsumer)"]
        PKG_GAME --> GAME_DOM["domain/ (GameSession, GameState, Move, Player, GameEndedEvent)"]
        PKG_GAME --> GAME_INFRA["infrastructure/ (JpaGameStateRepository, GameSessionEntity)"]
    end
```

---

### 3.4 Execution Sequence Flows

#### A. JWT Authentication Handshake Flow

```mermaid
sequenceDiagram
    autonumber
    participant User as React Frontend
    participant AuthCtrl as AuthController
    participant JwtService as JwtService
    participant WS as WebSocket Handshake
    participant Interceptor as JwtChannelInterceptor

    User->>AuthCtrl: POST /auth/api/login { username, password }
    AuthCtrl->>AuthCtrl: Verify credentials against PostgreSQL
    AuthCtrl->>JwtService: generateToken(username)
    JwtService-->>User: Returns AuthResponse { token: "eyJhbG..." }
    User->>User: Store token in localStorage

    User->>WS: CONNECT ws://localhost:8080/ws
    Note over User,WS: Pass connectHeaders: { Authorization: "Bearer eyJhbG..." }
    WS->>Interceptor: preSend(Message, Channel)
    Interceptor->>JwtService: extractUsername(token) & validateToken()
    JwtService-->>Interceptor: Valid Principal (Username)
    Interceptor-->>WS: Allow Connection Handshake
    WS-->>User: STOMP CONNECTED Frame
```

#### B. Redis Matchmaking Queue Engine Working Flow

```mermaid
sequenceDiagram
    autonumber
    participant P1 as Player 1 (React)
    participant P2 as Player 2 (React)
    participant STOMP as STOMP Broker (/ws)
    participant MatchSvc as MatchmakingService
    participant Redis as Redis Queue (RedisMatchmakingQueue)

    P1->>STOMP: Connect & Subscribe (/topic/match/p1)
    P2->>STOMP: Connect & Subscribe (/topic/match/p2)

    P1->>STOMP: SEND /app/matchmaking.join { playerId: "p1" }
    STOMP->>MatchSvc: processJoinRequest(p1)
    MatchSvc->>Redis: extractOpponent(CHESS, p1) -> None
    MatchSvc->>Redis: addPlayer(p1, CHESS) (Queued in Redis)

    P2->>STOMP: SEND /app/matchmaking.join { playerId: "p2" }
    STOMP->>MatchSvc: processJoinRequest(p2)
    MatchSvc->>Redis: extractOpponent(CHESS, p2) -> Returns p1
    MatchSvc->>MatchSvc: Create GameSession (UUID: game-123)

    MatchSvc-->>STOMP: convertAndSend(/topic/match/p1, { sessionId: "game-123" })
    MatchSvc-->>STOMP: convertAndSend(/topic/match/p2, { sessionId: "game-123" })

    STOMP-->>P1: Match Found! Navigate to /game/game-123
    STOMP-->>P2: Match Found! Navigate to /game/game-123
```

#### C. Apache Kafka Event-Driven ELO Pipeline Flow

```mermaid
sequenceDiagram
    autonumber
    participant P1 as Player 1 (White)
    participant STOMP as STOMP Broker
    participant GameSvc as GameService
    participant KafkaProd as GameEventPublisher (Producer)
    participant KafkaTopic as Kafka (game-ended-topic)
    participant KafkaCons as GameEventConsumer (Consumer)
    participant DB as PostgreSQL (UserRepository)

    P1->>STOMP: SEND /app/game/game-123/move { from: "f2", to: "f7" }
    STOMP->>GameSvc: executeMove(sessionId, player1, move)
    GameSvc->>GameSvc: Validate move with ChessGameEngine
    GameSvc->>GameSvc: Apply Move -> Checkmate Detected (Status: WHITE_WON)

    GameSvc-->>STOMP: convertAndSend(/topic/game/game-123, updatedSession)
    STOMP-->>P1: Broadcast State (Render Game Over Modal)

    GameSvc->>KafkaProd: publishGameEnded(GameEndedEvent)
    KafkaProd->>KafkaTopic: send("game-ended-topic", key: gameId, event)

    KafkaTopic-->>KafkaCons: @KafkaListener consumeGameEndedEvent(event)
    KafkaCons->>KafkaCons: Calculate ELO (K=32): Winner +16, Loser -16
    KafkaCons->>DB: Save updated UserEntity (ELO) & GameSessionEntity
```

---

## 4. Key Platform Features

### Real-Time Gameplay & State Engine

- **Authoritative Server Architecture**: Frontend sends move intents (`/app/game/{id}/move`); backend validates FEN positions and broadcasts authoritative game states.
- **Check & Checkmate Visual Indicators**: Highlights the checked King square in red (`rgba(239, 68, 68, 0.85)`) when `game.inCheck()` returns true.
- **Graveyard & Material Advantage Counter**: Calculates captured piece counts for White and Black, rendering captured SVG icons and material advantage scores (e.g., `+3`).
- **Native Synthesizer Sound Engine**: Built-in Web Audio API generator producing custom audio feedback for moves, captures, checks, and game-over events without external asset dependencies.
- **10-Minute Rapid Timers**: Synchronized active turn countdown clocks for both players.
- **Resignation & Draw Offers**: Dedicated UI actions publishing to `/app/game/{id}/resign` and `/app/game/{id}/draw`.
- **Game Over Modal**: Modal overlay displaying outcome, winner, end reason, and rating updates.

### Distributed Backend Infrastructure & Security

- **Redis Distributed Matchmaking**: Queue implementation pairing players atomically in O(1) time without self-matching edge cases.
- **Apache Kafka Asynchronous Event Streaming**: Match terminations publish `GameEndedEvent` to `game-ended-topic`.
- **Automated ELO Engine**: Asynchronous Kafka consumer implementing standard Elo rating calculations ($K = 32$):
  $$\text{Expected}_A = \frac{1}{1 + 10^{(R_B - R_A) / 400}}, \quad R'_A = R_A + K \times (S_A - \text{Expected}_A)$$
- **JWT Authentication Pipeline**: Secure authentication with custom Spring Security filters and STOMP channel interceptors validating Bearer tokens during WebSocket handshakes.
- **PostgreSQL Relational Storage**: JPA entities (`UserEntity`, `GameSessionEntity`) persisting accounts, rating histories, and finished match logs.

---

## 5. Local Setup & Installation

### Prerequisites

- Java 21 JDK
- Node.js 20+ & npm
- Docker & Docker Compose

### Step 1: Clone Repository

```bash
git clone https://github.com/2005rishabh/game-platform.git
cd game-platform
```

### Step 2: Start Infrastructure Services

Start PostgreSQL, Redis, Kafka, and Zookeeper via Docker Compose:

```bash
docker-compose up -d
```

### Step 3: Run Backend Service

```bash
cd backend
./mvnw spring-boot:run
```

The backend server runs on `http://localhost:8080`.

### Step 4: Run Frontend Application

```bash
cd ../frontend
npm install
npm run dev
```

The frontend dev server runs on `http://localhost:5173`.

---

## 6. WebSocket STOMP API Documentation

| Destination                    | Message Type | Payload Structure                                | Description                       |
| :----------------------------- | :----------- | :----------------------------------------------- | :-------------------------------- |
| `/app/matchmaking.join`        | SEND         | `{ "playerId": "string", "username": "string" }` | Enqueues player in Redis queue.   |
| `/app/matchmaking.cancel`      | SEND         | `{ "playerId": "string" }`                       | Removes player from queue.        |
| `/topic/match/{username}`      | SUBSCRIBE    | `{ "sessionId": "UUID" }`                        | Receive match notification.       |
| `/app/game/{sessionId}/move`   | SEND         | `{ "from": "e2", "to": "e4", "promotion": "q" }` | Submit move.                      |
| `/app/game/{sessionId}/resign` | SEND         | `{ "username": "string" }`                       | Resign current match.             |
| `/app/game/{sessionId}/draw`   | SEND         | `{ "username": "string" }`                       | Offer/accept draw.                |
| `/app/game/{sessionId}/state`  | SEND         | `{}`                                             | Request game state snapshot.      |
| `/topic/game/{sessionId}`      | SUBSCRIBE    | `GameSession Object`                             | Receive authoritative game state. |

---

Made with ❤️ by rishabh singh
