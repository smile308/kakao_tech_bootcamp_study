package Info;

public class Product {
    //productName: 상품명, id : 상품 고유 코드, brand : 브랜드명
    private String productName;
    private String id;
    private String brand;

    public Product(String productName, String id, String brand)
    {
        this.productName=productName;
        this.id=id;
        this.brand=brand;
    }

    //상품 정보 출력 메서드
    public void getDisplayInfo(){
        System.out.print("제품명: "+this.productName+" 상품ID: "+this.id+" 브랜드: "+this.brand);
    }

    public String getProductName(){
        return this.productName;
    }

    public String getId() {
        return this.id;
    }

}