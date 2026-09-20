package com.cryptolab.exception;

public class ProxySwitchException extends RuntimeException {
    public ProxySwitchException(String message, Throwable cause) {
        super(message, cause);
    }

    public ProxySwitchException(String message) {
        super(message);
    }
}
