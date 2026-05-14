public class Main {
    public static void main(String[] args) {
    int hour =1;
    if (hour >=7 && hour<=9)
    {
        System.out.println("아침 식사 시간");
    }
    else if (hour >=12 && hour<=14)
    {
        System.out.println("점심 식사 시간");
    }
    else if (hour >=18&&hour<=20)
    {
        System.out.println("저녁 식사 시간");
    }
    else
    {
        System.out.println("식사 금지");
    }

    String operator ="3";
    switch (operator){
        case "+" : {
            System.out.println("더하기");
            break;
        }

        case "-" : {
            System.out.println("빼기");
            break;}
        case "*" : {
            System.out.println("곱하기");
            break;
        }
        case "/" :
            {System.out.println("나누기");
            break;}
        default : {
            System.out.println("연산기호가 아님");
            break;
        }
        }
        String showMenu = "1. 작성 2. 조회 3. 수정 4. 삭제 5. 추가기능 6. 종료";
        System.out.println(showMenu);
        int userSelect = 6;
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



