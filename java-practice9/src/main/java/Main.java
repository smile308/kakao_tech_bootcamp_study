import java.util.*;
import practice.*;


public class Main{
    public static void printAnyList(List<?> list){
        for(Object obj : list){
            System.out.println(obj);
        }
    }


    public static void main(String[] args){
        List<Car> cars = Arrays.asList(new Car("Avante"),new Car("BMW"));
        List<String> brands = Arrays.asList("현대","기아");

        printAnyList(cars);
        printAnyList(brands);

    }
}