package practice;

public class FuelPump {
    private int fuelCount =0;

    public synchronized void usePump(String carName) {
        System.out.println(carName + "-> 주유 시작");
        fuelCount++;
        sleep(200);
        System.out.println(carName+"->주유 완료 주유 횟수: "+ fuelCount);
    }

    private void sleep(int ms){
        try{Thread.sleep(ms);
    }catch(InterruptedException ignored){

        }
}
}
