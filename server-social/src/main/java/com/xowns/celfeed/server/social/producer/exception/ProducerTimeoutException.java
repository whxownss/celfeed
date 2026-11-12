package com.xowns.celfeed.server.social.producer.exception;

public class ProducerTimeoutException extends ProducerSendException {
    public ProducerTimeoutException(Throwable cause) {
        super("타임아웃 발생", cause);
    }
}
