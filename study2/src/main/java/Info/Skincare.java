package Info;

public class Skincare extends Cosmetic {
    private String skincareCategory;

    public Skincare(String productName, String id, String brand, String skinType, boolean scent, String skincareCategory) {
        super(productName, id, brand, skinType, scent);
        this.skincareCategory=skincareCategory;
    }

    @Override
    public void getDisplayInfo(){
        super.getDisplayInfo();
        System.out.println(" 종류: "+this.skincareCategory);
    }
}