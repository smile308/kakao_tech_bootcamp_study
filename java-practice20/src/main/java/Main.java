import java.util.*;

public class Main {
    private static ThreadLocal<String> repairNote = new ThreadLocal<>();

    public static void main(String[] args){
        Runnable task =() ->{
            String carName= Thread.currentThread().getName();
            repairNote.set(carName +"의 정비 요청 기록");

            System.out.println(carName+"에 기록된 내용 :"+repairNote.get());
            repairNote.remove();
        };


        Thread car1 = new Thread(task,"A");
        Thread car2 = new Thread(task, "B");

        car1.start();
        car2.start();
    }
}
