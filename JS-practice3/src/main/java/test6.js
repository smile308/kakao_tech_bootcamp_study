//자바스크립트의 비동기 처리 미니퀘스트

const myFirstPromise = new Promise((resolve, reject) => {
    // 여기에 코드를 작성하세요.
    resolve("Hello, Promise!");
});

// 아래 코드는 수정하지 마세요.
myFirstPromise.then(message => {
    console.log(message);
});


async function  waitForMessage (){
    setTimeout(()=>console.log("Hello,Async/Await!"),1000);
}

waitForMessage();