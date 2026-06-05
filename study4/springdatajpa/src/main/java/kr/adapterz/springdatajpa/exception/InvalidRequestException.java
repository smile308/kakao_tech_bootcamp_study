package kr.adapterz.springdatajpa.exception;

public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException() {
        super("invalid_request");
    }
}