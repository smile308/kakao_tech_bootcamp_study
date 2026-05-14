import java.util.*;

public class Main {
    public static void main(String[] args) {
        List<Integer> a = new ArrayList<>(Arrays.asList(1,2,3,4,5));
        int[] numbers = {1, 2, 3, 4, 5, 6};
        int sum = 0;
        for (int i=0; i < a.size(); i++)
        {
            sum+=numbers[i];
        }
        System.out.println(sum);
        List<Integer> b = new ArrayList<>(Arrays.asList(1,2,3,4,5,6,7,8,9,10));
        for(int i=0;i<b.size();i++)
        {
            if((b.get(i)%2)==0)
            {
                System.out.println("짝수 발견:"+b.get(i));
            }
        }
        String showMenu = "1. 작성 2. 조회 3. 수정 4. 삭제 5. 추가기능 6. 종료";
        List<String> memo = new ArrayList<>();
        Scanner scanner = new Scanner(System.in);
        System.out.println(showMenu);
        int userSelect=1;
        int cnt;
        String fix;
        while(userSelect!=6)
        {
            userSelect = scanner.nextInt();
            scanner.nextLine();
            switch (userSelect)
            {
                case 1 : {
                    System.out.println("작성");
                    memo.add(scanner.nextLine());
                    break;
                }
                case 2 : {
                    System.out.println("조회");
                    for (int i=0;i<memo.size();i++)
                    {
                        System.out.println(i+1+"."+memo.get(i));
                    }
                    System.out.println("선택해주세요.");
                    cnt=scanner.nextInt();
                    scanner.nextLine();
                    System.out.println(memo.get(cnt-1));
                    break;
                }
                case 3 : {
                    System.out.println("수정");
                    cnt=scanner.nextInt();
                    scanner.nextLine();
                    fix=scanner.nextLine();
                    memo.set(cnt-1,fix);
                    break;
                }
                case 4 : {
                    System.out.println("삭제");
                    cnt=scanner.nextInt();
                    scanner.nextLine();
                    memo.remove(cnt-1);
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