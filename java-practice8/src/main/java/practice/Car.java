package practice;

public class Car<T> {
    private T feature;

    public Car(T feature) {
        this.feature = feature;
    }

    public T gerFeature(){
        return this.feature;
    }
}
