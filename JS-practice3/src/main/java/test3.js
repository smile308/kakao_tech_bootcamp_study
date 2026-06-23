//함수형 패러다임 미니퀘스트

function add(a,b){
    return a+b;
}

let sum=add(2,3);
console.log(sum);

let numbers =[1,2,3,4,5];
let total=0;

function sumArray(numbers){
    for(i=0;i<numbers.length;i++){
        total+=numbers[i];
    }
}

sumArray(numbers);
console.log(total);