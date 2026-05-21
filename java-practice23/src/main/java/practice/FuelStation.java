package practice;

import java.util.concurrent.locks.ReentrantLock;

public class FuelStation {
    private final ReentrantLock lock = new ReentrantLock();
    private int fuel = 100;

    public void refuel(String carName){
        if(lock.tryLock()){
            try{
                System.out.println(carName+" 주유소 진입");
                Thread.sleep(200);
                fuel-=10;
                System.out.println(carName+" 주유 완료/남은 연료:"+fuel);
            }catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
            }finally{
                lock.unlock();
            }
        }else{
            System.out.println(carName+" 주유 포기");
        }
    }
}
