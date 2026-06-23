//모듈 시스템 미니퀘스트 app.js 대체

//import{add} from './math.js';
import {add, substract} from './operation.js';
import User from "./userProfile.js";
console.log(add(2,3));

const user = new User("miles",27);
console.log(substract(3,2));
console.log(user.name);
console.log(user.age);