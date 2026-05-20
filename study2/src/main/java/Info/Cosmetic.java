package Info;

public class Cosmetic extends Product {
    private String skinType;
    private boolean isScent;

    public Cosmetic(String productName, String id, String brand, String skinType, boolean scent) {
        super(productName, id, brand);
        this.skinType=skinType;
        this.isScent=scent;
    }

    @Override
    public void getDisplayInfo()
    {
        super.getDisplayInfo();
        System.out.print("피부 타입: "+this.skinType+"향기 유무: "+this.isScent);
    }

    public String getSkinType() {
        return this.skinType;
    }

    public boolean getScent() {
        return this.isScent;
    }
}