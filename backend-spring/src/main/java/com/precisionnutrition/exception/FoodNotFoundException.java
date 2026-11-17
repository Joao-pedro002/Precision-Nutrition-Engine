package com.precisionnutrition.exception;

import java.util.UUID;

public class FoodNotFoundException extends RuntimeException {
    public FoodNotFoundException(UUID id) {
        super("Alimento não encontrado com ID: " + id);
    }

    public FoodNotFoundException(String message) {
        super(message);
    }
}
