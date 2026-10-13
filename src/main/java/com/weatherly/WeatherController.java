package com.weatherly;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class WeatherController {

    private final WeatherService service;

    public WeatherController(WeatherService service) {
        this.service = service;
    }

    // By city name:    /api/weather?city=London
    // By coordinates:  /api/weather?lat=51.5&lon=-0.12&name=London&region=England&country=United Kingdom
    @GetMapping("/weather")
    public ObjectNode weather(@RequestParam(required = false) String city,
                              @RequestParam(required = false) Double lat,
                              @RequestParam(required = false) Double lon,
                              @RequestParam(defaultValue = "") String name,
                              @RequestParam(defaultValue = "") String region,
                              @RequestParam(defaultValue = "") String country) {

        if (lat != null || lon != null) {
            if (lat == null || lon == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Both latitude and longitude are required when querying by coordinates.");
            }
            if (lat < -90.0 || lat > 90.0 || lon < -180.0 || lon > 180.0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Invalid coordinates: latitude must be between -90 and 90, longitude between -180 and 180.");
            }
            return service.forecast(lat, lon, name.isBlank() ? "Selected place" : name.trim(), region.trim(), country.trim());
        }

        if (city == null || city.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a city name.");
        }

        if (city.trim().length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "City name must be under 100 characters.");
        }

        return service.getWeather(city.trim());
    }

    // City suggestions while typing: /api/cities?q=lon
    @GetMapping("/cities")
    public ArrayNode cities(@RequestParam(required = false, defaultValue = "") String q) {
        return service.searchCities(q.trim());
    }
}
