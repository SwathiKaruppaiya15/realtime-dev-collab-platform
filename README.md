# CodeRoom — Real-Time Collaborative Code Editor

A full-stack collaborative code editor with real-time sync, multi-language code execution, AI code generation, room-based access control, and live chat.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Frontend | React 18, Vite, Monaco Editor, SockJS + STOMP |
| Backend | Spring Boot 3.2, Java 17 |
| Database | PostgreSQL |
| Real-time | WebSocket (STOMP) |
| Code Execution | Docker (isolated containers) |
| AI | OpenAI GPT-4o-mini |
| Auth | JWT (jjwt) |

---

## Features

- **Real-time collaboration** — multiple users edit the same file simultaneously with live cursor tracking
- **Role-based access** — ADMIN / EDITOR / VIEWER roles per room
- **Virtual file system** — create, rename, delete files and folders inside each room
- **Multi-language code execution** — Java, Python, JavaScript, C, C++ via Docker containers
- **HTML/CSS preview** — live iframe preview for HTML files
- **AI Code Assistant** — generate or improve code using GPT-4o-mini
- **Room chat** — real-time chat per room via WebSocket
- **Invite system** — invite users by email with notification bell
- **Export** — download all room files as a ZIP
- **Interactive terminal** — bash/cmd terminal per room session

---

## Project Structure

```
collab-code/
├── backend/                        # Spring Boot application
│   └── src/main/java/com/example/backend/
│       ├── config/                 # Security, JWT, WebSocket, CORS
│       ├── controller/             # REST + WebSocket controllers
│       ├── dto/                    # Request / Response DTOs
│       ├── entity/                 # JPA entities
│       ├── repository/             # Spring Data repositories
│       └── service/                # Business logic
│
├── frontend/                       # React + Vite application
│   └── src/
│       ├── api/                    # Axios instance + interceptors
│       ├── components/             # Reusable UI components
│       ├── hooks/                  # WebSocket hooks
│       └── pages/                  # Route pages
│
└── docker/                         # Dockerfiles for code runners
    ├── build-images.bat
    └── build-images.sh
```

---

## Prerequisites

- **Java 17+**
- **Maven 3.8+**
- **Node.js 18+**
- **PostgreSQL 14+**
- **Docker Desktop** (for code execution)

---

## Setup & Run

### 1. Database

Create a PostgreSQL database:

```sql
CREATE DATABASE mydb;
```

### 2. Backend

Configure `backend/src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/mydb
spring.datasource.username=postgres
spring.datasource.password=your_password

openai.api.key=sk-...your-key-here...
```

Start the backend:

```bash
cd backend
mvn spring-boot:run
```

Backend runs on **http://localhost:8080**

### 3. Frontend

```bash
cd frontend
npm install
npm run dev
```

Frontend runs on **http://localhost:5173**

### 4. Docker Images (for code execution)

Pull the required Docker images once:

```bash
# Windows
cd docker && build-images.bat

# Linux / Mac
cd docker && bash build-images.sh
```

Or pull manually:

```bash
docker pull amazoncorretto:17
docker pull python:3.11-slim
docker pull node:18-slim
docker pull gcc:latest
```

---

## API Reference

### Auth
| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/auth/register` | Register new user |
| POST | `/api/auth/login` | Login, returns JWT |

### Rooms
| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/rooms/create` | Create room |
| POST | `/api/rooms/join` | Join by code |
| GET | `/api/rooms/my` | List my rooms |
| GET | `/api/rooms/{id}/members` | List members |
| PUT | `/api/rooms/{id}/role` | Change member role (ADMIN) |
| DELETE | `/api/rooms/{id}/leave` | Leave room |
| DELETE | `/api/rooms/{id}` | Delete room (ADMIN) |
| DELETE | `/api/rooms/{id}/member/{userId}` | Remove member (ADMIN) |

### Files
| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/files/create` | Create file or folder |
| PUT | `/api/files/update/{id}` | Update content or rename |
| DELETE | `/api/files/delete/{id}` | Delete (ADMIN only) |
| GET | `/api/files/{roomId}` | Get file tree |

### Code Execution
| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/run` | Execute code in Docker |

Request body:
```json
{
  "language": "java",
  "code": "...",
  "fileName": "Main.java",
  "roomId": 1
}
```

### AI
| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/ai/generate` | Generate new code |
| POST | `/api/ai/improve` | Improve existing file |

### Notifications
| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/invite/send` | Send room invite |
| GET | `/api/notifications` | Get my notifications |
| POST | `/api/invite/respond` | Accept or reject invite |

### Chat
| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/chat/{roomId}` | Load chat history |
| WS | `/app/chat.send` | Send message (WebSocket) |

### Export
| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/export/{roomId}` | Download room as ZIP |

---

## WebSocket Topics

| Destination | Direction | Purpose |
|---|---|---|
| `/app/code.change` | Client → Server | Broadcast code edit |
| `/topic/code/{roomId}` | Server → Client | Receive code edit |
| `/app/cursor.move` | Client → Server | Send cursor position |
| `/topic/cursor/{roomId}` | Server → Client | Receive cursor positions |
| `/app/chat.send` | Client → Server | Send chat message |
| `/topic/room/{roomId}` | Server → Client | Receive chat messages |
| `/app/terminal.command` | Client → Server | Run terminal command |
| `/topic/terminal/{sessionId}` | Server → Client | Stream terminal output |

---

## Roles & Permissions

| Action | ADMIN | EDITOR | VIEWER |
|---|---|---|---|
| View files | ✓ | ✓ | ✓ |
| Edit files | ✓ | ✓ | ✗ |
| Create files | ✓ | ✓ | ✗ |
| Delete files | ✓ | ✗ | ✗ |
| Run code | ✓ | ✓ | ✗ |
| Invite users | ✓ | ✗ | ✗ |
| Change roles | ✓ | ✗ | ✗ |
| Remove members | ✓ | ✗ | ✗ |
| Delete room | ✓ | ✗ | ✗ |

---

## Environment Variables

| Variable | Where | Description |
|---|---|---|
| `openai.api.key` | `application.properties` | OpenAI API key |
| `jwt.secret` | `application.properties` | JWT signing secret (min 32 chars) |
| `spring.datasource.password` | `application.properties` | PostgreSQL password |

> Never commit real API keys. Use `application-local.properties` (gitignored) for secrets.

---

## Supported Languages (Code Execution)

| Language | Extension | Docker Image |
|---|---|---|
| Java | `.java` | `amazoncorretto:17` |
| Python | `.py` | `python:3.11-slim` |
| JavaScript | `.js` | `node:18-slim` |
| C++ | `.cpp` | `gcc:latest` |
| C | `.c` | `gcc:latest` |
| HTML/CSS | `.html` `.css` | Browser iframe (no Docker) |
