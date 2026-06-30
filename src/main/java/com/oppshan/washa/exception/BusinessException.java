package com.oppshan.washa.exception;

public class BusinessException extends RuntimeException {

    private final MessageCode messageCode;
    private final int status;

    private BusinessException(MessageCode messageCode,
                              int status) {
        super(messageCode.getKey());
        this.messageCode = messageCode;
        this.status = status;
    }

    public MessageCode getMessageCode() {
        return messageCode;
    }

    public int getStatus() {
        return status;
    }

    public static BusinessException authenticationRequired() {
        return new BusinessException(MessageCode.AUTHENTICATION_REQUIRED, 401);
    }

    public static BusinessException accessDenied() {
        return new BusinessException(MessageCode.ACCESS_DENIED, 403);
    }

    public static BusinessException userNotFound() {
        return new BusinessException(MessageCode.USER_NOT_FOUND, 404);
    }

    public static BusinessException salaryPresetNotFound() {
        return new BusinessException(MessageCode.SALARY_PRESET_NOT_FOUND, 400);
    }

    public static BusinessException salaryPresetBuiltIn() {
        return new BusinessException(MessageCode.SALARY_PRESET_BUILT_IN, 400);
    }
}
