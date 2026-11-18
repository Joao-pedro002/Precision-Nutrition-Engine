package com.precisionnutrition;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PrecisionNutritionApplication {

    public static void main(String[] args) {
        SpringApplication.run(PrecisionNutritionApplication.class, args);
    }
}
