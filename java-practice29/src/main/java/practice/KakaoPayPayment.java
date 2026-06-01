package practice;

public class KakaoPayPayment implements PaymentProcessor {
    @Override
    public void pay(int amount){
        System.out.println(amount + "원 카카오페이 결제");
    }
}
