const EMAIL_LOCAL_ATOM_PATTERN = "[A-Za-z0-9!#$%&'*+/=?^_`{|}~\\u0080-\\uFFFF-]";
const EMAIL_DOMAIN_ATOM_PATTERN = "[A-Za-z0-9!#$%&'*+/=?^_`{|}~\\u0080-\\uFFFF]";
const EMAIL_DOMAIN_LABEL_PATTERN = `${EMAIL_DOMAIN_ATOM_PATTERN}+(?:-+${EMAIL_DOMAIN_ATOM_PATTERN}+)*`;
const EMAIL_REGEX = new RegExp(
    `^${EMAIL_LOCAL_ATOM_PATTERN}+(?:\\.${EMAIL_LOCAL_ATOM_PATTERN}+)*@${EMAIL_DOMAIN_LABEL_PATTERN}(?:\\.${EMAIL_DOMAIN_LABEL_PATTERN})*$`,
    "i",
);

const PASSWORD_REGEX = /[!@#$%^&*(),.?":{}|<>_\-+=[\]\\;'/~]/;

export function isValidEmail(email){
    if (typeof email !== "string") {
        return false;
    }

    const separatorIndex = email.lastIndexOf("@");

    if (separatorIndex <= 0) {
        return false;
    }

    const localPart = email.slice(0, separatorIndex);
    const domainPart = email.slice(separatorIndex + 1);
    const hasValidDomainLabelLength = domainPart
        .split(".")
        .every((label) => label.length <= 63);

    return (
        localPart.length <= 64 &&
        domainPart.length <= 255 &&
        hasValidDomainLabelLength &&
        EMAIL_REGEX.test(email)
    );
}

export function isValidNickname(nickname) {
    return (
        typeof nickname === "string" &&
        nickname.length > 0 &&
        nickname.length <= 10 &&
        !/\s/.test(nickname)
    );
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
