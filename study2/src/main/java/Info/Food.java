package Info;

public class Food extends Product {
    private boolean vegan;

    public Food (String productName, String id, String brand, boolean vegan)
    {
        super(productName, id, brand);
        this.vegan=vegan;
    }

    @Override
    public void getDisplayInfo(){
        super.getDisplayInfo();
        System.out.println("비건 여부 :"+this.vegan);
    }

    public boolean getVegan(){
        return this.vegan;
    }
}