package kr.adapterz.springdatajpa.exception;

public class AuthException extends RuntimeException{
    public AuthException(String message) {
        super(message);
    }
}
