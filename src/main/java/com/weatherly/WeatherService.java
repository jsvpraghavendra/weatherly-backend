package com.weatherly;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class WeatherService {

    private static final String GEO_DIRECT_URL =
            "https://api.openweathermap.org/geo/1.0/direct?q={q}&limit={limit}&appid={key}";

    private static final String GEO_REVERSE_URL =
            "https://api.openweathermap.org/geo/1.0/reverse?lat={lat}&lon={lon}&limit=1&appid={key}";

    private static final String CURRENT_URL =
            "https://api.openweathermap.org/data/2.5/weather?lat={lat}&lon={lon}&appid={key}&units=metric";

    private static final String FORECAST_URL =
            "https://api.openweathermap.org/data/2.5/forecast?lat={lat}&lon={lon}&appid={key}&units=metric";

    private static final DateTimeFormatter ISO_LOCAL_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    private final RestClient client = RestClient.create();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String apiKey;

    public WeatherService(@Value("${weather.api.key:}") String apiKey) {
        String key = (apiKey == null || apiKey.isBlank())
                ? System.getenv("OPENWEATHER_API_KEY")
                : apiKey;
        this.apiKey = (key == null) ? "" : key.trim();
    }

    public boolean hasApiKey() {
        return !this.apiKey.isBlank();
    }

    public ObjectNode getWeather(String city) {
        if (city == null || city.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a city name.");
        }
        try {
            JsonNode geo = client.get()
                    .uri(GEO_DIRECT_URL, city.trim(), 1, apiKey)
                    .retrieve()
                    .body(JsonNode.class);

            if (geo != null && geo.isArray() && !geo.isEmpty()) {
                JsonNode place = geo.get(0);
                double lat = place.path("lat").asDouble();
                double lon = place.path("lon").asDouble();
                String name = place.path("name").asText(city.trim());
                String region = place.path("state").asText("");
                String country = place.path("country").asText("");
                return forecast(lat, lon, name, region, country);
            }

            // Fallback: search directly by city name using the current weather API
            JsonNode weatherFallback = client.get()
                    .uri("https://api.openweathermap.org/data/2.5/weather?q={city}&appid={key}&units=metric", city.trim(), apiKey)
                    .retrieve()
                    .body(JsonNode.class);

            if (weatherFallback != null && weatherFallback.has("coord")) {
                double lat = weatherFallback.path("coord").path("lat").asDouble();
                double lon = weatherFallback.path("coord").path("lon").asDouble();
                String name = weatherFallback.path("name").asText(city.trim());
                String country = weatherFallback.path("sys").path("country").asText("");
                return forecast(lat, lon, name, "", country);
            }

            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "City not found. Check the spelling and try again.");
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "City not found. Check the spelling and try again.");
        } catch (RestClientException e) {
            throw unavailable();
        }
    }

    public ObjectNode forecast(double lat, double lon, String name, String region, String country) {
        try {
            JsonNode currentWeather = client.get()
                    .uri(CURRENT_URL, lat, lon, apiKey)
                    .retrieve()
                    .body(JsonNode.class);

            JsonNode forecastData = client.get()
                    .uri(FORECAST_URL, lat, lon, apiKey)
                    .retrieve()
                    .body(JsonNode.class);

            if (currentWeather == null || currentWeather.path("main").isMissingNode()) {
                throw unavailable();
            }

            // Fill missing region or name via reverse geocoding if needed
            if (region == null || region.isBlank()) {
                try {
                    JsonNode reverse = client.get()
                            .uri(GEO_REVERSE_URL, lat, lon, apiKey)
                            .retrieve()
                            .body(JsonNode.class);
                    if (reverse != null && reverse.isArray() && !reverse.isEmpty()) {
                        region = reverse.get(0).path("state").asText("");
                        if (name == null || name.isBlank() || "Selected place".equalsIgnoreCase(name)) {
                            name = reverse.get(0).path("name").asText(name);
                        }
                        if (country == null || country.isBlank()) {
                            country = reverse.get(0).path("country").asText(country);
                        }
                    }
                } catch (Exception ignored) {
                }
            }

            if (name == null || name.isBlank() || "Selected place".equalsIgnoreCase(name)) {
                name = currentWeather.path("name").asText("Selected place");
            }
            if (country == null || country.isBlank()) {
                country = currentWeather.path("sys").path("country").asText("");
            }

            int timezoneOffsetSeconds = currentWeather.path("timezone").asInt(0);
            ZoneOffset offset = ZoneOffset.ofTotalSeconds(timezoneOffsetSeconds);

            long currentDt = currentWeather.path("dt").asLong(Instant.now().getEpochSecond());
            LocalDateTime localNow = Instant.ofEpochSecond(currentDt).atOffset(offset).toLocalDateTime();
            String currentTimeStr = localNow.format(ISO_LOCAL_FMT);

            long sunriseEpoch = currentWeather.path("sys").path("sunrise").asLong(0);
            long sunsetEpoch = currentWeather.path("sys").path("sunset").asLong(0);

            int isDay = (currentDt >= sunriseEpoch && currentDt <= sunsetEpoch) ? 1 : 0;
            String icon = currentWeather.path("weather").isArray() && !currentWeather.path("weather").isEmpty()
                    ? currentWeather.path("weather").get(0).path("icon").asText("")
                    : "";
            if (icon.endsWith("d")) {
                isDay = 1;
            } else if (icon.endsWith("n")) {
                isDay = 0;
            }

            int currentOwmId = currentWeather.path("weather").isArray() && !currentWeather.path("weather").isEmpty()
                    ? currentWeather.path("weather").get(0).path("id").asInt(800)
                    : 800;
            int currentWmo = owmToWmo(currentOwmId);

            double currentTemp = currentWeather.path("main").path("temp").asDouble();
            double feelsLike = currentWeather.path("main").path("feels_like").asDouble(currentTemp);
            int humidity = currentWeather.path("main").path("humidity").asInt(0);
            int clouds = currentWeather.path("clouds").path("all").asInt(0);
            double windSpeedMps = currentWeather.path("wind").path("speed").asDouble(0.0);
            double windSpeedKmh = Math.round(windSpeedMps * 3.6 * 10.0) / 10.0;
            double windDeg = currentWeather.path("wind").path("deg").asDouble(0.0);
            double pressure = currentWeather.path("main").path("pressure").asDouble(1013.0);
            double rain1h = currentWeather.path("rain").path("1h").asDouble(0.0);
            double snow1h = currentWeather.path("snow").path("1h").asDouble(0.0);
            double precipitation = Math.round((rain1h + snow1h) * 10.0) / 10.0;

            ObjectNode currentObj = mapper.createObjectNode();
            currentObj.put("time", currentTimeStr);
            currentObj.put("temperature_2m", currentTemp);
            currentObj.put("relative_humidity_2m", humidity);
            currentObj.put("apparent_temperature", feelsLike);
            currentObj.put("is_day", isDay);
            currentObj.put("precipitation", precipitation);
            currentObj.put("weather_code", currentWmo);
            currentObj.put("cloud_cover", clouds);
            currentObj.put("wind_speed_10m", windSpeedKmh);
            currentObj.put("wind_direction_10m", windDeg);
            currentObj.put("surface_pressure", pressure);

            // Parse 5-day / 3-hour forecast points
            List<ForecastPoint> points = new ArrayList<>();
            if (forecastData != null && forecastData.path("list").isArray()) {
                for (JsonNode item : forecastData.path("list")) {
                    long dt = item.path("dt").asLong();
                    double temp = item.path("main").path("temp").asDouble();
                    double tempMin = item.path("main").path("temp_min").asDouble(temp);
                    double tempMax = item.path("main").path("temp_max").asDouble(temp);
                    int owmId = item.path("weather").isArray() && !item.path("weather").isEmpty()
                            ? item.path("weather").get(0).path("id").asInt(800)
                            : 800;
                    int wmo = owmToWmo(owmId);
                    double pop = item.path("pop").asDouble(0.0);
                    String pod = item.path("sys").path("pod").asText("d");
                    int itemIsDay = "d".equalsIgnoreCase(pod) ? 1 : 0;
                    LocalDateTime itemLocal = Instant.ofEpochSecond(dt).atOffset(offset).toLocalDateTime();

                    points.add(new ForecastPoint(dt, itemLocal, temp, tempMin, tempMax, wmo, pop, itemIsDay));
                }
            }

            ObjectNode hourlyObj = buildHourly(localNow, offset, currentDt, currentTemp, currentWmo, isDay, points, sunriseEpoch, sunsetEpoch);
            ObjectNode dailyObj = buildDaily(localNow, offset, currentTemp, currentWmo, clouds, points, sunriseEpoch, sunsetEpoch);

            ObjectNode out = mapper.createObjectNode();
            out.put("city", name);
            out.put("region", region == null ? "" : region);
            out.put("country", country == null ? "" : country);
            out.put("lat", lat);
            out.put("lon", lon);
            out.set("current", currentObj);
            out.set("hourly", hourlyObj);
            out.set("daily", dailyObj);
            return out;
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "City not found. Check the spelling and try again.");
        } catch (RestClientException e) {
            throw unavailable();
        }
    }

    public ArrayNode searchCities(String q) {
        ArrayNode out = mapper.createArrayNode();
        if (q == null || q.trim().length() < 2) {
            return out;
        }
        try {
            JsonNode results = client.get()
                    .uri(GEO_DIRECT_URL, q.trim(), 5, apiKey)
                    .retrieve()
                    .body(JsonNode.class);

            if (results != null && results.isArray()) {
                for (JsonNode p : results) {
                    ObjectNode item = mapper.createObjectNode();
                    item.put("name", p.path("name").asText());
                    item.put("region", p.path("state").asText(""));
                    item.put("country", p.path("country").asText(""));
                    item.put("lat", p.path("lat").asDouble());
                    item.put("lon", p.path("lon").asDouble());
                    out.add(item);
                }
            }
        } catch (RestClientException e) {
            // suggestions are optional: return an empty list
        }
        return out;
    }

    private ObjectNode buildHourly(LocalDateTime localNow, ZoneOffset offset, long currentDt,
                                   double currentTemp, int currentWmo, int currentIsDay,
                                   List<ForecastPoint> points, long sunriseEpoch, long sunsetEpoch) {
        ObjectNode hourly = mapper.createObjectNode();
        ArrayNode timeArray = mapper.createArrayNode();
        ArrayNode tempArray = mapper.createArrayNode();
        ArrayNode codeArray = mapper.createArrayNode();
        ArrayNode isDayArray = mapper.createArrayNode();
        ArrayNode popArray = mapper.createArrayNode();

        LocalDateTime startHour = localNow.truncatedTo(ChronoUnit.HOURS);

        for (int i = 0; i < 24; i++) {
            LocalDateTime target = startHour.plusHours(i);
            long targetEpoch = target.toEpochSecond(offset);
            timeArray.add(target.format(ISO_LOCAL_FMT));

            if (i == 0) {
                tempArray.add(Math.round(currentTemp * 10.0) / 10.0);
                codeArray.add(currentWmo);
                isDayArray.add(currentIsDay);
                double firstPop = points.isEmpty() ? 0.0 : points.get(0).pop;
                popArray.add((int) Math.round(firstPop * 100));
            } else {
                ForecastPoint prev = null;
                ForecastPoint next = null;
                for (ForecastPoint p : points) {
                    if (p.epoch <= targetEpoch) {
                        prev = p;
                    } else {
                        next = p;
                        break;
                    }
                }

                double interpTemp;
                int wmo;
                int pop;
                int isDay;

                if (prev == null && next != null) {
                    double fraction = Math.max(0.0, Math.min(1.0, (double) (targetEpoch - currentDt) / Math.max(1, next.epoch - currentDt)));
                    interpTemp = currentTemp + fraction * (next.temp - currentTemp);
                    wmo = fraction < 0.5 ? currentWmo : next.wmo;
                    pop = (int) Math.round(next.pop * 100 * fraction);
                    isDay = next.isDay;
                } else if (prev != null && next != null) {
                    double fraction = Math.max(0.0, Math.min(1.0, (double) (targetEpoch - prev.epoch) / Math.max(1, next.epoch - prev.epoch)));
                    interpTemp = prev.temp + fraction * (next.temp - prev.temp);
                    wmo = fraction < 0.5 ? prev.wmo : next.wmo;
                    pop = (int) Math.round((prev.pop + fraction * (next.pop - prev.pop)) * 100);
                    isDay = next.isDay;
                } else if (prev != null) {
                    interpTemp = prev.temp;
                    wmo = prev.wmo;
                    pop = (int) Math.round(prev.pop * 100);
                    isDay = prev.isDay;
                } else {
                    interpTemp = currentTemp;
                    wmo = currentWmo;
                    pop = 0;
                    isDay = currentIsDay;
                }

                if (sunriseEpoch > 0 && sunsetEpoch > 0) {
                    long dayOffsetDays = ChronoUnit.DAYS.between(localNow.toLocalDate(), target.toLocalDate());
                    long estSunrise = sunriseEpoch + dayOffsetDays * 86400L;
                    long estSunset = sunsetEpoch + dayOffsetDays * 86400L;
                    isDay = (targetEpoch >= estSunrise && targetEpoch <= estSunset) ? 1 : 0;
                }

                tempArray.add(Math.round(interpTemp * 10.0) / 10.0);
                codeArray.add(wmo);
                isDayArray.add(isDay);
                popArray.add(pop);
            }
        }

        hourly.set("time", timeArray);
        hourly.set("temperature_2m", tempArray);
        hourly.set("weather_code", codeArray);
        hourly.set("is_day", isDayArray);
        hourly.set("precipitation_probability", popArray);
        return hourly;
    }

    private ObjectNode buildDaily(LocalDateTime localNow, ZoneOffset offset,
                                  double currentTemp, int currentWmo, int clouds,
                                  List<ForecastPoint> points, long sunriseEpoch, long sunsetEpoch) {
        ObjectNode daily = mapper.createObjectNode();
        ArrayNode timeArray = mapper.createArrayNode();
        ArrayNode codeArray = mapper.createArrayNode();
        ArrayNode maxTempArray = mapper.createArrayNode();
        ArrayNode minTempArray = mapper.createArrayNode();
        ArrayNode popMaxArray = mapper.createArrayNode();
        ArrayNode sunriseArray = mapper.createArrayNode();
        ArrayNode sunsetArray = mapper.createArrayNode();
        ArrayNode uvArray = mapper.createArrayNode();

        Map<LocalDate, List<ForecastPoint>> byDate = new LinkedHashMap<>();
        LocalDate today = localNow.toLocalDate();
        byDate.put(today, new ArrayList<>());

        for (ForecastPoint p : points) {
            LocalDate d = p.localTime.toLocalDate();
            byDate.computeIfAbsent(d, k -> new ArrayList<>()).add(p);
        }

        double lastMax = currentTemp + 2.0;
        double lastMin = currentTemp - 2.0;
        int lastCode = currentWmo;
        int lastPop = 0;

        int dayCount = 0;
        for (Map.Entry<LocalDate, List<ForecastPoint>> entry : byDate.entrySet()) {
            if (dayCount >= 7) break;
            LocalDate date = entry.getKey();
            List<ForecastPoint> dayPoints = entry.getValue();

            double maxT = currentTemp;
            double minT = currentTemp;
            double maxPop = 0.0;
            int code = currentWmo;

            if (dayPoints.isEmpty()) {
                maxT = currentTemp + 1.0;
                minT = currentTemp - 1.0;
            } else {
                maxT = dayPoints.stream().mapToDouble(p -> p.tempMax).max().orElse(currentTemp);
                minT = dayPoints.stream().mapToDouble(p -> p.tempMin).min().orElse(currentTemp);
                if (date.equals(today)) {
                    maxT = Math.max(maxT, currentTemp);
                    minT = Math.min(minT, currentTemp);
                }
                maxPop = dayPoints.stream().mapToDouble(p -> p.pop).max().orElse(0.0);

                code = dayPoints.stream()
                        .filter(p -> p.localTime.getHour() >= 11 && p.localTime.getHour() <= 15)
                        .findFirst()
                        .map(p -> p.wmo)
                        .orElse(dayPoints.get(dayPoints.size() / 2).wmo);
            }

            lastMax = maxT;
            lastMin = minT;
            lastCode = code;
            lastPop = (int) Math.round(maxPop * 100);

            timeArray.add(date.toString());
            codeArray.add(code);
            maxTempArray.add(Math.round(maxT * 10.0) / 10.0);
            minTempArray.add(Math.round(minT * 10.0) / 10.0);
            popMaxArray.add((int) Math.round(maxPop * 100));

            long sRise = (sunriseEpoch > 0) ? sunriseEpoch + dayCount * 86400L : date.atTime(6, 30).toEpochSecond(offset);
            long sSet = (sunsetEpoch > 0) ? sunsetEpoch + dayCount * 86400L : date.atTime(18, 30).toEpochSecond(offset);
            sunriseArray.add(Instant.ofEpochSecond(sRise).atOffset(offset).format(ISO_LOCAL_FMT));
            sunsetArray.add(Instant.ofEpochSecond(sSet).atOffset(offset).format(ISO_LOCAL_FMT));

            double estUv = Math.max(1.0, Math.round((8.0 * (1.0 - clouds / 100.0)) * 10.0) / 10.0);
            uvArray.add(estUv);

            dayCount++;
        }

        LocalDate lastDate = today.plusDays(dayCount - 1);
        while (dayCount < 7) {
            lastDate = lastDate.plusDays(1);
            timeArray.add(lastDate.toString());
            codeArray.add(lastCode);
            maxTempArray.add(Math.round(lastMax * 10.0) / 10.0);
            minTempArray.add(Math.round(lastMin * 10.0) / 10.0);
            popMaxArray.add(lastPop);

            long sRise = (sunriseEpoch > 0) ? sunriseEpoch + dayCount * 86400L : lastDate.atTime(6, 30).toEpochSecond(offset);
            long sSet = (sunsetEpoch > 0) ? sunsetEpoch + dayCount * 86400L : lastDate.atTime(18, 30).toEpochSecond(offset);
            sunriseArray.add(Instant.ofEpochSecond(sRise).atOffset(offset).format(ISO_LOCAL_FMT));
            sunsetArray.add(Instant.ofEpochSecond(sSet).atOffset(offset).format(ISO_LOCAL_FMT));

            double estUv = Math.max(1.0, Math.round((8.0 * (1.0 - clouds / 100.0)) * 10.0) / 10.0);
            uvArray.add(estUv);

            dayCount++;
        }

        daily.set("time", timeArray);
        daily.set("weather_code", codeArray);
        daily.set("temperature_2m_max", maxTempArray);
        daily.set("temperature_2m_min", minTempArray);
        daily.set("precipitation_probability_max", popMaxArray);
        daily.set("sunrise", sunriseArray);
        daily.set("sunset", sunsetArray);
        daily.set("uv_index_max", uvArray);
        return daily;
    }

    public static int owmToWmo(int owmId) {
        if (owmId >= 200 && owmId < 300) {
            return 95; // Thunderstorm
        }
        if (owmId >= 300 && owmId < 400) {
            return 53; // Drizzle
        }
        if (owmId >= 500 && owmId < 600) {
            if (owmId == 500) return 61; // Light rain
            if (owmId == 501) return 63; // Moderate rain
            if (owmId >= 502 && owmId <= 504) return 65; // Heavy rain
            if (owmId == 511) return 66; // Freezing rain
            if (owmId >= 520 && owmId <= 531) return 80; // Rain shower
            return 63;
        }
        if (owmId >= 600 && owmId < 700) {
            if (owmId == 600) return 71; // Light snow
            if (owmId == 601) return 73; // Snow
            if (owmId == 602) return 75; // Heavy snow
            if (owmId >= 611 && owmId <= 616) return 67; // Sleet / mixed
            if (owmId >= 620) return 85; // Snow showers
            return 73;
        }
        if (owmId >= 700 && owmId < 800) {
            return 45; // Fog/mist/haze
        }
        if (owmId == 800) {
            return 0; // Clear sky
        }
        if (owmId == 801) {
            return 1; // Mainly clear
        }
        if (owmId == 802) {
            return 2; // Partly cloudy
        }
        if (owmId == 803 || owmId == 804) {
            return 3; // Overcast
        }
        return 0;
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "The weather service is not responding. Try again in a moment.");
    }

    private record ForecastPoint(
            long epoch,
            LocalDateTime localTime,
            double temp,
            double tempMin,
            double tempMax,
            int wmo,
            double pop,
            int isDay
    ) {}
}
