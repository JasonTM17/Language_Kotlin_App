# Diagrams

Mermaid sources for the views that are easier to read than to describe. GitHub
and most Markdown viewers render these inline.

## System

```mermaid
flowchart TB
    subgraph Android["Android app"]
        UI[Compose UI]
        VM[ViewModels]
        REPO[Repositories]
        ROOM[(Room)]
        DS[(DataStore)]
        OUT[(Outbox)]
        WM[WorkManager]
        UI --> VM --> REPO
        REPO --> ROOM
        REPO --> DS
        REPO --> OUT
        WM --> REPO
    end

    subgraph Backend["Ktor backend"]
        R[Routes]
        S[Services]
        ORM[Exposed]
        GW[AI gateway]
        R --> S --> ORM
        S --> GW
    end

    DB[(MySQL 8)]
    AI[AI provider]

    REPO -->|HTTPS + JWT| R
    ORM --> DB
    GW -->|server-side key| AI
```

## Clean architecture inside the app

```mermaid
flowchart LR
    subgraph ui["ui"]
        SC[Screens]
        VMC[ViewModels]
        CP[Components]
    end
    subgraph domain["domain"]
        M[Models]
        RI[Repository interfaces]
        UC[Use cases]
        VAL[Validation]
        SRS[SRS]
    end
    subgraph data["data"]
        REM[Retrofit APIs + DTOs]
        LOC[Room DAOs + entities]
        RIMPL[Repository impls]
        DSS[DataStore]
        WK[Workers]
    end

    SC --> VMC --> UC
    VMC --> RI
    UC --> RI
    RIMPL -.implements.-> RI
    RIMPL --> REM
    RIMPL --> LOC
    RIMPL --> DSS
    WK --> RIMPL
```

Dependencies point inward: `ui` and `data` know `domain`; `domain` knows neither.

## Auth and token refresh

```mermaid
sequenceDiagram
    participant App
    participant OkHttp
    participant Auth as TokenAuthenticator
    participant API as Backend

    App->>OkHttp: request
    OkHttp->>API: Authorization: Bearer <access>
    API-->>OkHttp: 401
    OkHttp->>Auth: authenticate()
    Note over Auth: mutex — one refresh for concurrent 401s
    Auth->>API: POST /auth/refresh
    alt refresh succeeds
        API-->>Auth: rotated token pair
        Auth->>OkHttp: retry with new token
        OkHttp->>API: request
        API-->>App: 200
    else refresh fails
        Auth->>Auth: clear session
        Auth-->>OkHttp: give up
        OkHttp-->>App: 401
    end
```

## AI request path

```mermaid
sequenceDiagram
    participant App
    participant API as Backend
    participant PB as PromptBuilder
    participant GW as AI gateway
    participant P as Provider

    App->>API: POST /ai/chat {mode, message, contextIds}
    Note over App,API: no system instructions from the client
    API->>API: rate limit check per user
    API->>PB: build system prompt
    PB->>PB: profile, language, lesson, weak topics, summary
    PB-->>API: system prompt + bounded history
    API->>GW: chat(messages)
    GW->>P: provider call
    P-->>GW: reply
    GW-->>API: reply
    API->>API: persist messages, summarise if needed
    API-->>App: {conversationId, reply, mode}
```

## Offline event sync

```mermaid
sequenceDiagram
    participant VM as ViewModel
    participant OUT as Outbox (Room)
    participant WM as SyncWorker
    participant API as Backend

    VM->>OUT: enqueue(event, clientOperationId)
    VM->>WM: enqueue unique work
    WM->>OUT: pending batch
    loop each pending op
        WM->>API: POST /progress/events {clientOperationId}
        alt delivered
            API-->>WM: {eventId, created: true}
            WM->>OUT: mark synced
        else already recorded
            API-->>WM: {eventId, created: false}
            WM->>OUT: mark synced
        else failed
            WM->>OUT: attempts++, retry later
        end
    end
    Note over OUT: after 5 attempts an op is dead-lettered
```

## Progress aggregation

```mermaid
flowchart LR
    UP[(user_progress<br/>event log)]
    LS[(learning_streaks<br/>one row per day)]
    QZ[(quiz_attempts)]
    UM[(user_mistakes)]
    UVP[(user_vocabulary_progress)]
    AC[(ai_conversations)]

    UP --> AGG[ProgressRepository.summary]
    LS --> AGG
    QZ --> AGG
    UM --> AGG
    UVP --> AGG
    AC --> AGG
    AGG --> DTO[ProgressSummaryDto]
    DTO --> PS[Progress screen]
    DTO --> HM[Home dashboard]
```

The streak is derived from `learning_streaks`, never stored as a counter, and both
the Progress screen and the home dashboard read the same endpoint so they cannot
disagree.
