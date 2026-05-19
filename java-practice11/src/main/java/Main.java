import java.util.*;
import practice.*;


public class Main {

    public static void addSuvs(List<? super Suv> suvs){
        suvs.add(new Suv("Tuc"));
        suvs.add(new ElectricSuv("Model X"));
    }

    public static void main(String[] args){
        List<Car> carList = new ArrayList<>();
        List<Suv> suvList = new ArrayList<>();
        List<ElectricSuv> electricSuvs = new ArrayList<>();

        addSuvs(carList);
        addSuvs(suvList);

        System.out.println("carList -> "+carList);
        System.out.println("suvList -> "+suvList);
    }

}
