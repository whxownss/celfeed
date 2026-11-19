package com.xowns.celfeed.server.consumer.exception;

public class RetryableConsumerException extends RuntimeException {
    public RetryableConsumerException(String message) {
        super(message);
    }

    public RetryableConsumerException(String message, Throwable cause) {
        super(message, cause);
    }
}
