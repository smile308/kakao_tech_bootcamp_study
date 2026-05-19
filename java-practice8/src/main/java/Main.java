import java.util.*;
import practice.*;

public class Main {
    public static void main(String[] args){
        Car<String> gasolineCar= new Car<>("가솔린");
        Car<Integer> year = new Car<>(2023);
        System.out.println("연료 종류: "+gasolineCar.gerFeature());
        System.out.println("출시 연도: "+year.gerFeature());

        String type = CarUtil.identifyFeature("세단");
        System.out.println("차량 타입: "+type);

        EnginePrinter printer = new EnginePrinter();
        printer.printFeature("2.0 가솔린 터보");

        CarInfo<String,Double> info = new CarInfo<>("연비(km/L)",13.4);
        System.out.println(info.getKey()+":"+info.getValue());
    }
}
