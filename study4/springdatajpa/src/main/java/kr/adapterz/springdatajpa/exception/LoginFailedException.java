package kr.adapterz.springdatajpa.exception;

public class LoginFailedException extends RuntimeException {

    public LoginFailedException() {
        super("login_failed");
    }
}