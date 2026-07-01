package com.oppshan.washa.exception;

/**
 * A domain / business-rule violation. It carries a {@link MessageCode} (an i18n key, also handed to
 * {@code super()} as the exception message) and the HTTP status to respond with.
 *
 * <p>The constructor is private on purpose: every code is raised through a named static factory
 * ({@code userNotFound()}, {@code accessDenied()}, ...) that reads as intent rather than a
 * {@code new} with a bare status literal. Add a factory when you add a code. The status isn't
 * uniform across them: auth failures map to 401/403, a missing user to 404, and the salary-preset
 * rules to 400.
 */
public class BusinessException extends RuntimeException {

    private final MessageCode messageCode;
    private final int status;

    /** Private on purpose; raise every code through the static factories below. */
    private BusinessException(MessageCode messageCode,
                              int status) {
        super(messageCode.getKey());
        this.messageCode = messageCode;
        this.status = status;
    }

    /** The {@link MessageCode} this violation carries. */
    public MessageCode getMessageCode() {
        return messageCode;
    }

    /** The HTTP status to respond with. */
    public int getStatus() {
        return status;
    }

    /** The request needs a signed-in user but has none; HTTP 401. */
    public static BusinessException authenticationRequired() {
        return new BusinessException(MessageCode.AUTHENTICATION_REQUIRED, 401);
    }

    /** The identity is authenticated but not permitted; HTTP 403. */
    public static BusinessException accessDenied() {
        return new BusinessException(MessageCode.ACCESS_DENIED, 403);
    }

    /** No matching user account; HTTP 404. */
    public static BusinessException userNotFound() {
        return new BusinessException(MessageCode.USER_NOT_FOUND, 404);
    }

    /** No matching salary preset; HTTP 400. */
    public static BusinessException salaryPresetNotFound() {
        return new BusinessException(MessageCode.SALARY_PRESET_NOT_FOUND, 400);
    }

    /** The targeted salary preset is a built-in; HTTP 400. */
    public static BusinessException salaryPresetBuiltIn() {
        return new BusinessException(MessageCode.SALARY_PRESET_BUILT_IN, 400);
    }
}
