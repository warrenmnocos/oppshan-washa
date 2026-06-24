package com.oppshan.washa.exception;

import com.fasterxml.jackson.annotation.JsonValue;

public enum MessageCode {
    UNKNOWN("messages.errors.unknown"),
    AUTHENTICATION_REQUIRED("messages.errors.authenticationRequired"),
    ACCESS_DENIED("messages.errors.accessDenied"),
    SIGN_IN_FAILED("messages.errors.signInFailed"),
    USER_NOT_FOUND("messages.errors.userNotFound"),
    SALARY_PRESET_NOT_FOUND("messages.errors.salaryPresetNotFound"),
    SALARY_PRESET_BUILT_IN("messages.errors.salaryPresetBuiltIn");

    private final String key;

    MessageCode(String key) {
        this.key = key;
    }

    @JsonValue
    public String getKey() {
        return key;
    }
}
