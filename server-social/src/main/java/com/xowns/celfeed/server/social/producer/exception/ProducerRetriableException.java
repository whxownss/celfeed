package com.xowns.celfeed.server.social.producer.exception;

public class ProducerRetriableException extends ProducerSendException {
    public ProducerRetriableException(Throwable cause) {
        super("재시도 가능한 예외", cause);
    }
}
