package practice;

public class Car {

    private final Engine engine;
    private final Brake brake;

    public Car(Engine engine, Brake brake)
    {
        this.engine=engine;
        this.brake=brake;
    }

    public void start(){
        engine.start();
    }

    public void stop(){
        brake.apply();
    }
}
