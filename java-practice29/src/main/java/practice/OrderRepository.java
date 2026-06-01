package practice;

public class OrderRepository {
    public void save(Order order)
    {
        System.out.println("주문 저장: "+order.getAmount());
    }
}
