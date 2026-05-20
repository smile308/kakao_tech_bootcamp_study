import java.util.*;
import practice.*;



public class Main {
    public static void main(String[] args) throws InterruptedException{
        ChargingStation chargingStation = new ChargingStation();

        Car a = new Car("A",chargingStation);
        Car b = new Car("B",chargingStation);

        Thread aThread = new Thread(a);
        Thread bThread = new Thread(b);

        aThread.start();
        bThread.start();

        Thread.sleep(1000);
        chargingStation.openStation();


        aThread.join();
        bThread.join();
    }
}
