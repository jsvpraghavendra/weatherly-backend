package com.weatherly;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Finds cities and fetches forecasts from Open-Meteo
 * (a free weather API that needs no API key).
 */
@Service
public class WeatherService {

    private static final Logger logger = LoggerFactory.getLogger(WeatherService.class);

    private static final String GEO_URL =
            "https://geocoding-api.open-meteo.com/v1/search?name={name}&count={count}&language=en&format=json";

    private static final String FORECAST_URL =
            "https://api.open-meteo.com/v1/forecast?latitude={lat}&longitude={lon}"
            + "&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day,precipitation,weather_code,cloud_cover,wind_speed_10m,wind_direction_10m,surface_pressure"
            + "&hourly=temperature_2m,weather_code,is_day,precipitation_probability"
            + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset,uv_index_max"
            + "&timezone=auto&forecast_days=7";

    private final RestClient client = RestClient.create();
    private final ObjectMapper mapper = new ObjectMapper();

    /** Looks up a city by name and returns its forecast. */
    public ObjectNode getWeather(String city) {
        try {
            JsonNode geo = client.get().uri(GEO_URL, city.trim(), 1).retrieve().body(JsonNode.class);
            JsonNode results = geo == null ? null : geo.get("results");
            if (results == null || results.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "City not found. Check the spelling and try again.");
            }
            JsonNode place = results.get(0);
            return forecast(
                    place.get("latitude").asDouble(),
                    place.get("longitude").asDouble(),
                    place.path("name").asText(),
                    place.path("admin1").asText(""),
                    place.path("country").asText(""));
        } catch (RestClientResponseException e) {
            logger.warn("Open-Meteo geocoding request returned HTTP {}", e.getStatusCode().value());
            throw unavailable();
        } catch (RestClientException e) {
            logger.warn("Open-Meteo geocoding request failed", e);
            throw unavailable();
        }
    }

    /** Returns the forecast for exact coordinates. */
    public ObjectNode forecast(double lat, double lon, String name, String region, String country) {
        try {
            JsonNode forecast = client.get().uri(FORECAST_URL, lat, lon).retrieve().body(JsonNode.class);
            if (forecast == null || forecast.get("current") == null) {
                throw unavailable();
            }
            ObjectNode out = mapper.createObjectNode();
            out.put("city", name);
            out.put("region", region);
            out.put("country", country);
            out.put("lat", lat);
            out.put("lon", lon);
            out.set("current", forecast.get("current"));
            out.set("hourly", forecast.get("hourly"));
            out.set("daily", forecast.get("daily"));
            return out;
        } catch (RestClientResponseException e) {
            logger.warn("Open-Meteo forecast request returned HTTP {}", e.getStatusCode().value());
            throw unavailable();
        } catch (RestClientException e) {
            logger.warn("Open-Meteo forecast request failed", e);
            throw unavailable();
        }
    }

    /** Up to 5 matching places for the search box suggestions. */
    public ArrayNode searchCities(String q) {
        ArrayNode out = mapper.createArrayNode();
        if (q == null || q.trim().length() < 2) {
            return out;
        }
        try {
            JsonNode geo = client.get().uri(GEO_URL, q.trim(), 5).retrieve().body(JsonNode.class);
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
            logger.warn("Open-Meteo city suggestions request failed", e);
        }
        return out;
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "The weather service is not responding. Try again in a moment.");
    }
}
