package com.example.metrics;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequiredArgsConstructor
public class MainRestService {
    private final MetricsService metricsService;


    @GetMapping(value = "/creation")
    public void createUser() throws IOException {
        metricsService.createUser();
    }

    @GetMapping(value = "/deletion")
    public void deleteUser() throws IOException {
        metricsService.deleteUser();
    }
}
