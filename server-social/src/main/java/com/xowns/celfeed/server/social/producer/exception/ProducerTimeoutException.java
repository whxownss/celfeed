package com.xowns.celfeed.server.social.producer.exception;

public class ProducerTimeoutException extends ProducerSendException {
    public ProducerTimeoutException(Throwable cause) {
        super(cause);
    }
}
