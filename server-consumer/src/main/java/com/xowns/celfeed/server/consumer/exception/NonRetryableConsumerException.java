package com.xowns.celfeed.server.consumer.exception;

public class NonRetryableConsumerException extends RuntimeException {
    public NonRetryableConsumerException(String message) {
        super(message);
    }

    public NonRetryableConsumerException(String message, Throwable cause) {
        super(message, cause);
    }
}
