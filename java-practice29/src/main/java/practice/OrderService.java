package practice;

public class OrderService {
    private final PaymentProcessor paymentProcessor;
    private final OrderRepository orderRepository;

    public OrderService(PaymentProcessor paymentProcessor, OrderRepository orderRepository){
        this.paymentProcessor=paymentProcessor;
        this.orderRepository=orderRepository;
    }

    public void placeOrder(Order order){
        if(order.getAmount()<=0){
            throw new IllegalArgumentException("0보다 커야 합니다.");
        }
        paymentProcessor.pay(order.getAmount());
        orderRepository.save(order);
    }
}
