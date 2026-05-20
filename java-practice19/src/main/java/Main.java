import practice.*;


public class Main {
    public static void main(String[] args){
        ParkingLot lot =new ParkingLot();

        Runnable parkingTask =()->{
            String car = Thread.currentThread().getName();
            lot.tryPark(car);
        };

        Thread avante = new Thread(parkingTask, "Avante");
        Thread B = new Thread(parkingTask,"B");
        Thread C = new Thread(parkingTask,"C");

        avante.start();
        B.start();
        C.start();

    }

}
