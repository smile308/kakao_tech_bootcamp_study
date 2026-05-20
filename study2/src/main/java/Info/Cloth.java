package Info;

public class Cloth extends Product {
    // clothCategory : 옷 종류(상의,하의)
    private String clothCategory;

    //값을 넣기 위한 생성자
    public Cloth(String productName, String id, String brand,String clothCategory)
    {
        super(productName, id, brand);
        this.clothCategory=clothCategory;
    }

    //옷 종류 반환
    public String getClothCategory(){
        return this.clothCategory;
    }
}