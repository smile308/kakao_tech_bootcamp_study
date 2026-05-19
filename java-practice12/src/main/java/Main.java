import java.util.*;

public class Main {
    public static void main(String[] args)
    {
        List<String> fridge = new ArrayList<>();

        fridge.add("콜라");
        fridge.add("사이다");
        fridge.add("환타");
        System.out.println("초기 냉장고: "+fridge);

        fridge.add(1,"밀키스");
        System.out.println("삽입 후: "+fridge);

        String secondDrink=fridge.get(1);
        System.out.println("두 번째 음료: "+secondDrink);

        fridge.set(2,"웰치스");
        System.out.println("수정 후: " +fridge);

        fridge.remove(0);
        System.out.println("삭제 후: "+ fridge);

        System.out.println("웰치스 포함 여부 :"+ fridge.contains("웰치스"));

        System.out.println("냉장고 안 음료 개수" + fridge.size());

        System.out.println("전체 음료 출력:");
        for(String drink : fridge){
            System.out.println(drink);
        }

        fridge.clear();
        System.out.println("비운 후: "+fridge);
    }
}
