package org.humsaadhak;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * HumSaadhak 2.0 — one Spring Boot application, twelve business modules.
 * Each top-level package below this one is a Spring Modulith module; see ModularityTests.
 */
@SpringBootApplication
@EnableScheduling
public class HumSaadhakApplication {
    public static void main(String[] args) {
        SpringApplication.run(HumSaadhakApplication.class, args);
    }
}
