package com.xowns.celfeed.server.social.producer.exception;

public class ProducerInterruptedException extends ProducerSendException {
    public ProducerInterruptedException(Throwable cause) {
        super(cause);
    }
}
