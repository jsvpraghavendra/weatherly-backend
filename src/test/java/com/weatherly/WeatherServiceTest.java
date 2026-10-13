package com.weatherly;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

public class WeatherServiceTest {

    private final WeatherService service = new WeatherService();

    @Test
    void testBlankCityThrowsBadRequest() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.getWeather("   "));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Enter a city name.", ex.getReason());
    }

    @Test
    void testNullCityThrowsBadRequest() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.getWeather(null));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Enter a city name.", ex.getReason());
    }

    @Test
    void testInvalidCoordinatesThrowsBadRequest() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.forecast(95.0, 10.0, "Invalid Lat", "", ""));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void testSearchCitiesShortQueryReturnsEmpty() {
        ArrayNode results = service.searchCities("a");
        assertNotNull(results);
        assertEquals(0, results.size());
    }

    @Test
    void testSearchCitiesNullQueryReturnsEmpty() {
        ArrayNode results = service.searchCities(null);
        assertNotNull(results);
        assertEquals(0, results.size());
    }
}
