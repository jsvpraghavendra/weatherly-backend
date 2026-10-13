package com.weatherly;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
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
        if (lat != null && lon != null) {
            return service.forecast(lat, lon, name.isBlank() ? "Selected place" : name, region, country);
        }
        if (city == null || city.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a city name.");
        }
        return service.getWeather(city);
    }

    // City suggestions while typing: /api/cities?q=lon
    @GetMapping("/cities")
    public ArrayNode cities(@RequestParam String q) {
        return service.searchCities(q);
    }
}
