package com.xowns.celfeed.server.social.producer.exception;

public class ProducerSendException extends RuntimeException {

    public ProducerSendException(Throwable cause) {
        this("메시지 전송 실패", cause);
    }

    public ProducerSendException(String message, Throwable cause) {
        super(message, cause);
    }
}
