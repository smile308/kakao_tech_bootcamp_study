package Info;

public class Product {
    //productName: 상품명, id : 상품 고유 코드, brand : 브랜드명
    private String productName;
    private String id;
    private String brand;

    //값을 넣기 위한 생성자
    public Product(String productName, String id, String brand)
    {
        this.productName=productName;
        this.id=id;
        this.brand=brand;
    }

    //상품명을 반환
    public String getProductName(){
        return this.productName;
    }

    //상품ID를 반환
    public String getId() {
        return this.id;
    }

    //상품 브랜드를 반환
    public String getBrand() {
        return this.brand;
    }
}