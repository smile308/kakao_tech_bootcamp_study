import java.util.*;

class Product {
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

class Cosmetic extends Product {
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

class Cloth extends Product {
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

class Food extends Product {
    // vegan : 비건 여부
    private boolean vegan;

    //값을 넣기 위한 생성자
    public Food (String productName, String id, String brand, boolean vegan)
    {
        super(productName, id, brand);
        this.vegan=vegan;
    }

    //비건 여부 반환;
    public boolean getVegan(){
        return this.vegan;
    }
}

class Makeup extends Cosmetic {
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

class Skincare extends Cosmetic {
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

class Market {
    //marketName : 마켓 이름(올리브영,지그재그,무신사)
    //priceMap <상품ID,가격>
    private String marketName;
    private Map<String,Integer> priceMap = new HashMap<>();

    //마켓 이름을 정하는 생성자
    public Market(String marketName)
    {
        this.marketName=marketName;
    }

    //priceMap에 상품의 아이디와 가격을 입력
    public void addProductPrice(String productId, int price)
    {
        this.priceMap.put(productId,price);
    }

    //priceMap에서 productId에 해당하는 값을 반환하고 없으면 -1을 반환 -1의 경우 오류 처리에 사용
    public int getPrice (String productId){
        return priceMap.getOrDefault(productId,-1);
    }

    //Market의 이름을 반환
    public String getMarketName(){
        return this.marketName;
    }

}
public class Main{


    public static void main(String[] args) {

        Scanner sc= new Scanner(System.in);
        //상품 리스트를 메인 밖으로 빼기 위해 별도의 메서드 사용
        //상픔 클래스들에 상품 정보 입력
        List<Product> productList = new ArrayList<>();
        initProducts(productList);

        //각 마켓별로 상품 가격 입력
        List<Market> marketList = new ArrayList<>();
        initMarkets(marketList);

        //유저 입력창
        System.out.println("어떤 상품을 구매하시겠습니까? 번호로 입력해 주세요.");
        System.out.println("1.화장품, 2.옷, 3.식품");

        //유저 상품 종류 선택
        int insert =sc.nextInt();

        //cosmeticCategory : 색조,기초 제품 구분
        int cosmeticCategory=0;
        // number : 제품 번호 출력에 사용할 번호
        int number=1;
        sc.nextLine();

        //입력값에 따라 제품군 출력
        switch (insert) {
            case 1: {
                //유저가 보게 될 안내창
                System.out.println("어떤 화장품을 구매하시겠습니까? 번호로 입력해 주세요.");
                System.out.println("1.색조, 2.기초");
                //화장품 내부에서 소분류 선택
                cosmeticCategory = sc.nextInt();
                sc.nextLine();
                System.out.println("구매하실 제품의 ID를 입력해주세요");

                switch (cosmeticCategory) {
                    //색조 제품일 경우
                    case 1: {
                        //색조 제품 리스트 출력
                        for (Product p : productList) {
                            if (p instanceof Makeup) {
                                System.out.println(number + ".제품명:" + p.getProductName() + " ID:"+p.getId()+" 브랜드:"+p.getBrand()+" 피부 타입:"+((Makeup) p).getSkinType()+" 향 유무:"+((Makeup) p).getScent()+" 종류:"+((Makeup) p).getMakeupCategory());
                                number++;
                            }
                        }
                        break;
                    }
                    //기초 제품일 경우
                    case 2: {
                        //기초 제품들 리스트 출력
                        for (Product p : productList) {
                            if (p instanceof Skincare) {
                                System.out.println(number + ".제품명:" + p.getProductName() + " ID:"+p.getId()+" 브랜드:"+p.getBrand()+" 피부 타입:"+((Skincare) p).getSkinType()+" 향 유무:"+((Skincare) p).getScent() + " 종류:"+((Skincare) p).getSkincareCategory());
                                number++;
                            }
                        }
                        break;
                    }
                }
                break;
            }
            case 2: {
                System.out.println("구매하실 제품의 ID를 입력해주세요");
                //의류 제품 리스트 출력
                for (Product p : productList) {
                    if (p instanceof Cloth) {
                        System.out.println(number + "." + p.getProductName() + " ID:"+p.getId() + " 종류:"+((Cloth) p).getClothCategory());
                        number++;
                    }
                }
                break;
            }
            case 3: {
                System.out.println("구매하실 제품의 ID를 입력해주세요");
                //식품 제품 리스트 출력
                for (Product p : productList) {
                    if (p instanceof Food) {
                        System.out.println(number + "." + p.getProductName() + " ID:"+p.getId()+" 비건 여부:"+((Food)p).getVegan());
                        number++;
                    }
                }
                break;
            }
            default :
            {
                System.out.println("에러가 발생했습니다.");
                return;
            }
        }

        //제품 아이디 입력
        String insertId=sc.nextLine();
        //정상 작동 여부 확인
        boolean isActive = false;
        for (Product p : productList){
            //insertId와 제품의 아이디가 동일할 때 제품명 출력
            if(p.getId().equals(insertId)) {
                System.out.println("선택하신 상품:" + p.getProductName() + "의 가격을 비교해드리겠습니다.");
                isActive=true;
            }
        }
        //marketList 전체를 훑으며 ID가 같은 가격일 때 가격을 출력
            for(Market m : marketList){
                    int price = m.getPrice(insertId);
                    if (price != -1) {
                        System.out.println(m.getMarketName() + "에서 판매중인 가격은 " + m.getPrice(insertId) + "입니다.");
                    }
            }
            //에러 확인
            if (isActive == true) {
                System.out.println("상품 가격 비교가 끝났습니다. 즐거운 쇼핑 되세요.");
            }
            else{
                System.out.println("에러가 발생했습니다. 다시 이용해 주세요.");
            }

    }


    private static void initProducts(List<Product> list){
        //제품 정보를 Product에 저장
        list.add(new Makeup("카야 피그", "M001","롬앤", "지성", true, "립"));
        list.add(new Makeup("던 핑크", "M002","투슬래시포", "건성", false, "블러셔"));
        list.add(new Makeup("킬커버", "M003","클리오", "지성", true, "쿠션"));

        list.add(new Skincare("독도 토너", "S001", "라운드랩", "민감성", false,"토너"));
        list.add(new Skincare("껌딱지 시트 마스크팩", "S002", "아비브", "건성", true,"마스크팩"));
        list.add(new Skincare("시카마누 세럼", "S003", "파넬", "민감성", true,"세럼"));

        list.add(new Cloth("나른 슬림 사각", "C001", "나른", "속옷" ));
        list.add(new Cloth("무탠다드 셔츠", "C002", "무신사 스탠다드", "상의" ));
        list.add(new Cloth("올드스쿨", "C003", "반스", "신발" ));

        list.add(new Food ("리얼 피자 베이글칩", "F001","딜라이트",false));
        list.add(new Food ("삼대오백 프로틴", "F002","삼대오백",false));
    }
    private static void initMarkets(List<Market> marketList)
    {
        //올리브영 마켓에 상품들 추가
        Market oliveYoung = new Market("올리브영");
        oliveYoung.addProductPrice("M001", 10000);
        oliveYoung.addProductPrice("M002", 23000);
        oliveYoung.addProductPrice("M003", 30000);
        oliveYoung.addProductPrice("S001", 24000);
        oliveYoung.addProductPrice("S002", 4000);
        oliveYoung.addProductPrice("S003", 18000);
        oliveYoung.addProductPrice("C001", 9000);
        oliveYoung.addProductPrice("F001", 2500);
        oliveYoung.addProductPrice("F002", 45000);
        marketList.add(oliveYoung);

        //무신사 마켓에 상품들 추가
        Market musinsa = new Market("무신사");
        musinsa.addProductPrice("M001", 11000);
        musinsa.addProductPrice("M002", 22000);
        musinsa.addProductPrice("M003", 33000);
        musinsa.addProductPrice("S001", 25000);
        musinsa.addProductPrice("S002", 3000);
        musinsa.addProductPrice("S003", 20000);
        musinsa.addProductPrice("C002", 34000);
        musinsa.addProductPrice("C003", 78000);
        musinsa.addProductPrice("F002", 35000);
        marketList.add(musinsa);

    }
}