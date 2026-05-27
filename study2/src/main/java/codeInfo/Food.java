package codeInfo;

public class Food extends Product {
    private boolean vegan;

    public Food (String productName, String id, String brand, boolean vegan)
    {
        super(productName, id, brand);
        this.vegan=vegan;
    }

    @Override
    public void displayInfo(){
        super.displayInfo();
        System.out.println(" 비건 여부: "+this.vegan);
    }
}