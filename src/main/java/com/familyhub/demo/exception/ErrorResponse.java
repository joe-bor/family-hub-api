package com.familyhub.demo.exception;

import java.time.Instant;

public record ErrorResponse(int httpStatus,String path, String message, Instant timeStamp) {
    public ErrorResponse(int httpStatus, String path, String message){
        this(httpStatus, path, message, Instant.now());
    }
}
