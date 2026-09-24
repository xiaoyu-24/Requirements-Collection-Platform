# XQSJ · Requirements Collection and Management Platform

[简体中文](README.md) | **English**

A team collaboration platform that brings requirement submission, evaluation, system and version management, attachments, and progress notifications into one workspace. It supports self-hosted deployment and optional integration with an OpenAI-compatible AI service to extract requirement fields from text or screenshots.

## Features

- **Requirement management**: drafts, submissions, combined filters, urgency levels, progress updates, and detail views.
- **Work queue and overview**: track requirements for systems you own or support, and drill down from summaries into requirement lists.
- **Systems and versions**: manage owners, collaborators, and versions; assign, move, or remove requirements in batches, with version change history.
- **Attachments**: upload by selection, drag and drop, or clipboard paste. Preview images and PDFs directly; convert Word and Excel files with LibreOffice.
- **AI-assisted entry**: extract fields from text or screenshots, fill only empty fields, and review results before submitting. Store multiple AI configurations and activate one at a time.
- **Roles and accounts**: users, handlers, and administrators, with account enable/disable controls, password resets, and first-login password changes.
- **Notifications and dictionaries**: in-app activity updates, pending-work reminders, and configurable departments and requirement types.

Requirement statuses include pending evaluation, confirmed, in development, paused, completed, rejected, and closed.

| Role | Main capabilities |
| --- | --- |
| User `USER` | Submit and view personal requirements, edit or delete in permitted states, and view systems and versions |
| Handler `HANDLER` | View and process team requirements, use the work queue and overview, and manage systems and versions |
| Administrator `ADMIN` | Handler capabilities plus account, dictionary, and AI configuration management |

## Technology

| Layer | Technologies |
| --- | --- |
| Frontend | Vue 3.5, TypeScript 5.7, Vite 6, Ant Design Vue 4, Vue Router |
| Backend | Java 17, Spring Boot 3.3, Spring Data JPA |
| Database | MySQL; existing deployment documentation targets MySQL 5.7.32 compatibility |
| Database migrations | Flyway |
| Attachment previews | Native browser image/PDF previews and LibreOffice |
| Optional AI service | OpenAI-compatible Chat Completions API; screenshot recognition requires an image-capable model |

The application interface is currently in Chinese. This English README does not imply an English interface is available.

## Run locally

### 1. Prerequisites

- JDK 17 and Maven 3.6.3 or later.
- Node.js 22 LTS and npm.
- An accessible MySQL database. Validate migrations against a separate empty database before adopting another MySQL version.
- Optional: LibreOffice, required only for online Word/Excel previews.

Clone the repository and run the following steps from its root directory.

### 2. Create a database

Run the following with a MySQL account allowed to create databases, replacing the example password:

```sql
CREATE DATABASE requirements_platform
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'requirements_app'@'localhost' IDENTIFIED BY 'CHANGE_ME';
GRANT ALL PRIVILEGES ON requirements_platform.* TO 'requirements_app'@'localhost';
```

These privileges support local development and automatic migrations. Separate migration privileges from routine application access in production. For a remote database, adjust the MySQL account's Host to match the connection source.

### 3. Start the backend

PowerShell:

```powershell
$env:DB_URL='jdbc:mysql://localhost:3306/requirements_platform?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai'
$env:DB_USERNAME='requirements_app'
$env:DB_PASSWORD='CHANGE_ME'
cd backend
mvn spring-boot:run
```

Bash:

```bash
export DB_URL='jdbc:mysql://localhost:3306/requirements_platform?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai'
export DB_USERNAME='requirements_app'
export DB_PASSWORD='CHANGE_ME'
cd backend
mvn spring-boot:run
```

The backend listens on port `8080` by default and runs pending Flyway migrations at startup. The [health endpoint](http://localhost:8080/api/health) returns `{"status":"UP"}` when the application is running.

**First login:** on the first startup with an empty database, the application creates `admin` and prints a random initial password once in the startup log. Alternatively, set `ADMIN_INITIAL_PASSWORD` before starting the backend. Change the password when prompted at first login. Startup configuration never resets existing accounts.

This public distribution contains no organization-specific accounts. V22/V23 are retained as no-op migrations. It targets fresh databases and must not directly replace a deployment that used the original migrations. See the [public release notes](docs/public-release.md). Accounts created or reset through account management still use the initial password `888888` and must change it at first login.

### 4. Start the frontend

Open another terminal at the repository root:

```bash
cd frontend
npm ci
npm run dev
```

Open the URL printed by Vite, usually [http://localhost:5173](http://localhost:5173). Vite proxies `/api` to `http://localhost:8080`; no separate frontend API URL is required.

After signing in, an administrator can configure departments and requirement types and create accounts. Administrators or handlers can then create systems and versions so the team can start submitting and processing requirements.

## Configuration

The backend reads environment variables. Spring Boot does not automatically load a standalone `.env` file; the deployment template injects it through systemd's `EnvironmentFile`.

| Environment variable | Default | Purpose |
| --- | --- | --- |
| `DB_URL` | Local `requirements_platform` database | JDBC URL; see the full example above |
| `DB_USERNAME` | `requirements_app` | Database account |
| `DB_PASSWORD` | Empty | Database password |
| `ADMIN_INITIAL_PASSWORD` | Empty (randomly generated) | Initial administrator password, used only with an empty users table |
| `ATTACHMENTS_ROOT` | `./uploads` | Original attachment directory |
| `ATTACHMENTS_PREVIEW_ROOT` | `./previews` | Preview cache directory |
| `ATTACHMENTS_MINIMUM_FREE_SPACE_BYTES` | `0` | Free disk space reserved during uploads |
| `LIBREOFFICE_EXECUTABLE` | `libreoffice` | Path to the LibreOffice executable |
| `ATTACHMENTS_PREVIEW_TIMEOUT_SECONDS` | `60` | Timeout per Office conversion |
| `ATTACHMENTS_PREVIEW_CONCURRENCY` | `2` | Concurrent Office conversions |
| `AI_REQUEST_TIMEOUT_SECONDS` | `30` | AI text request timeout |
| `AI_IMAGE_REQUEST_TIMEOUT_SECONDS` | `60` | AI image request timeout |
| `NOTIFICATIONS_REMINDER_CRON` | `0 0 9 * * *` | Pending-work reminder schedule |
| `NOTIFICATIONS_STALE_AFTER_DAYS` | `7` | Days without updates before a stale-work reminder |

Relative directories are resolved from the backend process's working directory. Use absolute paths in production.

Administrators configure the AI endpoint, model, and API key in the AI settings page. Manual entry works without AI. When AI is used, the submitted text or image and relevant dictionaries, system names, and version information are sent to the configured provider. API keys are currently stored as plaintext in the database; the API returns only a masked value.

## Build and verify

From the repository root:

```bash
# Backend tests and package
mvn -f backend/pom.xml test
mvn -f backend/pom.xml package

# Frontend dependencies, type checking, and build
npm --prefix frontend ci
npm --prefix frontend run build
```

Outputs are `backend/target/requirements-platform-0.1.0-SNAPSHOT.jar` and `frontend/dist/`. Current database-related automated tests use H2 with Flyway disabled; they do not replace migration validation against an empty MySQL database.

## Deployment

Run the backend JAR, serve the frontend build through Nginx, proxy `/api` on the same origin to the backend, and configure an SPA fallback to `index.html`. Configure HTTPS in production and install LibreOffice on the preview server. Back up the database and original attachments together; preview caches can be regenerated.

The following operational documents are in Chinese:

- [Deployment and environment configuration](docs/deployment.md)
- [Nginx, systemd, backup, and health-check templates](deploy/centos7/)
- [Backup and recovery](docs/backup-and-recovery.md)
- [GitHub publishing steps](docs/publishing.md)

The templates in `deploy/centos7/` reflect an existing deployment environment. Adjust installation steps and paths for other distributions.

## Repository layout

```text
backend/                  Spring Boot backend
  src/main/java/          Application APIs and services
  src/main/resources/     Configuration and Flyway migrations
  src/test/               Backend automated tests
frontend/                 Vue frontend
  src/components/         Pages and components
  src/composables/        Shared interaction and state logic
deploy/centos7/            Deployment templates and operational scripts
docs/                     Deployment, backup, and public release documentation
```

## Contributing and license

Issues and suggestions are welcome. Run relevant backend tests and the frontend build before submitting changes, and use fictional data in examples.

The repository does not currently include a `LICENSE` file. Public source availability does not grant an open-source license; the maintainer must specify permission to copy, modify, and distribute the project.
