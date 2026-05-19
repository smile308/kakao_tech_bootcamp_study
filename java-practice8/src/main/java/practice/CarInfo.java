package practice;

public class CarInfo<K,V> {
    private K key;
    private V value;

    public CarInfo(K key, V value)
    {
        this.key=key;
        this.value=value;
    }

    public K getKey(){
        return key;
    }
    public V getValue(){
        return value;
    }
}
