package practice;
import practice.*;

public class ElectricSuv extends Suv {
    public ElectricSuv(String model)
    {
        super(model);
    }
    @Override
    public String toString(){
        return "ElectricSuv: "+super.toString();
    }
}
