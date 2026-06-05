package kr.adapterz.springdatajpa.exception;

public class DataNullException extends RuntimeException{
    public DataNullException(){
        super("data_null");
    }
}
