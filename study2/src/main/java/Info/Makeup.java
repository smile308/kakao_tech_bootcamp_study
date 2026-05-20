package Info;

public class Makeup extends Cosmetic {
    private String makeupCategory;

    public Makeup (String productName, String id, String brand,String skinType, boolean scent, String makeupCategory)
    {
        super(productName, id, brand, skinType, scent);
        this.makeupCategory=makeupCategory;
    }

    @Override
    public void getDisplayInfo(){
        super.getDisplayInfo();
        System.out.println(" 종류: "+this.makeupCategory);
    }
}