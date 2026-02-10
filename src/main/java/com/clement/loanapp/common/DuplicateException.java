package com.clement.loanapp.common;

public class DuplicateException extends RuntimeException{
    public DuplicateException(String message) {
        super(message);
    }
}