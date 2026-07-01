package com.oppshan.washa.exception;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The backend's half of the {@code MessageCode} contract shared with the frontend: the TS enum
 * ({@code message-code.ts}) and the i18n keys in {@code en.json}. Each constant's string IS an
 * en.json key path; Jackson emits it via {@link JsonValue}, so that key is what ships in an error body
 * or a redirect query and the frontend resolves it to a localized message. TS is the superset (it adds
 * frontend-only info and validation codes); this enum carries just the codes the backend side needs.
 *
 * <p>Most codes are emitted by the backend. {@code UNKNOWN} (the frontend's fallback for an
 * unrecognized code) and {@code SIGN_IN_FAILED} aren't produced by any current backend path; they
 * exist here only to stay byte-for-byte aligned with the TS enum. Change a value in lockstep across
 * Java, TS, and en.json, or the wire contract drifts (root CLAUDE.md C.1).
 */
public enum MessageCode {
    UNKNOWN("messages.errors.unknown"),
    AUTHENTICATION_REQUIRED("messages.errors.authenticationRequired"),
    ACCESS_DENIED("messages.errors.accessDenied"),
    SIGN_IN_FAILED("messages.errors.signInFailed"),
    USER_NOT_FOUND("messages.errors.userNotFound"),
    SALARY_PRESET_NOT_FOUND("messages.errors.salaryPresetNotFound"),
    SALARY_PRESET_BUILT_IN("messages.errors.salaryPresetBuiltIn");

    private final String key;

    /** Binds each constant to its en.json key path. */
    MessageCode(String key) {
        this.key = key;
    }

    /** The en.json key path; Jackson serializes this as the code's wire value via {@code @JsonValue}. */
    @JsonValue
    public String getKey() {
        return key;
    }
}
