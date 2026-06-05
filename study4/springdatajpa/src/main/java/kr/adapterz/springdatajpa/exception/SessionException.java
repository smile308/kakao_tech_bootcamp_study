package kr.adapterz.springdatajpa.exception;

public class SessionException extends RuntimeException{
    public SessionException() {
        super("invalid_request");
    }
}
