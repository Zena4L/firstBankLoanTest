package com.clement.loanapp.common;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.NotNull;
import org.apache.coyote.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);


    @Override
    protected ResponseEntity<@NotNull Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        List<String> errors = ex.getBindingResult().getFieldErrors()
                .stream()
                .map(this::formatFieldError)
                .toList();

        log.error("Validation error: {}", errors);
        return exceptionResponseBuilder(String.join(", ", errors), HttpStatus.BAD_REQUEST);
    }


    @ExceptionHandler({ConstraintViolationException.class, TransactionSystemException.class})
    public ResponseEntity<Object> handleConstraintViolationException(
            ConstraintViolationException ex) {
        List<String> errors =
                ex.getConstraintViolations().stream().map(ConstraintViolation::getMessage).toList();
        return exceptionResponseBuilder(String.join(", ", errors), HttpStatus.BAD_REQUEST);
    }


    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Object> handle404Exception(NotFoundException ex) {
        log.error("Resource not found: ", ex);
        return exceptionResponseBuilder(ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(DuplicateException.class)
    public ResponseEntity<Object> handle409Exception(DuplicateException ex) {
        log.error("Conflict error", ex);
        return exceptionResponseBuilder(ex.getMessage(), HttpStatus.CONFLICT);
    }


    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<Object> handle400Exception(BadRequestException ex) {
        log.error("Bad request error", ex);
        return exceptionResponseBuilder(ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(UnprocessableExceptionException.class)
    public ResponseEntity<Object> handle429Exception(UnprocessableExceptionException ex) {
        log.error("Unprocessable exception", ex);
        return exceptionResponseBuilder(ex.getMessage(), HttpStatus.UNPROCESSABLE_CONTENT);
    }


    private String formatFieldError(FieldError error) {
        String property = error.getField();
        if (property.startsWith("content.")) {
            property = property.substring(8);
        }
        return property + ": " + error.getDefaultMessage();
    }

    private ResponseEntity<Object> exceptionResponseBuilder(String message, HttpStatus status) {
        ProblemDetail problemDetail = ProblemDetail.forStatus(status);
        problemDetail.setStatus(status.value());
        problemDetail.setTitle(status.getReasonPhrase());
        problemDetail.setDetail(message);
        return ResponseEntity.status(status).body(problemDetail);
    }
}