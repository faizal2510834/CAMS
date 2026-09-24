package com.cams.service;

public class MasterDataNotFoundException extends RuntimeException {
    public MasterDataNotFoundException(String message) {
        super(message);
    }
}
