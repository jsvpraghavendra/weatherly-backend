package com.weatherly;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin(origins = "*")
public class HealthController {

    @GetMapping({"/", "/health", "/api/health"})
    public String health() {
        return "Weatherly backend is running";
    }
}
