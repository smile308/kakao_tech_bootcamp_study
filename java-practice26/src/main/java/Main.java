import practice.*;
import java.util.*;

public class Main{
    public static void main(String[] args){
        Car avanteCar = new Car("Avante", 2021, "12가 1234", null);
        Car sonataCar = new Car("Sonata", 2020, null, "홍길동");

        printCarInfo(avanteCar);
        System.out.println();
        printCarInfo(sonataCar);
    }
    public static void printCarInfo(Car car){
        System.out.println("차종: "+car.getModel());
        System.out.println("연식: "+car.getYear());

        String plate = car.getPlateNumber().orElse("미등록 차량");
        System.out.println("번호판: "+plate);

        car.getOwnerName().ifPresent(owner ->System.out.println("소유자: "+owner));
    }
}