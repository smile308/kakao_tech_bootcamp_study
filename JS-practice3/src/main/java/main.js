//객체 미니퀘스트
const myPet ={
    name: "Momo",
    type: "Cat"
};
console.log(myPet.name);
console.log(myPet.type);

function Person (name, age) {
    this.name=name;
    this.age=age;

    this.greet=function()
    {
        return`Hello, my name is ${this.name} and I am ${this.age} years old.`;
    };
}

const person=new Person("Jane Doe",25);
console.log(person.greet());

//프로그래밍 패러다임 미니퀘스트
const numbers =[1,2,3,4,5];

const sumnumbers=numbers.reduce((a,b)=>{return a+b;},0);

console.log(sumnumbers);

const doublenumbers=numbers.map((numbers)=>numbers*2);

console.log(doublenumbers);

//함수형 패러다임 미니퀘스트

//모듈 시스템 미니퀘스트

//블로킹 논블로킹,동기 비동기 미니퀘스트

//자바스크립트의 비동기 처리 미니퀘스트

//자바스크립트 엔진 미니퀘스트

//Execution Context 미니퀘스트

//스코프 미니퀘스트