import java.util.*;

public class Main {
    public static void main(String[] args){
        Set<String> emails = new HashSet<>();

        emails.add("void@startupcode.kr");
        emails.add("yaro@startupcode.kr");
        emails.add("wayne@startupcode.kr");
        emails.add("joey@startupcode.kr");

        System.out.println("현재 집합: "+ emails);

        System.out.println("void@startupcode.kr 포함? " + emails.contains("void@startupcode.kr"));
        System.out.println("claire@startupcode.kr 포함? " + emails.contains("claire@startupcode.kr"));


        emails.remove("joey@startupcode.kr");
        System.out.println("삭제 후: "+ emails);
        System.out.println("요소 개수: "+emails.size());
        System.out.println("전체 요소 출력: ");
        for(String email : emails){
            System.out.println(email);
        }

        emails.clear();
        System.out.println(emails);
        System.out.println("비어있는가?: "+emails.isEmpty());
    }
}
