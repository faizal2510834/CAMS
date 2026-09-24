package com.cams.service;

public class IssueForbiddenException extends RuntimeException {
    public IssueForbiddenException(String message) {
        super(message);
    }
}
