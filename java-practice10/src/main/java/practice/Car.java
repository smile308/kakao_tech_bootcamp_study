package practice;

public class Car {
    protected String model;
    public Car(String model){
        this.model=model;
    }
    public void printModel(){
        System.out.println("모델명: "+ model);
    }
}
