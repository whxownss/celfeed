package com.xowns.celfeed.server.social.producer.exception;

public class ProducerNonRetriableException extends ProducerSendException {
    public ProducerNonRetriableException(Throwable cause) {
        super("재시도 불가능한 예외", cause);
    }
}
