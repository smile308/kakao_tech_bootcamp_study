const EMAIL_REGEX=/^[A-Za-z]+@[A-Za-z]+(\.[A-Za-z]+)+$/;

const PASSWORD_REGEX=/[!@#$%^&*(),.?":{}|<>_\-+=\[\]\\;'\/~]/;

export function isValidEmail(email){
    return EMAIL_REGEX.test(email);
}

export function isValidPassword(password){
    if(typeof password !== "string"){
        return false;
    }
    const hasValidLength=password.length>=8 && password.length<=20;

    const hasUpperCase=/[A-Z]/.test(password);
    const hasLowerCase=/[a-z]/.test(password);
    const hasNumber=/[0-9]/.test(password);

    const hasSpecialCharacter=PASSWORD_REGEX.test(password);

    return hasValidLength && hasUpperCase && hasLowerCase && hasNumber && hasSpecialCharacter;
}