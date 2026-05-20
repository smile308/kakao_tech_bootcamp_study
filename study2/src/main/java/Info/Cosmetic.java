package Info;

public class Cosmetic extends Product {
    // skinType : 피부타입, scent : 향기 유무
    private String skinType;
    private boolean scent;

    //값을 넣기 위한 생성자
    public Cosmetic(String productName, String id, String brand, String skinType, boolean scent) {
        super(productName, id, brand);
        this.skinType=skinType;
        this.scent=scent;
    }

    //피부 타입 반환
    public String getSkinType() {
        return this.skinType;
    }

    //향기 유무 반환
    public boolean getScent() {
        return this.scent;
    }
}