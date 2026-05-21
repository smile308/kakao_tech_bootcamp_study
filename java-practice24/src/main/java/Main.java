import java.util.*;
import practice.*;
public class Main {
    public static void main(String[] args){
        FuelStation station = new FuelStation();

        Thread t1 = new Thread(() ->station.refuel("A"));
        Thread t2 = new Thread(()->station.refuel("B"));

        t1.start();
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        t2.start();

        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        t2.interrupt();

    }
}
