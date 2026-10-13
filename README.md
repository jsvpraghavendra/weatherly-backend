# Weatherly Backend

Spring Boot API for Weatherly. It provides city lookup and weather forecasts
using the Open-Meteo geocoding and forecast APIs. No database or Open-Meteo API
key is required.

## Run locally

Requirements: Java 21 and Maven.

```sh
mvn spring-boot:run
```

The API listens on port `8080` by default:

- `GET /api/cities?q=lon`
- `GET /api/weather?city=London`

The frontend's Vite development server proxies `/api` requests to
`http://localhost:8080`.

## Build and run with Docker

From this directory:

```sh
docker build -t weatherly-backend .
docker run --rm -p 8080:8080 weatherly-backend
```

The image builds the application with Java 21 and runs it on a Java 21 JRE
image as a non-root user. The service uses the hosting platform's `PORT`
environment variable when set, and defaults to `8080`.

## Deployment

Deploy this directory as a Docker application. The container listens on the
port specified by `PORT` (or `8080` if unset). Configure the frontend hosting
platform to proxy `/api` requests to this backend's public URL; the Vite proxy
in `vite.config.js` is only for local development.

The API fetches weather and geocoding data from Open-Meteo at runtime, so the
deployed service needs outbound HTTPS access to `api.open-meteo.com` and
`geocoding-api.open-meteo.com`.
