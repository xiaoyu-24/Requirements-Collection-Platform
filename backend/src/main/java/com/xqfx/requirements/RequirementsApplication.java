package com.xqfx.requirements;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RequirementsApplication {

    public static void main(String[] args) {
        SpringApplication.run(RequirementsApplication.class, args);
    }
}
