import java.util.*;

public class Main{

    public static int calc(int A, int B)
    {
        int sol = A*B;
        return sol;
    }
    public static void main(String[] args){
        Scanner scanner = new Scanner(System.in);
        String input = scanner.nextLine();

        int num1=input.charAt(0)-'0';
        char oper=input.charAt(1);
        int num2=input.charAt(2)-'0';

        System.out.println("첫 번째 숫자:"+num1);
        System.out.println("연산자:"+oper);
        System.out.println("두 번째 숫자:"+num2);
        int result=0;
        switch(oper){
            case '+' :{
                result=num1+num2;
                break;
            }
            case '-' :{
                result=num1-num2;
                break;
            }
            case '/' :{
                result=num1/num2;
                break;
            }
            case '*' :{
                result=num1*num2;
                break;
            }
        }

        System.out.println(""+num1+oper+num2+"="+result);


    }
}