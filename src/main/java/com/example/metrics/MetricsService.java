package com.example.metrics;

import com.github.javafaker.Faker;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.util.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Service
public class MetricsService {
    @Value("${sla.interval}")
    private String interval;
    @Value("${sla.percent}")
    private Double percent;
    private Double percentValue = 1.0;
    @Value("${sla.duration.user-create-max}")
    private Double userCreateDuration;
    @Value("${sla.duration.user-delete-max}")
    private Double userDeleteDuration;
    @Autowired
    private MeterRegistry meterRegistry;
    private Counter usersCreated;
    private Counter usersDeleted;
    private Counter requestsTotal;
    private Counter userDeletionErrors;
    private Counter userCreationErrors;

    private Gauge gauge;
    private Double userCreation = 0.0;
    private Double userDeletion = 0.0;

    private Gauge badMinutesGauge;
    private List<String> usernames = new ArrayList<>();
    private final Faker faker = new Faker();

    @PostConstruct
    public void init() {
        percentValue = 100.0;
        requestsTotal = Counter.builder("requests_total").description("number of requests").register(meterRegistry);
        usersCreated = Counter.builder("user_created").description("number of users created").register(meterRegistry);

        usersDeleted = Counter.builder("user_deleted").description("number of users deleted").register(meterRegistry);

        userDeletionErrors = Counter.builder("user_delete_error").description("number of user deletion errors").register(meterRegistry);
        userCreationErrors = Counter.builder("user_create_error").description("number of user creation errors").register(meterRegistry);

        gauge = Gauge.builder("availability", this, MetricsService::getPercent).description("Requests availability").register(meterRegistry);

        badMinutesGauge = Gauge.builder("bad_minutes", this, MetricsService::getRequestMaxTime).description("Bad minutes").register(meterRegistry);
    }

    public Double getPercent() {
        return percentValue;
    }

    public Double getRequestMaxTime() {
        if (userCreation > userCreateDuration || userDeletion > userDeleteDuration) {
            return 1.0;
        }
        return 0.0;
    }

    @Timed(value = "user.creating.time", description = "Time taken to create user")
    public void createUser() {
        try {
            String username = faker.name().username();
            URL url = new URL("http://localhost:8081/api/v0/users");
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json");
            con.setDoOutput(true);
            String jsonInputString = "{\"name\": \"" + username + "\"}";
            try (OutputStream os = con.getOutputStream()) {
                byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            int code = con.getResponseCode();
            if (code == 200 || code == 201 || code == 204) {
                usernames.add(username);
                usersCreated.increment();
                System.out.println("user created: " + usernames.get(usernames.size() - 1));
            } else {
                userCreationErrors.increment();
            }
        } catch (Exception e) {
            userCreationErrors.increment();
        }
        requestsTotal.increment();
        gauge.measure();
        badMinutesGauge.measure();
    }

    @Timed(value = "user.deletion.time", description = "Time taken to delete user")
    public void deleteUser() {
        try {
            if (!usernames.isEmpty()) {
                System.out.println("user deleted: " + usernames.get(usernames.size() - 1));
                URL url = new URL("http://localhost:8081/api/v0/users/" + usernames.remove(0));
                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                con.setRequestMethod("DELETE");
                con.setRequestProperty("Content-Type", "application/json");
                con.setDoOutput(true);
                int code = con.getResponseCode();
                if (code == 200 || code == 204 || code == 201) {
                    usersDeleted.increment();
                } else {
                    userDeletionErrors.increment();
                }
            }else {
                userDeletionErrors.increment();
            }
        } catch (Exception e) {
            userDeletionErrors.increment();
        }
        requestsTotal.increment();
        gauge.measure();
        badMinutesGauge.measure();
    }

    //    получает метрики и фиксирует недоступность или превышение выполнения запроса, процент доступности onCall отображается в метрике availability, превышение времени выполнения запроса в метрике bad_minutes
    public void countSla() throws IOException {
        URL url = new URL("http://localhost:30303/actuator/prometheus?includedNames=user_create_error_total,user_delete_error_total,requests_total,user_creating_time_seconds_max,user_deletion_time_seconds_max");
        URLConnection con = url.openConnection();
        InputStream in = con.getInputStream();
        String body = IOUtils.toString(in, StandardCharsets.UTF_8);
        String[] metricValues = body.split("\n");
        List<String> metrics = new ArrayList<>(List.of(metricValues[2], metricValues[5], metricValues[8], metricValues[11], metricValues[14]));
        HashMap<String, Double> metrics2 = new HashMap<>();
        Double value;
        for (int i = 0; i < metrics.size(); i++) {
            String[] split = metrics.get(i).split(" ");
            if (split.length == 1) {
                value = Double.parseDouble(split[0]);
            } else {
                value = Double.parseDouble(split[1]);
            }
            if (metrics.get(i).startsWith("user_create_error_total")) {
                metrics2.put("user_create_error_total", value);
            }
            if (metrics.get(i).startsWith("user_delete_error_total")) {
                metrics2.put("user_delete_error_total", value);
            }
            if (metrics.get(i).startsWith("requests_total")) {
                metrics2.put("requests_total", value);
            }
            if (metrics.get(i).startsWith("user_creating_time_seconds_max")) {
                metrics2.put("user_creating_time_seconds_max", value);
            }
            if (metrics.get(i).startsWith("user_deletion_time_seconds_max")) {
                metrics2.put("user_deletion_time_seconds_max", value);
            }
        }
        if (metrics2.get("user_create_error_total") != null && metrics2.get("user_delete_error_total") != null && metrics2.get("requests_total") != null) {
            System.out.println(percentValue);
            percentValue = 100 - ((metrics2.get("user_create_error_total") + metrics2.get("user_delete_error_total")) / metrics2.get("requests_total")) * 100;
        }
        if (metrics2.get("user_deletion_time_seconds_max") != null && metrics2.get("user_creating_time_seconds_max") != null) {
            userCreation = metrics2.get("user_deletion_time_seconds_max");
            userDeletion = metrics2.get("user_creating_time_seconds_max");
        }
        gauge.measure();
        badMinutesGauge.measure();
    }
}
