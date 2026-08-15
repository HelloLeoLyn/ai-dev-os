package com.aidevos.orchestrator.execution.workspace;

public class PromotionException extends IllegalStateException {
    private final String errorCode;

    public PromotionException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public PromotionException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode(){return errorCode;}
}
