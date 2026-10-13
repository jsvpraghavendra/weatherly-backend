package com.weatherly;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;

/**
 * Finds cities and fetches forecasts from Open-Meteo
 * (or a configured upstream weather provider).
 */
@Service
public class WeatherService {

    private static final Logger logger = LoggerFactory.getLogger(WeatherService.class);

    private final String geocodingUrl;
    private final String forecastUrl;
    private final String apiKey;
    private final RestClient client;
    private final ObjectMapper mapper;

    public WeatherService(
            @Value("${weather.geocoding-url:https://geocoding-api.open-meteo.com/v1/search}") String geocodingUrl,
            @Value("${weather.forecast-url:https://api.open-meteo.com/v1/forecast}") String forecastUrl,
            @Value("${weather.api-key:}") String apiKey,
            @Value("${weather.user-agent:Weatherly/1.0 (https://github.com/jsvpraghavendra/weatherly-backend)}") String userAgent,
            @Value("${weather.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${weather.read-timeout-ms:8000}") int readTimeoutMs) {

        this.geocodingUrl = geocodingUrl;
        this.forecastUrl = forecastUrl;
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.mapper = new ObjectMapper();

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        this.client = RestClient.builder()
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.USER_AGENT, userAgent)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /** Default constructor for standalone test environments. */
    public WeatherService() {
        this(
                "https://geocoding-api.open-meteo.com/v1/search",
                "https://api.open-meteo.com/v1/forecast",
                "",
                "Weatherly/1.0 (https://github.com/jsvpraghavendra/weatherly-backend)",
                5000,
                8000
        );
    }

    /** Looks up a city by name and returns its forecast. */
    public ObjectNode getWeather(String city) {
        if (city == null || city.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a city name.");
        }

        URI geoUri = buildGeocodingUri(city.trim(), 1);
        try {
            JsonNode geo = client.get().uri(geoUri).retrieve().body(JsonNode.class);
            JsonNode results = geo == null ? null : geo.get("results");
            if (results == null || results.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "City not found. Check the spelling and try again.");
            }
            JsonNode place = results.get(0);
            return forecast(
                    place.get("latitude").asDouble(),
                    place.get("longitude").asDouble(),
                    place.path("name").asText(city.trim()),
                    place.path("admin1").asText(""),
                    place.path("country").asText(""));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (RestClientResponseException e) {
            logger.warn("Open-Meteo geocoding returned HTTP {}", e.getStatusCode().value());
            throw translateResponseException(e);
        } catch (ResourceAccessException e) {
            logger.warn("Open-Meteo geocoding timed out: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT,
                    "Weather provider connection timed out. Try again in a moment.");
        } catch (RestClientException e) {
            logger.warn("Open-Meteo geocoding failed: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "The weather service is not responding. Try again in a moment.");
        }
    }

    /** Returns the forecast for exact coordinates. */
    public ObjectNode forecast(double lat, double lon, String name, String region, String country) {
        if (lat < -90.0 || lat > 90.0 || lon < -180.0 || lon > 180.0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid coordinates: latitude must be between -90 and 90, longitude between -180 and 180.");
        }

        URI forecastUri = buildForecastUri(lat, lon);
        try {
            JsonNode forecast = client.get().uri(forecastUri).retrieve().body(JsonNode.class);
            if (forecast == null || forecast.get("current") == null) {
                logger.warn("Forecast payload missing 'current' block");
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Weather provider returned incomplete forecast data.");
            }
            ObjectNode out = mapper.createObjectNode();
            out.put("city", name != null && !name.isBlank() ? name : "Selected place");
            out.put("region", region != null ? region : "");
            out.put("country", country != null ? country : "");
            out.put("lat", lat);
            out.put("lon", lon);
            out.set("current", forecast.get("current"));
            out.set("hourly", forecast.get("hourly"));
            out.set("daily", forecast.get("daily"));
            return out;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (RestClientResponseException e) {
            logger.warn("Open-Meteo forecast request returned HTTP {}", e.getStatusCode().value());
            throw translateResponseException(e);
        } catch (ResourceAccessException e) {
            logger.warn("Open-Meteo forecast timed out: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT,
                    "Weather provider connection timed out. Try again in a moment.");
        } catch (RestClientException e) {
            logger.warn("Open-Meteo forecast failed: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "The weather service is not responding. Try again in a moment.");
        }
    }

    /** Up to 5 matching places for search suggestions. */
    public ArrayNode searchCities(String q) {
        ArrayNode out = mapper.createArrayNode();
        if (q == null || q.trim().length() < 2) {
            return out;
        }
        try {
            URI searchUri = buildGeocodingUri(q.trim(), 5);
            JsonNode geo = client.get().uri(searchUri).retrieve().body(JsonNode.class);
            JsonNode results = geo == null ? null : geo.get("results");
            if (results != null) {
                for (JsonNode p : results) {
                    ObjectNode item = mapper.createObjectNode();
                    item.put("name", p.path("name").asText());
                    item.put("region", p.path("admin1").asText(""));
                    item.put("country", p.path("country").asText(""));
                    item.put("lat", p.path("latitude").asDouble());
                    item.put("lon", p.path("longitude").asDouble());
                    out.add(item);
                }
            }
        } catch (RestClientResponseException e) {
            logger.warn("Open-Meteo city suggestions returned HTTP {}", e.getStatusCode().value());
        } catch (RestClientException e) {
            logger.warn("Open-Meteo city suggestions request failed: {}", e.getMessage());
        }
        return out;
    }

    private URI buildGeocodingUri(String query, int count) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(geocodingUrl)
                .queryParam("name", query)
                .queryParam("count", count)
                .queryParam("language", "en")
                .queryParam("format", "json");

        if (!apiKey.isEmpty()) {
            builder.queryParam("apikey", apiKey);
        }
        return builder.build().encode().toUri();
    }

    private URI buildForecastUri(double lat, double lon) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(forecastUrl)
                .queryParam("latitude", lat)
                .queryParam("longitude", lon)
                .queryParam("current", "temperature_2m,relative_humidity_2m,apparent_temperature,is_day,precipitation,weather_code,cloud_cover,wind_speed_10m,wind_direction_10m,surface_pressure")
                .queryParam("hourly", "temperature_2m,weather_code,is_day,precipitation_probability")
                .queryParam("daily", "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset,uv_index_max")
                .queryParam("timezone", "auto")
                .queryParam("forecast_days", 7);

        if (!apiKey.isEmpty()) {
            builder.queryParam("apikey", apiKey);
        }
        return builder.build().encode().toUri();
    }

    private ResponseStatusException translateResponseException(RestClientResponseException e) {
        int status = e.getStatusCode().value();
        if (status == 404) {
            return new ResponseStatusException(HttpStatus.NOT_FOUND, "City or weather data not found.");
        }
        if (status == 429) {
            return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Weather service rate limit exceeded. Please try again shortly.");
        }
        if (status == 400) {
            return new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid weather request parameters.");
        }
        if (status == 401 || status == 403) {
            return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Weather service access rejected by upstream provider (HTTP " + status + ").");
        }
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "The weather service is not responding. Try again in a moment.");
    }
}
