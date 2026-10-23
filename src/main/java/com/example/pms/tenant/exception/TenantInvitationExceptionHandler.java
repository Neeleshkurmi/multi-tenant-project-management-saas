package com.example.pms.tenant.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class TenantInvitationExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException exception
    ) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse(
                        "Access denied",
                        List.of(exception.getMessage())
                ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRequest(
            IllegalArgumentException exception
    ) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(
                        "Invalid invitation request",
                        List.of(exception.getMessage())
                ));
    }


    @ExceptionHandler(InvitationExpiredException.class)
    public ResponseEntity<ErrorResponse> handleInvitationExpired(
            InvitationExpiredException exception
    ) {
        return ResponseEntity.status(HttpStatus.GONE)
                .body(new ErrorResponse(
                        "Invitation expired",
                        List.of(exception.getMessage())
                ));
    }

    public record ErrorResponse(
            String message,
            List<String> errors
    ) {}
}