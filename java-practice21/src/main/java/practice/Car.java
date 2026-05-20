package practice;


public class Car implements Runnable{
    private final String name;
    private final ChargingStation station;

    public Car(String name, ChargingStation station)
    {
        this.name=name;
        this.station=station;
    }
    @Override
    public void run(){
        System.out.println("충전 대기 중");
        while(!station.isAvailable()){

        }
        System.out.println("충전 시작");
        try{
            Thread.sleep(400);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        System.out.println(name+"충전 완료");
    }
}
