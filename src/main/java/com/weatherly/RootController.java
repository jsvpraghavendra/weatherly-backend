package com.weatherly;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class RootController {

    @GetMapping("/")
    public ResponseEntity<Map<String, Object>> root() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "UP");
        response.put("service", "weatherly-backend");
        response.put("message", "Weatherly backend service is operational.");
        response.put("timestamp", Instant.now().toString());

        Map<String, String> endpoints = new LinkedHashMap<>();
        endpoints.put("health", "/health");
        endpoints.put("weather_by_city", "/api/weather?city={cityName}");
        endpoints.put("weather_by_coords", "/api/weather?lat={latitude}&lon={longitude}&name={placeName}");
        endpoints.put("city_search", "/api/cities?q={searchQuery}");
        response.put("endpoints", endpoints);

        return ResponseEntity.ok(response);
    }

    @GetMapping({"/health", "/api/health"})
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "UP");
        response.put("service", "weatherly-backend");
        response.put("timestamp", Instant.now().toString());
        return ResponseEntity.ok(response);
    }
}
