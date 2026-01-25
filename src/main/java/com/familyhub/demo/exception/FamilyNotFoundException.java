package com.familyhub.demo.exception;

import java.util.UUID;

public class FamilyNotFoundException extends RuntimeException{
    public FamilyNotFoundException(UUID id) {
        super("Family not found with id: " + id);
    }

    public FamilyNotFoundException(String username) {
        super("Family with username " + username + " does not exist");
    }
}
