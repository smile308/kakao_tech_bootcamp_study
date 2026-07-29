package kr.adapterz.springdatajpa.exception;

public class PostVersionConflictException extends RuntimeException {

    public PostVersionConflictException() {
        super("Post_Version_Conflict");
    }
}
