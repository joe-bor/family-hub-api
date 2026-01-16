package com.familyhub.demo.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;


@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(FamilyNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleFamilyNotFound(FamilyNotFoundException ex, HttpServletRequest request) {
        return buildErrorResponse(
                HttpStatus.NOT_FOUND,
                request,
                ex.getMessage()
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error occurred: ", ex);
        return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                request,
                "Internal Server Error"
        );
    }



    private ResponseEntity<ErrorResponse> buildErrorResponse(HttpStatus status, HttpServletRequest request, String message) {
        ErrorResponse error = new ErrorResponse(status.value(), request.getRequestURI(), message);

        return ResponseEntity.status(status).body(error);
    }
}
