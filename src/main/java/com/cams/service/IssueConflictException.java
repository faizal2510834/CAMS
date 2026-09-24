package com.cams.service;

public class IssueConflictException extends RuntimeException {
    public IssueConflictException(String message) {
        super(message);
    }
}
