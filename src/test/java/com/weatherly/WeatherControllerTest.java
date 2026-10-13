package com.weatherly;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {WeatherController.class, RootController.class})
public class WeatherControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WeatherService weatherService;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void testRootEndpointReturnsOperationalStatus() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("weatherly-backend"))
                .andExpect(jsonPath("$.endpoints.health").value("/health"));
    }

    @Test
    void testHealthEndpointReturnsUp() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("weatherly-backend"));
    }

    @Test
    void testApiHealthEndpointReturnsUp() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void testWeatherEndpointMissingCityAndCoordsReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/weather"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Enter a city name."));
    }

    @Test
    void testWeatherEndpointBlankCityReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/weather").param("city", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Enter a city name."));
    }

    @Test
    void testWeatherEndpointInvalidCoordsReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/weather").param("lat", "95.0").param("lon", "10.0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void testWeatherEndpointMissingLonReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/weather").param("lat", "51.5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Both latitude and longitude are required when querying by coordinates."));
    }

    @Test
    void testWeatherEndpointValidCityReturnsData() throws Exception {
        ObjectNode sample = mapper.createObjectNode();
        sample.put("city", "London");
        sample.put("region", "England");
        sample.put("country", "United Kingdom");
        sample.put("lat", 51.5);
        sample.put("lon", -0.12);

        ObjectNode current = mapper.createObjectNode();
        current.put("temperature_2m", 18.5);
        current.put("weather_code", 1);
        sample.set("current", current);

        when(weatherService.getWeather("London")).thenReturn(sample);

        mockMvc.perform(get("/api/weather").param("city", "London"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.city").value("London"))
                .andExpect(jsonPath("$.current.temperature_2m").value(18.5));
    }

    @Test
    void testWeatherEndpointCityNotFoundReturns404() throws Exception {
        when(weatherService.getWeather("UnknownCity123"))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "City not found. Check the spelling and try again."));

        mockMvc.perform(get("/api/weather").param("city", "UnknownCity123"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("City not found. Check the spelling and try again."));
    }

    @Test
    void testWeatherEndpointUpstreamFailureReturns502() throws Exception {
        when(weatherService.getWeather("FailCity"))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The weather service is not responding. Try again in a moment."));

        mockMvc.perform(get("/api/weather").param("city", "FailCity"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.message").value("The weather service is not responding. Try again in a moment."));
    }

    @Test
    void testCitiesEndpointReturnsSuggestions() throws Exception {
        ArrayNode arr = mapper.createArrayNode();
        ObjectNode cityNode = mapper.createObjectNode();
        cityNode.put("name", "London");
        cityNode.put("country", "United Kingdom");
        arr.add(cityNode);

        when(weatherService.searchCities("lon")).thenReturn(arr);

        mockMvc.perform(get("/api/cities").param("q", "lon"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("London"));
    }
}
