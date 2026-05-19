package practice;

public class EnginePrinter implements CarPrinter <String> {
    @Override
    public void printFeature(String feature) {
        System.out.println("엔진: "+feature);
    }
}
