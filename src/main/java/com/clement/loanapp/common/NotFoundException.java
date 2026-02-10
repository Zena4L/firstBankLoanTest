package com.clement.loanapp.common;

public class NotFoundException extends RuntimeException{
    public NotFoundException(String message) {
        super(message);
    }
}