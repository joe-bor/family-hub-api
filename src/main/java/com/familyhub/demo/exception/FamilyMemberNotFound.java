package com.familyhub.demo.exception;

import java.util.UUID;

public class FamilyMemberNotFound extends RuntimeException {

    public FamilyMemberNotFound(UUID id) {
        super("Family Member with id: " + id + " not found");
    }
    public FamilyMemberNotFound(String message) {
        super(message);
    }
}
