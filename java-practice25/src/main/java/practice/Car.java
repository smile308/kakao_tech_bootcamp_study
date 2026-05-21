package practice;

public class Car {
    private String model;
    private int year;
    public Car(String model, int year){
        this.model=model;
        this.year=year;
    }

    public String getModel(){
        return model;
    }

   public int getYear(){
        return year;
   }

   @Override
    public String toString(){
        return model + year;
   }

}
