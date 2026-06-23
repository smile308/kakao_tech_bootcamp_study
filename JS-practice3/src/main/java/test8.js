//Execution Context 미니퀘스트

/* A에서 에러가 나서 실행되지 않음
const message = 'Hello, JavaScript!';

const showMessage = () => {
    console.log(message); // A
    let message = 'Hello, ES6!';
    console.log(message); // B
};

showMessage();

 */

const color = 'blue';

const firstLevel = () => {
    let color = 'red';

    const secondLevel = () => {
        let color = 'green';
        console.log(color); // (1)
    };

    secondLevel();
    console.log(color); // (2)
};

firstLevel();
console.log(color); // (3)