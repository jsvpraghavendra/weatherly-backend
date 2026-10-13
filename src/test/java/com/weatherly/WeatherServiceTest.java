package com.weatherly;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class WeatherServiceTest {

    private WeatherService service;

    @BeforeEach
    void setUp() {
        String apiKey = System.getenv("OPENWEATHER_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            File localProps = new File("local.properties");
            if (localProps.exists()) {
                try (FileInputStream fis = new FileInputStream(localProps)) {
                    Properties props = new Properties();
                    props.load(fis);
                    apiKey = props.getProperty("weather.api.key");
                } catch (Exception ignored) {
                }
            }
        }
        service = new WeatherService(apiKey);
    }

    @Test
    void testOwmToWmoMapping() {
        assertEquals(0, WeatherService.owmToWmo(800)); // Clear
        assertEquals(1, WeatherService.owmToWmo(801)); // Few clouds
        assertEquals(2, WeatherService.owmToWmo(802)); // Scattered clouds
        assertEquals(3, WeatherService.owmToWmo(804)); // Overcast
        assertEquals(45, WeatherService.owmToWmo(741)); // Fog
        assertEquals(53, WeatherService.owmToWmo(300)); // Drizzle
        assertEquals(61, WeatherService.owmToWmo(500)); // Light rain
        assertEquals(63, WeatherService.owmToWmo(501)); // Moderate rain
        assertEquals(65, WeatherService.owmToWmo(502)); // Heavy rain
        assertEquals(71, WeatherService.owmToWmo(600)); // Light snow
        assertEquals(73, WeatherService.owmToWmo(601)); // Snow
        assertEquals(85, WeatherService.owmToWmo(620)); // Snow shower
        assertEquals(95, WeatherService.owmToWmo(200)); // Thunderstorm
    }

    @Test
    void testSearchCitiesLive() {
        if (!service.hasApiKey()) return;
        ArrayNode cities = service.searchCities("London");
        assertNotNull(cities);
        assertTrue(cities.size() > 0);
        assertTrue(cities.get(0).has("name"));
        assertTrue(cities.get(0).has("lat"));
        assertTrue(cities.get(0).has("lon"));
    }

    @Test
    void testGetWeatherLive() {
        if (!service.hasApiKey()) return;
        ObjectNode weather = service.getWeather("London");
        assertNotNull(weather);
        assertEquals("London", weather.path("city").asText());
        assertTrue(weather.has("lat"));
        assertTrue(weather.has("lon"));

        // Current
        assertTrue(weather.has("current"));
        assertTrue(weather.path("current").has("temperature_2m"));
        assertTrue(weather.path("current").has("relative_humidity_2m"));
        assertTrue(weather.path("current").has("apparent_temperature"));
        assertTrue(weather.path("current").has("weather_code"));
        assertTrue(weather.path("current").has("wind_speed_10m"));
        assertTrue(weather.path("current").has("is_day"));

        // Hourly
        assertTrue(weather.has("hourly"));
        assertEquals(24, weather.path("hourly").path("time").size());
        assertEquals(24, weather.path("hourly").path("temperature_2m").size());
        assertEquals(24, weather.path("hourly").path("weather_code").size());

        // Daily
        assertTrue(weather.has("daily"));
        assertEquals(7, weather.path("daily").path("time").size());
        assertEquals(7, weather.path("daily").path("temperature_2m_max").size());
        assertEquals(7, weather.path("daily").path("temperature_2m_min").size());
        assertEquals(7, weather.path("daily").path("sunrise").size());
        assertEquals(7, weather.path("daily").path("sunset").size());
    }
}
