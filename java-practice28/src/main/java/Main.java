import practice.*;

public class Main {
    public static void main(String[] args)
    {
        Engine engine = new Engine();
        Brake brake = new Brake();
        Car car= new Car(engine, brake);

        car.start();
        car.stop();
    }
}
