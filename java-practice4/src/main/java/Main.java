import java.util.*;

public class Main {
    public static void main(String[] args){
        Scanner scanner = new Scanner(System.in);

        int num = scanner.nextInt();

        for( int i=1;i<10;i++)
        {
            System.out.println(num+"*"+i+"="+num*i);
        }

        int row=5;

        for(int i=0;i<5;i++)
        {
            for(int j=0;j<row-i-1;j++)
            {
                System.out.print(" ");
            }
            for(int j=0;j<(i*2+1);j++)
            {
                System.out.print("*");
            }
            System.out.println();
        }
        String showMenu = "1. 작성 2. 조회 3. 수정 4. 삭제 5. 추가기능 6. 종료";
        System.out.println(showMenu);
        int userSelect=1;
        while(userSelect!=6)
        {
            userSelect = scanner.nextInt();
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
}
