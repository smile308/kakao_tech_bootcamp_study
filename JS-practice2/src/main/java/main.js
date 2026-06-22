import readlineSyncModule from 'readline-sync';

let hour = 19;

if(hour>=7&&hour<=9)
    console.log("아침 식사 시간");
else if(hour>=12&&hour<=14)
    console.log("점심 식사 시간");
else if(hour>=18&&hour<=20)
    console.log("저녁 식사 시간");
else
    console.log("식사 금지");

let cal ='-'

switch (cal) {
    case '+':
        console.log("더하기");
        break;
    case '-':
        console.log("빼기");
        break;
}

const userInput = parseInt(readlineSyncModule.question('사용자입력: '), 10);
console.log(userInput);
for(let i=0;i<9;i++)
{
    console.log(`${userInput}*${i+1}=${userInput*(i+1)}`);
}

const numbers = [1, 2, 3, 4, 5];

let sum=0;
for(let i=0;i<5;i++)
{
 sum+=numbers[i];
 console.log(`현재 합계: ${sum} (${numbers[i]}를 더함)`);
}
console.log(`최종 합계: ${sum}`);

let num1 = 5;
let num2 = 3;
let calc='+';
console.log(`첫 번째 숫자: ${num1}`);
console.log(`연산자: ${calc}`);
console.log(`두 번째 숫자: ${num2}`);

switch (calc){
    case '*':
        console.log(`${num1}${calc}${num2}=${num1*num2}`);
        break;
    case '+':
        console.log(`${num1}${calc}${num2}=${num1+num2}`);
        break;
}