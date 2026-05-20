package Info;

public class Skincare extends Cosmetic {
    // skincareCategory : 스킨케어 카테코리(스킨,세럼,로션)
    private String skincareCategory;

    //값을 넣기 위한 생성자
    public Skincare(String productName, String id, String brand, String skinType, boolean scent, String skincareCategory) {
        super(productName, id, brand, skinType, scent);
        this.skincareCategory=skincareCategory;
    }

    //피부 타입 반환
    public String getSkincareCategory(){
        return this.skincareCategory;
    }
}