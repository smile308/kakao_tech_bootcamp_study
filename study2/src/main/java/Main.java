import codeInfo.*;
import multiTask.*;
import java.util.*;

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
        String insertId = "";
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
                                System.out.print(number+".");
                                p.displayInfo();
                                number++;
                            }
                        }
                        insertId=sc.nextLine();
                        if(insertId.charAt(0)!='M')
                        {
                            System.out.println("잘못된 상품 코드이거나 현재 보고계신 상품이 아닙니다.");
                            return;
                        }
                        break;
                    }
                    //기초 제품일 경우
                    case 2: {
                        //기초 제품들 리스트 출력
                        for (Product p : productList) {
                            if (p instanceof Skincare) {
                                System.out.print(number+".");
                                p.displayInfo();
                                number++;
                            }
                        }
                        insertId=sc.nextLine();
                        if(insertId.charAt(0)!='S')
                        {
                            System.out.println("잘못된 상품 코드이거나 현재 보고계신 상품이 아닙니다.");
                            return;
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
                        System.out.print(number+".");
                        p.displayInfo();
                        number++;
                    }
                }
                insertId=sc.nextLine();
                if(insertId.charAt(0)!='C')
                {
                    System.out.println("잘못된 상품 코드이거나 현재 보고계신 상품이 아닙니다.");
                    return;
                }
                break;
            }
            case 3: {
                System.out.println("구매하실 제품의 ID를 입력해주세요");
                //식품 제품 리스트 출력
                for (Product p : productList) {
                    if (p instanceof Food) {
                        System.out.print(number+".");
                        p.displayInfo();
                        number++;
                    }
                }
                insertId=sc.nextLine();
                if(insertId.charAt(0)!='F')
                {
                    System.out.println("잘못된 상품 코드이거나 현재 보고계신 상품이 아닙니다.");
                    return;
                }
                break;
            }
            default :
            {
                System.out.println("에러가 발생했습니다.");
                return;
            }
        }


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


        Runnable compareTask = new PriceCompare(marketList,insertId);
        Thread compareThread = new Thread(compareTask);


        compareThread.start();



        //스레드 종료까지 기다림
        try {
            compareThread.join();
            System.out.println("비교가 끝났습니다.");
        } catch (InterruptedException e) {
            e.printStackTrace();
            Thread.currentThread().interrupt();
        }
        //에러 확인
            if (isActive == true) {
                System.out.println("즐거운 쇼핑 되세요.");
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