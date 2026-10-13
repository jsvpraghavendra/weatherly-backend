# Weatherly Backend

Spring Boot API service for Weatherly. It provides city lookup, geocoding, and weather forecasts using the Open-Meteo API. No database is required, and the standard tier requires no API key.

---

## REST Endpoints

| Endpoint | Method | Description | Example / Parameters |
|---|---|---|---|
| `/` | `GET` | Service status and available endpoint directory (prevents 404 Whitelabel) | `GET /` |
| `/health` | `GET` | Health check for deployment liveness probes (returns 200 OK) | `GET /health` |
| `/api/weather` | `GET` | Current weather and 7-day forecast by city or coordinates | `GET /api/weather?city=London`<br>`GET /api/weather?lat=51.5&lon=-0.12&name=London` |
| `/api/cities` | `GET` | City search auto-complete suggestions | `GET /api/cities?q=lon` |

---

## Local Development

### Requirements
- **Java 21** (JDK 21 or newer)
- **Apache Maven 3.9+**

### Run with Maven
```sh
mvn spring-boot:run
```

By default, the server listens on port `8080`.
The frontend's Vite development server (`http://localhost:5173`) proxies `/api` requests to `http://localhost:8080`.

### Run tests
```sh
mvn test
```

### Build JAR
```sh
mvn clean package
java -jar target/weatherly-backend-1.0.0.jar
```

---

## Build and Run with Docker

Build the container image:
```sh
docker build -t weatherly-backend .
```

Run locally:
```sh
docker run --rm -p 8080:8080 weatherly-backend
```

The Dockerfile builds using `maven:3.9-eclipse-temurin-21` and runs on a hardened `eclipse-temurin:21-jre-jammy` base image as an unprivileged user (`appuser`).

---

## Render Deployment Guide

Deploy this repository as a **Web Service** on Render:

1. **New Web Service**: Connect your GitHub repository `weatherly-backend`.
2. **Environment**: Select **Docker** (Render will automatically detect the `Dockerfile`).
   - *Alternatively, if deploying as Java/Native*:
     - Build Command: `mvn clean package -DskipTests`
     - Start Command: `java -jar target/weatherly-backend-1.0.0.jar`
3. **Environment Variables**:
   In the Render Dashboard under **Environment**:

   | Key | Recommended Value | Purpose |
   |---|---|---|
   | `PORT` | *(Leave empty - Render assigns this automatically, e.g. 10000)* | Port Tomcat listens on. |
   | `CORS_ALLOWED_ORIGINS` | `https://*.onrender.com,http://localhost:5173` | Allowed frontend origins (comma-separated). Set to your exact frontend domain if using a custom domain. |
   | `WEATHER_USER_AGENT` | `Weatherly/1.0 (https://github.com/jsvpraghavendra/weatherly-backend)` | Custom User-Agent header sent to Open-Meteo to satisfy Cloudflare/bot management requirements. |
   | `WEATHER_GEOCODING_URL` | `https://geocoding-api.open-meteo.com/v1/search` | Upstream geocoding API URL. |
   | `WEATHER_FORECAST_URL` | `https://api.open-meteo.com/v1/forecast` | Upstream forecast API URL. |
   | `WEATHER_API_KEY` | *(Optional - leave empty for free tier)* | Optional API key for commercial Open-Meteo tier. Never commit secrets. |
   | `WEATHER_CONNECT_TIMEOUT_MS` | `5000` | Connection timeout in milliseconds. |
   | `WEATHER_READ_TIMEOUT_MS` | `8000` | Read timeout in milliseconds. |

4. **Health Check Path**:
   Set **Health Check Path** in Render settings to `/health`. Render will verify HTTP 200 before routing live traffic.

---

## Troubleshooting

- **HTTP 404 on Root (`/`)**:
  - The root path `GET /` is now explicitly mapped to return a JSON status message. Visiting the root URL in a browser will return a JSON status payload instead of Spring Boot's Whitelabel 404 page.
- **HTTP 502 (Bad Gateway) on `/api/weather`**:
  - Open-Meteo is an external service. A 502 indicates that upstream communication failed or was rejected.
  - The service now sends a custom `User-Agent` header (`Weatherly/1.0`), preventing Cloudflare bot filtering on datacenter IPs.
  - Connect/read timeouts are enforced (5s / 8s).
  - Check service logs in the Render dashboard for diagnostic logs starting with `Open-Meteo`.
