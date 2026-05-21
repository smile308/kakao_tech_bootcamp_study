package practice;

import java.util.concurrent.locks.ReentrantLock;

public class FuelStation {
    private final ReentrantLock lock = new ReentrantLock();
    private int fuel =100;

    public void refuel(String carName){
        try{
            System.out.println(carName+" 주유 대기중");
            lock.lockInterruptibly();
            try{
                System.out.println(carName+" 주유 시작");
                Thread.sleep(600);
                fuel-=10;
                System.out.println(carName+" 주유 완료 / 남은 연료 : "+fuel);
            }finally{
                lock.unlock();
            }
        }catch(InterruptedException e)
        {System.out.println(carName+" 주유 중지");
            Thread.currentThread().interrupt();}

    }

}
