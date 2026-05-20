package practice;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


public class ParkingLot {
    private final Lock lock = new ReentrantLock();
    private int availableSpots=2;

    public void tryPark(String carName){
        lock.lock();
        try {
            if (availableSpots > 0) {
                System.out.println(carName + "주차 시작");
                availableSpots--;
                Thread.sleep(300);
                System.out.println(carName + "주차 완료/남은 자리:" + availableSpots);
            } else {
                System.out.println(carName + "주차 실패");
            }
        }catch(InterruptedException e){
                Thread.currentThread().interrupt();
            }finally{
                lock.unlock();
            }
        }
    }
