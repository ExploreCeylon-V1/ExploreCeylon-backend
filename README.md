# 🏝️ ExploreCeylon — Backend API

**Spring Boot 3 REST API for Sri Lanka's AI-Powered Tourism Platform**

Group 4 · COM3b33 · University of Ruhuna · 2026

<p>
  <img alt="Java" src="https://img.shields.io/badge/Java-17-orange?logo=openjdk&logoColor=white">
  <img alt="Spring Boot" src="https://img.shields.io/badge/Spring%20Boot-3.5.14-brightgreen?logo=springboot&logoColor=white">
  <img alt="PostgreSQL" src="https://img.shields.io/badge/PostgreSQL-15%2B-blue?logo=postgresql&logoColor=white">
  <img alt="Maven" src="https://img.shields.io/badge/Build-Maven-C71A36?logo=apachemaven&logoColor=white">
  <img alt="License" src="https://img.shields.io/badge/License-MIT-yellow.svg">
</p>

---

## 📑 Table of Contents

- [Project Overview](#-project-overview)
- [Tech Stack](#-tech-stack)
- [Project Structure](#-project-structure)
- [Getting Started](#-getting-started)
- [Environment Variables / application.properties](#-environment-variables--applicationproperties)
- [API Endpoints](#-api-endpoints)
- [Database](#-database)
- [Payment System (PayHere)](#-payment-system-payhere)
- [License](#-license)

---

## 📖 Project Overview

ExploreCeylon's backend is a Spring Boot 3 REST API that powers an AI-assisted trip-planning platform for travelers visiting Sri Lanka. It exposes JWT-secured endpoints consumed by a traveler-facing web app and an admin dashboard, and integrates with an external AI microservice for itinerary generation.

**Major modules found in the codebase:**

- 🔐 **Auth & Users** — registration/login, Google sign-in, refresh tokens, password reset, email/phone verification, session (login history) management
- 🗺️ **Trip Planning** — multi-day trips, day items, AI-generated itineraries (via external AI service), shareable trip links
- 💰 **Budget Tracking** — per-trip budgets, category budgets, line items, summaries
- 🏛️ **Destinations & Hidden Gems** — curated destinations plus user-submitted "hidden gems" with admin approval workflow, reviews for both
- 🚙 **Vehicle Rentals** — local vehicle listings (tuk-tuks, cars, vans), bookings, availability, reviews
- 🧭 **Tour Guides** — guide directory, bookings, availability, reviews
- 💳 **Payments** — PayHere-integrated payment flows for guide and vehicle bookings (advance/final split, commission)
- 🏨 **Hotel Search** — reactive proxy to a RapidAPI hotel-search provider
- 📅 **Events** — seasonal/cultural event calendar with trip-sync lookups
- 💬 **Live Chat** — WebSocket/STOMP chat between travelers and admin support
- 🔔 **Notifications** — in-app notifications with a scheduled booking-reminder job
- 📊 **Admin Console** — dashboard analytics, revenue, user management, bulk moderation, review moderation, contact-message inbox
- 📁 **File Uploads** — image uploads to AWS S3 (profile photos, gem photos)

---

## 🛠️ Tech Stack

| Category | Technology | Version |
|---|---|---|
| Language | Java | 17 |
| Framework | Spring Boot (parent) | 3.5.14 |
| Web | Spring Boot Starter Web | (managed by parent) |
| Reactive HTTP | Spring Boot Starter WebFlux (`WebClient`) | (managed by parent) |
| Realtime | Spring Boot Starter WebSocket (STOMP/SockJS) | (managed by parent) |
| Data Access | Spring Boot Starter Data JPA | (managed by parent) |
| Security | Spring Boot Starter Security | (managed by parent) |
| Validation | Spring Boot Starter Validation | (managed by parent) |
| Email | Spring Boot Starter Mail (Gmail SMTP) | (managed by parent) |
| Database Driver | PostgreSQL JDBC Driver | (managed by parent, runtime) |
| Auth Tokens | `io.jsonwebtoken` (jjwt-api / jjwt-impl / jjwt-jackson) | 0.11.5 |
| Cloud Storage | AWS SDK v2 — `software.amazon.awssdk:s3` | 2.25.40 |
| JSON | Jackson Databind | (managed by parent) |
| Boilerplate | Lombok | (managed by parent, optional) |
| Testing | Spring Boot Starter Test + Spring Security Test | (managed by parent, test scope) |
| Test DB | H2 (in-memory) | (managed by parent, test scope) |
| Build Tool | Maven (via `mvnw` wrapper) | — |

---

## 📂 Project Structure

```
src/main/java/com/exploreceylon/backend/
├── BackendApplication.java          # @SpringBootApplication, @EnableScheduling
│
├── config/
│   ├── ChatChannelInterceptor.java  # STOMP channel auth interceptor
│   ├── ChatHandshakeHandler.java    # WS handshake principal resolution
│   ├── ChatHandshakeInterceptor.java
│   ├── CorsConfig.java
│   ├── JwtAuthFilter.java           # JWT filter in the security chain
│   ├── JwtService.java              # token issue/parse/validate
│   ├── S3Config.java                # AWS S3 client bean
│   ├── SecurityConfig.java          # HttpSecurity rules, CORS, auth provider
│   ├── UserDetailsServiceImpl.java
│   ├── WebClientConfig.java         # WebClient bean for external APIs
│   └── WebSocketConfig.java         # STOMP endpoint /ws-chat
│
├── controller/
│   ├── AdminAnalyticsController.java
│   ├── AdminController.java
│   ├── AdminReviewController.java
│   ├── AdminVehicleController.java
│   ├── AuthController.java
│   ├── BudgetController.java
│   ├── ChatController.java
│   ├── ContactMessageController.java
│   ├── DestinationController.java
│   ├── DestinationReviewController.java
│   ├── EventController.java
│   ├── Gemreviewcontroller.java     # hidden-gem reviews
│   ├── GuidePaymentController.java
│   ├── HiddenGemController.java
│   ├── HotelController.java
│   ├── LocalVehicleController.java
│   ├── NotificationController.java
│   ├── TourGuideController.java
│   ├── TripController.java
│   ├── UploadController.java
│   ├── UserController.java
│   ├── VehicleBookingController.java
│   └── VehiclePaymentController.java
│
├── dto/
│   ├── admin/        # AdminBookingResponse, AnalyticsResponse, DashboardStatsResponse, ...
│   ├── auth/         # LoginRequest, RegisterRequest, AuthResponse, GoogleLoginRequest, ...
│   ├── budget/        # CreateBudgetRequest, BudgetResponse, BudgetItemResponse, ...
│   ├── chat/          # ChatConversationResponse, ChatMessageRequest/Response
│   ├── destination/   # CreateDestinationRequest, DestinationResponse, DestinationReviewResponse, ...
│   ├── event/         # CreateEventRequest, EventResponse
│   ├── gem/           # CreateGemRequest, GemResponse
│   ├── guide/         # BookGuideRequest, GuideResponse, ReviewResponse, ...
│   ├── hotel/         # HotelSearchRequest/Response, HotelResult
│   ├── notification/  # NotificationResponse
│   ├── payment/       # PayHereInitRequest/Response, PayHereNotifyRequest, PaymentResponse
│   ├── review/        # Createreviewrequest, Reviewresponse (vehicle reviews)
│   ├── trip/          # CreateTripRequest, TripResponse, TripDayResponse, GenerateAiTripRequest, ...
│   ├── vehicle/       # BookVehicleRequest, LocalVehicleRequest/Response, VehicleBookingResponse, ...
│   ├── ContactMessageRequest/Response.java
│   └── UploadResponse.java
│
├── exception/
│   ├── GlobalExceptionHandler.java
│   └── RateLimitException.java
│
├── model/                            # 30 @Entity classes — see Database section
│
├── repository/                       # Spring Data JPA repositories (+ 2 custom query repos)
│
├── service/
│   ├── AdminAnalyticsService / AdminReviewService / AdminService
│   ├── AiService                     # calls external AI itinerary microservice
│   ├── AuthService / RefreshTokenService / VerificationService
│   ├── BookingSchedulerService       # @Scheduled reminder jobs
│   ├── BudgetService
│   ├── ChatService
│   ├── CodeSender / CompositeCodeSender / LogCodeSender  # OTP delivery strategy
│   ├── ContactMessageService
│   ├── DestinationService / DestinationReviewService
│   ├── EmailSenderService / EmailTemplates
│   ├── EventService
│   ├── Gemreviewservice
│   ├── GuidePaymentService / VehiclePaymentService / PayHereService
│   ├── HiddenGemService
│   ├── HotelApiService                # RapidAPI hotel search proxy
│   ├── ItineraryAssemblyService
│   ├── LocalVehicleService / VehicleBookingService
│   ├── LoginHistoryService
│   ├── NotificationService
│   ├── S3Service
│   ├── SmsSenderService                # Notify.lk SMS
│   └── TourGuideService / TripService
│
├── specification/
│   └── UserSpecifications.java        # JPA Specification filters for admin user search
│
└── util/
    ├── DeviceInfoUtils.java
    └── GeoUtils.java
```

---

## 🚀 Getting Started

### Prerequisites

- **Java 17** (JDK)
- **Maven** (the bundled `mvnw` / `mvnw.cmd` wrapper is sufficient — no local Maven install required)
- **PostgreSQL 15+** running locally or accessible remotely
- (Optional) **ngrok** or another tunnel, if you need PayHere's IPN webhook to reach your local machine

### 1. Clone the repository

```bash
git clone <repository-url>
cd ExploreCeylon-backend
```

### 2. Set up the database

```sql
CREATE DATABASE explore_ceylon_db;
```

The schema is auto-managed by Hibernate (`spring.jpa.hibernate.ddl-auto=update`), and `src/main/resources/data.sql` seeds sample destinations, hidden gems, tour guides, vehicles, events, locations, and demo users on every startup (idempotent inserts).

### 3. Configure `application.properties`

Copy the template below into `src/main/resources/application.properties` and fill in your own credentials (see [Environment Variables](#-environment-variables--applicationproperties) for the full list).

### 4. Run the application

```bash
# Windows
mvnw.cmd spring-boot:run

# macOS / Linux
./mvnw spring-boot:run
```

The API starts on **`http://localhost:8080`**.

---

## ⚙️ Environment Variables / application.properties

> ⚠️ **Security note:** the current `application.properties` in this repository has real credentials committed as plaintext literals (DB password, JWT secret, SMTP password, SMS API key, AWS keys, PayHere merchant secret). None of them use `${ENV_VAR}` placeholders. Treat the values below as **illustrative examples only** — rotate and externalize all secrets (env vars / a secrets manager) before any real deployment.

| Property | Required | Example value | Notes |
|---|:---:|---|---|
| `spring.datasource.url` | ✅ | `jdbc:postgresql://localhost:5432/explore_ceylon_db` | PostgreSQL connection URL |
| `spring.datasource.username` | ✅ | `postgres` | DB user |
| `spring.datasource.password` | ✅ | `••••••••` | DB password |
| `spring.datasource.driver-class-name` | ✅ | `org.postgresql.Driver` | |
| `spring.jpa.hibernate.ddl-auto` | ✅ | `update` | Auto schema migration |
| `spring.jpa.show-sql` | ⬜ | `true` | SQL logging |
| `spring.jpa.properties.hibernate.format_sql` | ⬜ | `true` | Pretty-print SQL logs |
| `spring.sql.init.mode` | ✅ | `always` | Runs `data.sql` on every boot |
| `spring.jpa.defer-datasource-initialization` | ✅ | `true` | Ensures Hibernate creates tables before `data.sql` runs |
| `rapidapi.key` | ✅ | `••••••••` | RapidAPI subscription key |
| `rapidapi.hotel.host` | ✅ | `booking-com.p.rapidapi.com` | Hotel search provider host |
| `rapidapi.hotel.baseurl` | ✅ | `https://booking-com.p.rapidapi.com` | |
| `rapidapi.vehicle.baseurl` | ✅ | `https://local-vehicles-db.p.rapidapi.com` | |
| `rapidapi.vehicle.host` | ✅ | `local-vehicles-db.p.rapidapi.com` | |
| `jwt.secret` | ✅ | `••••••••` | HMAC signing key — use a long random secret |
| `jwt.expiration` | ✅ | `900000` | Access token TTL (ms) — 15 min |
| `jwt.refresh-expiration` | ✅ | `604800000` | Refresh token TTL (ms) — 7 days |
| `ai.service.url` | ✅ | `http://localhost:8000` | Base URL of the external AI itinerary microservice |
| `app.dev-mode` | ⬜ | `false` | Gates dev-only endpoints (e.g. manual reminder trigger) |
| `app.verification.cooldown-seconds` | ⬜ | `60` | OTP resend cooldown |
| `app.verification.max-sends-per-hour` | ⬜ | `5` | OTP rate limit |
| `app.verification.max-attempts` | ⬜ | `5` | OTP verify attempt limit |
| `app.verification.lock-minutes` | ⬜ | `30` | Lockout duration after max attempts |
| `spring.mail.host` | ✅ | `smtp.gmail.com` | Gmail SMTP host |
| `spring.mail.port` | ✅ | `587` | |
| `spring.mail.username` | ✅ | `you@gmail.com` | Gmail account |
| `spring.mail.password` | ✅ | `••••••••` | Gmail **app password**, not your login password |
| `spring.mail.properties.mail.smtp.auth` | ✅ | `true` | |
| `spring.mail.properties.mail.smtp.starttls.enable` | ✅ | `true` | |
| `spring.mail.properties.mail.smtp.connectiontimeout` | ⬜ | `5000` | |
| `spring.mail.properties.mail.smtp.timeout` | ⬜ | `5000` | |
| `spring.mail.properties.mail.smtp.writetimeout` | ⬜ | `5000` | |
| `app.mail.from` | ✅ | `noreply@yourdomain.com` | Sender address for outbound email |
| `app.mail.from-name` | ⬜ | `Explore Ceylon` | Display name for outbound email |
| `notifylk.user-id` | ✅ | `••••••` | Notify.lk SMS user ID |
| `notifylk.api-key` | ✅ | `••••••••` | Notify.lk SMS API key |
| `notifylk.sender-id` | ⬜ | `NotifyDEMO` | SMS sender ID |
| `notifylk.base-url` | ✅ | `https://app.notify.lk/api/v1/send` | |
| `spring.codec.max-in-memory-size` | ⬜ | `10MB` | WebClient/WebFlux buffer limit |
| `aws.s3.access-key` | ✅ | `••••••••` | AWS IAM access key |
| `aws.s3.secret-key` | ✅ | `••••••••` | AWS IAM secret key |
| `aws.s3.region` | ✅ | `eu-north-1` | |
| `aws.s3.bucket-name` | ✅ | `your-s3-bucket` | |
| `aws.s3.base-url` | ✅ | `https://your-bucket.s3.eu-north-1.amazonaws.com` | Public base URL for stored images |
| `payhere.merchant.id` | ✅ | `••••••••` | PayHere merchant ID |
| `payhere.merchant.secret` | ✅ | `••••••••` | PayHere merchant secret (used for MD5 signature checks) |
| `payhere.sandbox` | ✅ | `true` | `true` → sandbox checkout, `false` → live PayHere |
| `app.base.url` | ✅ | `http://localhost:8080` | Base URL this backend is reachable at — used to build the PayHere `notify_url` |
| `frontend.base.url` | ✅ | `http://localhost:5173` | Frontend base URL — used to build PayHere `return_url` / `cancel_url` |

---

## 🔌 API Endpoints

All endpoints are prefixed `/api/v1` unless noted. 🔓 = public · 🔐 = authenticated (any logged-in user) · 🛡️ = ADMIN role required.

### Auth — `/api/v1/auth`
| Method | Path | Access |
|---|---|:---:|
| POST | `/auth/register` | 🔓 |
| POST | `/auth/login` | 🔓 |
| POST | `/auth/google` | 🔓 |
| POST | `/auth/refresh-token` | 🔓 |
| POST | `/auth/logout` | 🔓 |
| POST | `/auth/forgot-password` | 🔓 |
| POST | `/auth/verify-reset-code` | 🔓 |
| POST | `/auth/reset-password` | 🔓 |
| GET | `/auth/me` | 🔓* |
| POST | `/auth/change-password` | 🔐 |

\* path is `permitAll` by config but the handler requires an authenticated principal in practice.

### Users — `/api/v1/users`
| Method | Path | Access |
|---|---|:---:|
| GET | `/users/count` | 🔓 |
| GET | `/users/me` | 🔐 |
| PUT | `/users/me` | 🔐 |
| POST | `/users/me/photo` | 🔐 |
| DELETE | `/users/me/photo` | 🔐 |
| POST | `/users/me/deactivate` | 🔐 |
| DELETE | `/users/account` | 🔐 |
| GET | `/users/sessions` | 🔐 |
| DELETE | `/users/sessions/logout-all` | 🔐 |
| POST | `/users/me/phone/send-otp` | 🔐 |
| POST | `/users/me/phone/verify-otp` | 🔐 |
| POST | `/users/me/email/send-verification` | 🔐 |
| POST | `/users/me/email/verify` | 🔐 |

### Trips — `/api/v1/trips`
| Method | Path | Access |
|---|---|:---:|
| POST | `/trips` | 🔐 |
| GET | `/trips/my` | 🔐 |
| GET | `/trips/{id}` | 🔐 |
| GET | `/trips/share/{token}` | 🔓 |
| PUT | `/trips/{tripId}/days/{dayId}` | 🔐 |
| PATCH | `/trips/{id}/title` | 🔐 |
| POST | `/trips/{tripId}/days/{dayId}/items` | 🔐 |
| DELETE | `/trips/{tripId}/days/{dayId}/items/{itemId}` | 🔐 |
| PATCH | `/trips/{id}/status` | 🔐 |
| DELETE | `/trips/{id}` | 🔐 |
| POST | `/trips/{id}/generate-ai` | 🔐 |

### Budget — `/api/v1/budget`
| Method | Path | Access |
|---|---|:---:|
| POST | `/budget` | 🔐 |
| GET | `/budget/trip/{tripId}` | 🔐 |
| GET | `/budget/trip/{tripId}/summary` | 🔐 |
| PUT | `/budget/{budgetId}` | 🔐 |
| PUT | `/budget/{budgetId}/categories` | 🔐 |
| POST | `/budget/{budgetId}/repair-dates` | 🔐 |
| POST | `/budget/{budgetId}/items` | 🔐 |
| GET | `/budget/{budgetId}/items` | 🔐 |
| PUT | `/budget/{budgetId}/items/{itemId}` | 🔐 |
| DELETE | `/budget/{budgetId}/items/{itemId}` | 🔐 |

### Destinations — `/api/v1/destinations`
| Method | Path | Access |
|---|---|:---:|
| GET | `/destinations` | 🔓 |
| GET | `/destinations/featured` | 🔓 |
| GET | `/destinations/search` | 🔓 |
| GET | `/destinations/nearby` | 🔓 |
| GET | `/destinations/{id}` | 🔓 |
| GET | `/destinations/{id}/reviews` | 🔓 |
| POST | `/destinations/{id}/reviews` | 🔐 |
| DELETE | `/destinations/{id}/reviews/{reviewId}` | 🛡️ |
| POST | `/destinations` | 🛡️ |
| PUT | `/destinations/{id}` | 🛡️ |
| PUT | `/destinations/{id}/featured` | 🛡️ |
| PUT | `/destinations/{id}/active` | 🛡️ |
| DELETE | `/destinations/{id}` | 🛡️ |

### Hidden Gems — `/api/v1/gems`
| Method | Path | Access |
|---|---|:---:|
| GET | `/gems` | 🔓 |
| GET | `/gems/{id}` | 🔓 |
| GET | `/gems/search` | 🔓 |
| GET | `/gems/nearby` | 🔓 |
| GET | `/gems/{gemId}/reviews` | 🔓 |
| POST | `/gems/{gemId}/reviews` | 🔐 |
| DELETE | `/gems/{gemId}/reviews/{reviewId}` | 🛡️ |
| POST | `/gems/submit` | 🔐 |
| GET | `/gems/pending` | 🛡️ |
| POST | `/gems` | 🛡️ |
| PUT | `/gems/{id}/approve` | 🛡️ |
| PUT | `/gems/{id}` | 🛡️ |
| DELETE | `/gems/{id}` | 🛡️ |

### Tour Guides — `/api/v1/guides`, `/api/v1/guide-bookings`
| Method | Path | Access |
|---|---|:---:|
| GET | `/guides` | 🔓 |
| GET | `/guides/{id}` | 🔓 |
| GET | `/guides/search` | 🔓 |
| GET | `/guides/{id}/reviews` | 🔓 |
| GET | `/guides/{id}/availability` | 🔓 |
| GET | `/guides/{id}/bookings` | 🔓 |
| POST | `/guides/{id}/reviews` | 🔐 |
| POST | `/guides` | 🛡️ |
| PUT | `/guides/{id}` | 🛡️ |
| PUT | `/guides/{id}/availability` | 🛡️ |
| DELETE | `/guides/{id}` | 🛡️ |
| POST | `/guide-bookings` | 🔐 |
| GET | `/guide-bookings/my` | 🔐 |
| GET | `/guide-bookings/{id}` | 🔐 |
| PATCH | `/guide-bookings/{id}/cancel` | 🔐 |
| PATCH | `/guide-bookings/{id}/status` | 🛡️ |

### Local Vehicles — `/api/v1/vehicles/local`, `/api/v1/vehicle-bookings`, `/api/v1/admin/vehicles`
| Method | Path | Access |
|---|---|:---:|
| GET | `/vehicles/local` | 🔓 |
| GET | `/vehicles/local/{id}` | 🔓 |
| POST | `/vehicles/local/search` | 🔓 |
| GET | `/vehicles/local/tuktuks` | 🔓 |
| GET | `/vehicles/local/{id}/check-availability` | 🔓 |
| GET | `/vehicles/local/{id}/reviews` | 🔓 |
| POST | `/vehicles/local/{id}/reviews` | 🔐 |
| PUT | `/vehicles/local/{id}/availability` | 🛡️ |
| POST | `/vehicle-bookings` | 🔐 |
| GET | `/vehicle-bookings/my` | 🔐 |
| GET | `/vehicle-bookings/{id}` | 🔐 |
| GET | `/vehicle-bookings/trip/{tripId}` | 🔐 |
| GET | `/vehicle-bookings/check-availability` | 🔐 |
| PATCH | `/vehicle-bookings/{id}/cancel` | 🔐 |
| PATCH | `/vehicle-bookings/{id}/status` | 🛡️ |
| POST | `/admin/vehicles` | 🛡️ |
| PUT | `/admin/vehicles/{id}` | 🛡️ |
| PATCH | `/admin/vehicles/{id}` | 🛡️ |
| DELETE | `/admin/vehicles/{id}` | 🛡️ |

### Hotels — `/api/v1/hotels` (reactive proxy to RapidAPI)
| Method | Path | Access |
|---|---|:---:|
| POST | `/hotels/search` | 🔓 |
| GET | `/hotels/{hotelId}` | 🔓 |

### Events — `/api/v1/events`
| Method | Path | Access |
|---|---|:---:|
| GET | `/events` | 🔓 |
| GET | `/events/upcoming` | 🔓 |
| GET | `/events/trip-sync` | 🔓 |
| GET | `/events/{id}` | 🔓 |
| POST | `/events` | 🛡️ |
| PUT | `/events/{id}` | 🛡️ |
| DELETE | `/events/{id}` | 🛡️ |

### Chat — `/api/v1/chat` (+ WebSocket `/ws-chat`)
| Method | Path | Access |
|---|---|:---:|
| GET | `/chat/my-conversation` | 🔐 |
| GET | `/chat/my-conversation/messages` | 🔐 |
| POST | `/chat/my-conversation/messages` | 🔐 |
| PATCH | `/chat/my-conversation/read` | 🔐 |
| GET | `/chat/admin/conversations` | 🛡️ |
| GET | `/chat/admin/unread-count` | 🛡️ |
| GET | `/chat/admin/conversations/{id}/messages` | 🛡️ |
| POST | `/chat/admin/conversations/{id}/messages` | 🛡️ |
| PATCH | `/chat/admin/conversations/{id}/read` | 🛡️ |

### Notifications — `/api/v1/notifications`
| Method | Path | Access |
|---|---|:---:|
| GET | `/notifications/my` | 🔐 |
| GET | `/notifications/unread-count` | 🔐 |
| PATCH | `/notifications/{id}/read` | 🔐 |
| PATCH | `/notifications/read-all` | 🔐 |
| POST | `/notifications/dev/trigger-reminders` | 🔐 (also requires `app.dev-mode=true`) |

### Uploads — `/api/v1/upload`
| Method | Path | Access |
|---|---|:---:|
| POST | `/upload/single` | 🔐 |
| POST | `/upload/multiple` | 🔐 |
| DELETE | `/upload` | 🔐 |

### Contact — `/api/v1/contact`
| Method | Path | Access |
|---|---|:---:|
| POST | `/contact` | 🔓 |
| GET | `/contact/admin` | 🛡️ |
| GET | `/contact/admin/unread` | 🛡️ |
| GET | `/contact/admin/count` | 🛡️ |
| PATCH | `/contact/admin/{id}/read` | 🛡️ |
| POST | `/contact/admin/{id}/reply` | 🛡️ |
| DELETE | `/contact/admin/{id}` | 🛡️ |

### Payments — see [Payment System](#-payment-system-payhere)

### Admin — `/api/v1/admin`
| Method | Path | Access |
|---|---|:---:|
| GET | `/admin/dashboard` | 🛡️ |
| GET | `/admin/dashboard/recent-activity` | 🛡️ |
| GET | `/admin/dashboard/top-lists` | 🛡️ |
| GET | `/admin/analytics` | 🛡️ |
| GET | `/admin/revenue` | 🛡️ |
| GET | `/admin/bookings` | 🛡️ |
| POST | `/admin/bookings/bulk-status` | 🛡️ |
| GET | `/admin/users` | 🛡️ |
| GET | `/admin/users/{id}` | 🛡️ |
| PUT | `/admin/users/{id}/activate` | 🛡️ |
| PUT | `/admin/users/{id}/deactivate` | 🛡️ |
| POST | `/admin/users/bulk-activate` | 🛡️ |
| POST | `/admin/users/bulk-deactivate` | 🛡️ |
| PUT | `/admin/users/{id}/role` | 🛡️ |
| PUT | `/admin/users/{id}/reset-verification` | 🛡️ |
| GET | `/admin/stats/vehicles` | 🛡️ |
| GET | `/admin/stats/guides` | 🛡️ |
| GET | `/admin/reviews` | 🛡️ |
| DELETE | `/admin/reviews/{entityType}/{id}` | 🛡️ |
| POST | `/admin/reviews/bulk-delete` | 🛡️ |

> Access is enforced centrally in `SecurityConfig` via path/method matchers — no `@PreAuthorize` annotations are used in the codebase.

---

## 🗄️ Database

Engine: **PostgreSQL**, schema managed via Hibernate (`ddl-auto=update`) and seeded via `data.sql`.

**Entities (`model/`):**

| Entity | Key relationships |
|---|---|
| `User` | target of most `@ManyToOne` relations below |
| `RefreshToken` | `user` → User |
| `LoginHistory` | `user` → User |
| `VerificationCode`, `VerificationRateLimit` | plain `userId` reference |
| `Trip` | `user` → User · `days` → List\<TripDay\> · `preference` → TripPreference (1:1) |
| `TripDay` | `trip` → Trip · `items` → List\<TripDayItem\> |
| `TripDayItem` | `tripDay` → TripDay |
| `TripPreference` | `trip` → Trip (1:1) |
| `Budget` | `trip` → Trip (1:1) · `user` → User · `items` → List\<BudgetItem\> |
| `BudgetItem` | `budget` → Budget |
| `Destination` | plain fields (uses `BudgetLevel` enum) |
| `DestinationReview` | `destination` → Destination |
| `HiddenGem` | `submittedBy` → User |
| `Gemreview` | `gem` → HiddenGem |
| `Event` | plain fields |
| `TourGuide` | plain fields |
| `GuideBooking` | `guide` → TourGuide · `user` → User · `trip` → Trip |
| `GuideReview` | `guide` → TourGuide · `user` → User · `booking` → GuideBooking |
| `GuidePayment` | `guideBooking` → GuideBooking · `user` → User |
| `Vehicle` | plain fields |
| `VehicleBooking` | `vehicle` → Vehicle · `user` → User · `trip` → Trip |
| `VehicleReview` | `vehicle` → Vehicle · `user` → User · `booking` → VehicleBooking |
| `VehiclePayment` | `vehicleBooking` → VehicleBooking · `user` → User |
| `Location` | name/lat/lng geocoding gazetteer |
| `ChatConversation`, `ChatMessage` | plain traveler/sender ID references |
| `Notification` | `user` → User |
| `ContactMessage` | plain fields |

**Seed data (`data.sql`, idempotent — safe on every restart):** 29 destinations, 31 hidden gems, 23 events, 8 tour guides, 8 vehicles, 29 geocoded locations, and 4 demo users (`admin@exploreceylon.com` as ADMIN, plus 3 TRAVELER accounts) sharing a common demo password hash.

---

## 💳 Payment System (PayHere)

Guide bookings and vehicle bookings are paid for in **two phases** via [PayHere](https://www.payhere.lk/), Sri Lanka's payment gateway:

| Phase | Share of total booking cost |
|---|---|
| **Advance** | 20% — paid to reserve the booking |
| **Final** | 80% — paid to complete the booking |

> The `phasePercent` field comment in `GuidePayment`/`VehiclePayment` mentions "40 or 60," but the currently implemented logic in `PayHereService.calcPhaseAmount()` (and the booking-status migration in `data.sql`) both compute a **20% advance / 80% final** split — that is the split actually in effect.

On top of that, ExploreCeylon takes a **15% commission** on every payment; the remaining **85%** is the payout owed to the guide or vehicle owner (`PayHereService.calculateCommission` / `calculatePayout`).

**Flow:**
1. `POST /api/v1/payments/{guide|vehicle}/initiate` — client requests a payment; backend generates an order ID and returns the PayHere hidden-form fields (merchant ID, amount, MD5 hash, checkout action URL).
2. Frontend auto-submits the form to PayHere's sandbox/live checkout page.
3. PayHere calls back `POST /api/v1/payments/{guide|vehicle}/notify` (public IPN webhook) with the payment result; the backend verifies the MD5 signature and updates payment/booking status.
4. `POST /api/v1/payments/{guide|vehicle}/confirm/{orderId}` — a manual confirmation fallback used from the frontend's return page, useful when PayHere's IPN cannot reach a non-public `notify_url`.

**Endpoints (per booking type — `guide` / `vehicle`):**

| Method | Path | Access |
|---|---|:---:|
| POST | `/payments/{type}/initiate` | 🔐 |
| POST | `/payments/{type}/notify` | 🔓 (PayHere IPN webhook) |
| POST | `/payments/{type}/confirm/{orderId}` | 🔐 |
| GET | `/payments/{type}/booking/{bookingId}` | 🔐 |
| GET | `/payments/{type}/my` | 🔐 |

**⚠️ Local testing note:** PayHere's IPN webhook must reach `app.base.url` + `/api/v1/payments/{type}/notify` over the public internet. When running the backend on `localhost`, expose it with a tunnel such as **ngrok** (`ngrok http 8080`) and point `app.base.url` at the generated public URL — otherwise rely on the `/confirm/{orderId}` fallback endpoint instead of the webhook.

---

## 📄 License

Distributed under the **MIT License**.
