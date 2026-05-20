package Info;

public class Makeup extends Cosmetic {
    // makeUpCategory : 메이크업 카테고리(립,파운데이션,블러셔)
    private String makeupCategory;

    //값을 넣기 위한 생성자
    public Makeup (String productName, String id, String brand,String skinType, boolean scent, String makeupCategory)
    {
        super(productName, id, brand, skinType, scent);
        this.makeupCategory=makeupCategory;
    }

    //색조 종류 반환;
    public String getMakeupCategory() {
        return makeupCategory;
    }
}