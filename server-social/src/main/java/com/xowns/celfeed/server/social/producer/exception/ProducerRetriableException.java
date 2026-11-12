package com.xowns.celfeed.server.social.producer.exception;

public class ProducerRetriableException extends ProducerSendException {
    public ProducerRetriableException(Throwable cause) {
        super(cause);
    }
}
