package practice;
import practice.*;

public class Suv extends Car {
    public Suv(String model)
    {
        super(model);
    }
    @Override
    public String toString(){
        return "Suv: "+super.toString();
    }
}
