import practice.*;

public class Main {
    public static void main(String[] args){
        Order order = new Order(10000);

        PaymentProcessor paymentProcessor = new CardPayment();
        OrderRepository orderRepository = new OrderRepository();

        OrderService orderService = new OrderService(paymentProcessor, orderRepository);

        orderService.placeOrder(order);
    }
}
