import practice.*;

import java.util.*;

public class Main {
    public static void main(String[] args) throws InterruptedException{
        FuelPump pump = new FuelPump();
        EntranceGate gate = new EntranceGate();
        ParkingLot lot = new ParkingLot();

        Runnable carTask=()->{
          String carName=Thread.currentThread().getName();
          gate.passThrough(carName);
          pump.usePump(carName);
          lot.enter(carName);
          Fuellogger.writeLog(carName);
        };
        Thread avante = new Thread(carTask, "Avante");
        Thread sonata = new Thread(carTask, "Sonata");
        Thread grandeur = new Thread(carTask, "Grandeur");

        avante.start();
        sonata.start();
        grandeur.start();

        avante.join();
        sonata.join();
        grandeur.join();


    }
}
