package kr.adapterz.springdatajpa.exception;

public class AuthException extends RuntimeException{
    public AuthException() {
        super("invalid_request");
    }
}
