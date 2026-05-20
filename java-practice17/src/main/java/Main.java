import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {
    public static void main(String[] args)
    {
        ExecutorService repairShop = Executors.newFixedThreadPool(2);


        repairShop.execute(()->handleRepair("Kia"));
        repairShop.execute(()->handleRepair("Hyundai"));

        repairShop.shutdown();


    }

    private static void handleRepair(String carModel){
        System.out.println(carModel+"정비 시작"+Thread.currentThread().getName());
        try{
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        System.out.println(carModel+"종료");
    }
}
