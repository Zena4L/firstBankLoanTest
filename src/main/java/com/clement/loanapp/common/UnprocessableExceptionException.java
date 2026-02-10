package com.clement.loanapp.common;

public class UnprocessableExceptionException extends RuntimeException{
    public UnprocessableExceptionException(String message) {
        super(message);
    }
}