import java.util.*;
import practice.*;

public class Main {
    public static void main(String[] args){
        FuelStation station = new FuelStation();

        Runnable electricCar = () -> station.refuel("전기차");
        Runnable gasolineCar = () -> station.refuel("가솔린차");

        new Thread(electricCar).start();
        new Thread(gasolineCar).start();

    }
}
