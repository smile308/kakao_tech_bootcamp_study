//프로그래밍 패러다임 미니퀘스트
const numbers =[1,2,3,4,5];

const sumnumbers=numbers.reduce((a,b)=>{return a+b;},0);

console.log(sumnumbers);

const doublenumbers=numbers.map((numbers)=>numbers*2);

console.log(doublenumbers);
