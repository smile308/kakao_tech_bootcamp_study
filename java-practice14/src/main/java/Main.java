import java.util.*;

public class Main {
    public static void main(String[] args)
    {
        Map<String, Integer> fridgeMap = new HashMap<>();
        Map<String, Integer> fridgeLinkedMap = new LinkedHashMap<>();
        Map<String, Integer> fridgeSortedMap = new TreeMap<>();

        fridgeMap.put("사이다", 3);
        fridgeMap.put("콜라", 5);
        fridgeMap.put("환타", 2);

        fridgeLinkedMap.put("사이다", 3);
        fridgeLinkedMap.put("콜라", 5);
        fridgeLinkedMap.put("환타", 2);

        fridgeSortedMap.put("사이다", 3);
        fridgeSortedMap.put("콜라", 5);
        fridgeSortedMap.put("환타", 2);

        System.out.println("HashMap 출력:");
        printMap(fridgeMap);
        System.out.println("LinkedHashMap 출력:");
        printMap(fridgeLinkedMap);
        System.out.println("TreeMap 출력:");
        printMap(fridgeSortedMap);


    }

    public static void printMap(Map<String, Integer> map){
        for(Map.Entry<String,Integer> entry : map.entrySet()){
            System.out.println(entry.getKey()+"->"+entry.getValue()+"개");
        }
    }
}
