//스코프 미니퀘스트

let temperature =25;

temperature=30;

const MAX_TEMPERATURE = 35;

//여기서 const라서 에러가 발생한다.
//MAX_TEMPERATURE=40;

if(true)
{
    let isRaining=true;
    console.log(isRaining);
}

// 밖에서는 스코프로인해 실행 불가능
//console.log(isRaining);