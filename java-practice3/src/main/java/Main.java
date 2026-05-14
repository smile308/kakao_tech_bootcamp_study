import java.util.*;

public class Main {
    public static void main(String[] args)
    {
        Scanner scanner = new Scanner(System.in);
        String name;
        name = scanner.nextLine();
        System.out.println("나:"+name);
        System.out.println("앵무새:"+name);

        boolean isCorrect = false;
        String correct = "열심히 배워서 최고의 개발자가 되어보자!";
        String text = scanner.nextLine();
        if (text.equals(correct))
        {
            System.out.println("정답입니다.");
        }
        String showMenu = "1. 작성 2. 조회 3. 수정 4. 삭제 5. 추가기능 6. 종료";
        System.out.println(showMenu);
        int userSelect = scanner.nextInt();
        switch (userSelect)
        {
            case 1 : {
                System.out.println("작성");
                break;
            }
            case 2 : {
                System.out.println("조회");
                break;
            }
            case 3 : {
                System.out.println("수정");
                break;
            }
            case 4 : {
                System.out.println("삭제");
                break;
            }
            case 5 : {
                System.out.println("추가기능");
                break;
            }
            case 6 : {
                System.out.println("종료");
                break;
            }
            default : break;
        }
    }
}
