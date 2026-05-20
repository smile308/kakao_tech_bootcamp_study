package practice;

public class ChargingStation {
    private volatile boolean available = false;

    public void openStation(){
        System.out.println("충전 가능 상태로 전환");
        available=true;
    }
    public boolean isAvailable(){
        return available;
    }
}
