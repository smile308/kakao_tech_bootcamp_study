import java.util.*;
import practice.*;


public class Main {
    public static void printCarModels(List<? extends Car> cars)
    {
        for(Car car : cars)
        {
            car.printModel();
        }
    }
    public static void main(String[] args){
        List<ElectricCar> electricCars = Arrays.asList(new ElectricCar("Model 3"), new ElectricCar("Model 4"));
        List<GasolineCar> gasolineCars = Arrays.asList(new GasolineCar("VS4"), new GasolineCar("VS7"));

        printCarModels(electricCars);
        printCarModels(gasolineCars);
    }
}
