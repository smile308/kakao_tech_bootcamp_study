package codeInfo;

public class Cloth extends Product {
    private String clothCategory;

    public Cloth(String productName, String id, String brand,String clothCategory)
    {
        super(productName, id, brand);
        this.clothCategory=clothCategory;
    }


    @Override
    public void displayInfo(){
        super.displayInfo();
        System.out.println(" 종류: "+this.clothCategory);
    }
}