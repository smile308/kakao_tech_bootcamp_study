package practice;

public class EntranceGate {
    private final Object lock = new Object();
    private int passedCars=0;

    public void passThrough(String carName){
        System.out.println(carName+"게이트 접근");

        synchronized (lock){
            passedCars++;
            System.out.println(carName+"통과 완료 / 누적 통과:"+passedCars);
        }
    }
}
