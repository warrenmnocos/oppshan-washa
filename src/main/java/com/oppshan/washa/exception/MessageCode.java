package com.oppshan.washa.exception;

public enum MessageCode {
    UNKNOWN("messages.errors.unknown"),
    AUTHENTICATION_REQUIRED("messages.errors.authenticationRequired"),
    ACCESS_DENIED("messages.errors.accessDenied"),
    USER_NOT_FOUND("messages.errors.userNotFound");

    private final String key;

    MessageCode(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }
}
