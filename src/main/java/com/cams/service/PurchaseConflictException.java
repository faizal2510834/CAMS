package com.cams.service;

public class PurchaseConflictException extends RuntimeException {
    public PurchaseConflictException(String message) {
        super(message);
    }
}
