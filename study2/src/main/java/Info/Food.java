package Info;

public class Food extends Product {
    // vegan : 비건 여부
    private boolean vegan;

    //값을 넣기 위한 생성자
    public Food (String productName, String id, String brand, boolean vegan)
    {
        super(productName, id, brand);
        this.vegan=vegan;
    }

    //비건 여부 반환;
    public boolean getVegan(){
        return this.vegan;
    }
}