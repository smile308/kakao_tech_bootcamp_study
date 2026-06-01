package practice;

public class CardPayment implements PaymentProcessor {
    @Override
    public void pay(int amount){
        System.out.println(amount + "원 카드 결제");
    }
}
