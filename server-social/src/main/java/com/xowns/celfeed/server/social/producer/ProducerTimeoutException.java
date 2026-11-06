package com.xowns.celfeed.server.social.producer;

public class ProducerTimeoutException extends ProducerSendException {
    public ProducerTimeoutException(Throwable cause) {
        super(cause);
    }
}
