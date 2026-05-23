package practice;
import java.util.*;

public class Car {
    private String model;
    private int year;
    private Optional<String> plateNumber;
    private Optional<String> ownerName;

    public Car(String model, int year, String plateNumber, String ownerName)
    {
        this.model = model;
        this.year = year;
        this.plateNumber = Optional.ofNullable(plateNumber);
        this.ownerName = Optional.ofNullable(ownerName);
    }

    public Optional<String> getPlateNumber(){
        return plateNumber;
    }

    public Optional<String> getOwnerName(){
        return ownerName;
    }

    public String getModel(){
        return model;
    }

    public int getYear(){
        return year;
    }
}
