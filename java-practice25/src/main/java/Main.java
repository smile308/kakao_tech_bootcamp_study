import java.util.*;
import java.util.stream.Collectors;

import practice.*;


public class Main {
    public static void main(String[] args){
        List<Car> carList = List.of(
                new Car("A", 2020),
                new Car("B", 2017),
                new Car("C", 2027),
                new Car("D", 2008)
        );

        List<Car> sortedCars = carList.stream().sorted((first,second)->second.getYear()- first.getYear()).collect(Collectors.toList());
        System.out.println("내림차순 정렬: ");
        sortedCars.forEach(car -> System.out.println(car));

        List<Car> recentCars = carList.stream().filter(car->car.getYear()>=2020).collect(Collectors.toList());

        System.out.println("2020년 이후 차량:");
        recentCars.forEach(car->System.out.println(car));
    }
}
