package io.github.rjaros87.storage;

public class UserExistsException extends Exception {
    public UserExistsException(String errorMessage) {
        super(errorMessage);
    }
}
