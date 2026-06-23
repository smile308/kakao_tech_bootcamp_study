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