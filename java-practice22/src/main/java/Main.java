import java.util.*;
import practice.*;

public class Main {
    public static void main(String[] args){
        Counter counter=new Counter();
        IncrementThread Thread1 = new IncrementThread(counter);
        IncrementThread Thread2 = new IncrementThread(counter);

        Thread1.start();
        Thread2.start();

        try {
            Thread1.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        try {
            Thread2.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println(counter.getValue());
    }
}
