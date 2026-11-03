# IntelliDesk

**Back-end design for an IT help desk and ticketing system.**
INSY 4325 Group Project · Class diagram and database diagram

IT requests sent by email or in person are easy to lose, and nobody can see status or workload. IntelliDesk is one system to **submit, assign, track and resolve IT tickets**, plus a **self-help knowledge base**.

> **Status:** design phase. This repository documents the proposed back-end design (class and database diagrams). It covers the non-AI features of the front end: no AI classification, chatbot, AI recommendations or automatic assignment.

---

## Table of contents

- [User roles](#user-roles)
- [Architecture](#architecture)
- [Tech stack](#tech-stack)
- [Domain model](#domain-model)
- [Business rules](#business-rules)
- [Database design](#database-design)
- [Example workflow](#example-workflow)
- [Requirements traceability](#requirements-traceability)
- [Repository layout](#repository-layout)

---

## User roles

| Role | What they do |
| --- | --- |
| **Employee** | Submits tickets and screenshots, comments, tracks status, reads FAQs |
| **Technician** | Works assigned tickets, adds internal notes, resolves tickets |
| **Admin** | Assigns tickets, manages accounts, articles and settings, views reports |

## Architecture

| Layer | Responsibility |
| --- | --- |
| **Front end** (browser) | Shows screens and collects input. Sends requests with the session cookie. Never trusted for role or identity. |
| **Java application** | Controllers receive requests, services apply the business rules, domain classes model the data. |
| **Database** | 13 tables linked by PK/FK. Constraints protect the data and transactions keep related changes together. |

**Example: an employee clicks "Submit Ticket"**

1. The front end sends title, description, category and priority.
2. `TicketService.createTicket` checks the session and sets the submitter, the configured default status (initially `OPEN`), timestamps and `dueAt`.
3. The database saves a `tickets` row and a `ticket_history` row together, in one transaction.
4. The front end shows the new ticket.

## Tech stack

| Area | Choice |
| --- | --- |
| Language | Java (required) |
| Framework | Spring Boot (proposed) |
| Database | MySQL (proposed) |

## Domain model

### Core ticket classes

`User`, `Ticket`, `Category` and `PriorityPolicy` form the core.

- **submitter** (`User`, 1): the employee who opened the ticket. Always set.
- **assignee** (`User`, 0..1): the technician. Empty until an admin assigns one.
- **escalatedBy** (`User`, 0..1): who escalated the ticket, if anyone.
- **Category and priority** are chosen manually by the employee.
- **SLA deadline:** `dueAt = createdAt + resolutionHours`. Configurable; proposed `LOW` 48 h, `MEDIUM` 24 h, `HIGH` 4 h.

### Communication and history

Each ticket has many comments, attachments and history rows.

- **Comment:** `PUBLIC` (ticket owner + staff) or `INTERNAL` (staff only, filtered by the server).
- **Attachment:** screenshots, JPEG/PNG up to 10 MB, kept in protected storage. The table keeps only the metadata.
- **TicketHistory:** append-only, one row per changed field, with who and when.

### Accounts, verification and notifications

- **Sign-in:** `signIn` checks email and password. With verification enabled, an `AuthChallenge` (hashed code, 10-minute expiry) is created and `verifyCode` then creates the `UserSession`. If verification is disabled in settings, a correct password creates the session directly.
- **Authorization (always on):** the server reads the role from the account and checks role, ownership and assignment on every request.
- **Two kinds of email:** verification codes go through `EmailGateway` (no `Notification` row); ticket alerts are stored as `Notification` rows (`EMAIL` / `IN_APP`).

### Knowledge base and settings

- FAQs and troubleshooting articles share one class (`kind = FAQ` or `ARTICLE`).
- Employees see only published `PUBLIC` articles; `STAFF` articles are for technicians and admins.
- New articles start as `DRAFT`; admins publish them.
- Keyword search with a category filter; "Popular" uses `viewCount`.
- One settings row (`id = 1`) holds the admin defaults: default ticket status, two-factor on/off, session timeout, email notifications, ticket alerts.

### Services

| Service | Operations |
| --- | --- |
| `TicketService` | `createTicket`, `findTickets`, `getTicket`, `assign`, `changeStatus`, `changePriority`, `resolve`, `escalate` |
| `ReportingService` | `dashboard`, `dailyTicketVolume`, `technicianWorkload`, `exportTickets` |
| `UserService` | `createUser`, `updateUser`, `deactivateUser`, `findUsers` |
| `AuthService` | sign in, verify code, log out, `authorize` |
| `CommunicationService` | `addComment`, `upload` |
| `KnowledgeService` / `SettingsService` | manage articles, settings, categories and priorities |
| `NotificationService` | ticket alerts when enabled |

Full signatures are in the class diagrams from the design deck.

### Enumerations

| Enum | Values |
| --- | --- |
| `Role` | EMPLOYEE, TECHNICIAN, ADMIN |
| `TicketStatus` | OPEN, IN_PROGRESS, PENDING, RESOLVED |
| `CommentVisibility` | PUBLIC, INTERNAL |
| `ArticleKind` | FAQ, ARTICLE |
| `ArticleAudience` | PUBLIC, STAFF |
| `ArticleStatus` | DRAFT, PUBLISHED |
| `NotificationChannel` | EMAIL, IN_APP |
| `DeliveryStatus` | PENDING, SENT, FAILED |
| `NotificationEvent` | ASSIGNED, STATUS_CHANGED, ESCALATED, PUBLIC_REPLY |

The same allowed values are enforced in Java and by database `CHECK` constraints (or equivalent validation).

## Business rules

Proposed rules for the main ticket operations. Every operation checks the signed-in user's permissions, ticket changes are recorded in `TicketHistory`, and supported ticket events trigger notifications when enabled.

| Operation | Screen action | Rule |
| --- | --- | --- |
| `createTicket` | Submit Ticket | Sets submitter, default status, timestamps, `dueAt` |
| `assign` | Assigned technician | Admins only; must be an active technician |
| `changeStatus` | Save workflow update | Assigned technician or admin; allowed changes only |
| `resolve` | Resolve Ticket | Needs an assignee and resolution notes |
| `escalate` | Escalate | Saves who, when and why; flags for admin review |

`RESOLVED` is a final status.

**Reporting.** Every number is calculated on request from stored rows; there are no summary tables.

- **Status counts:** tickets counted by status.
- **Resolved today:** based on `resolved_at`, using the Chicago day.
- **Daily volume:** `created_at` grouped by day.
- **Technician load:** `loadPercent = active assigned tickets ÷ capacity × 100`. Active means not `RESOLVED`. Can exceed 100%.
- **CSV export:** admins only; contains no secrets or internal notes.

## Database design

13 tables: `users`, `tickets`, `categories`, `priority_policies`, `comments`, `attachments`, `ticket_history`, `auth_challenges`, `user_sessions`, `notifications`, `departments`, `knowledge_articles`, `system_settings`.

**Conventions**

- One generated ID per table, e.g. `tickets.ticket_id`.
- A foreign key stores the parent's PK, e.g. `tickets.category_id → categories.category_id`.
- `tickets` links to `users` twice: `submitted_by` (required, who reported it) and `assigned_to` (optional, the technician), plus optional `escalated_by`.
- Notation: **PK** primary key · **FK** foreign key · **UQ** unique · **NN** required · **NULL** optional.

**Data integrity**

- Foreign keys are enforced.
- Users are deactivated, not deleted.
- A ticket change and its history row commit in one transaction.
- Each comment, attachment and history row has a required `ticket_id`.

## Example workflow

One ticket from submission to resolution (planned workflow, illustrative Wi-Fi ticket).

| # | Step | Class · operation | Tables written / read |
| --- | --- | --- | --- |
| 1 | Employee submits Wi-Fi ticket | `TicketService.createTicket` | `tickets`, `ticket_history` |
| 2 | Uploads a screenshot | `CommunicationService.upload` | `attachments` |
| 3 | Admin assigns John Smith | `TicketService.assign` | `tickets.assigned_to`, `ticket_history`, `notifications` |
| 4 | Technician posts a public reply | `CommunicationService.addComment` | `comments`, `notifications` |
| 5 | Status → `IN_PROGRESS` | `TicketService.changeStatus` | `tickets.status`, `ticket_history`, `notifications` |
| 6 | Technician resolves with notes | `TicketService.resolve` | `tickets.status = RESOLVED`, `resolved_at`, `resolution_notes`; `ticket_history`; `STATUS_CHANGED` notification when enabled |
| 7 | Dashboard counts refresh | `ReportingService.dashboard` | reads `tickets` (nothing new stored) |

Every step checks permissions first. Notifications are created only when enabled in settings.

## Requirements traceability

| Requirement | Classes | Tables |
| --- | --- | --- |
| Sign in, verify, log out | `AuthService`, `AuthChallenge`, `UserSession` | `users`, `auth_challenges`, `user_sessions` |
| Submit tickets + screenshots | `TicketService`, `CommunicationService` | `tickets`, `attachments` |
| Comments, notes, activity | `Comment`, `TicketHistory` | `comments`, `ticket_history` |
| Assign, status, SLA, escalate, resolve | `TicketService`, `PriorityPolicy` | `tickets`, `priority_policies` |
| FAQ and knowledge base | `KnowledgeService`, `KnowledgeArticle` | `knowledge_articles` |
| Dashboards, workload, CSV export | `ReportingService` | `tickets`, `users` |
| Accounts and technicians | `UserService`, `User`, `Department` | `users`, `departments` |
| Settings and ticket alerts | `SettingsService`, `NotificationService` | `system_settings`, `notifications` |
| Role-based access | `AuthService.authorize` | `users.role`, `user_sessions` |

Java services decide what each user may do. The database keeps every record linked and consistent.


## Team
Rio, Keith|
Patel, Meet|
Cardell, Kendrick|
Nepal, Shaswat|
Jessie, Austin|
Shottenkirk, Ashton|
