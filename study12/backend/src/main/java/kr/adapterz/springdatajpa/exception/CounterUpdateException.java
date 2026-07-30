package kr.adapterz.springdatajpa.exception;

public class CounterUpdateException extends RuntimeException {

    public CounterUpdateException() {
        super("Counter_Update_Failed");
    }
}
