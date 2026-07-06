(function () {
  const EMAIL_REGEX = /^[A-Za-z]+@[A-Za-z]+(\.[A-Za-z]+)+$/;
  const PASSWORD_SPECIAL_REGEX = /[!@#$%^&*(),.?":{}|<>_\-+=\[\]\\;'\/~]/;

  function isValidEmail(email) {
    return EMAIL_REGEX.test(email);
  }

  function isValidPassword(password) {
    if (typeof password !== "string") {
      return false;
    }

    const hasValidLength = password.length >= 8 && password.length <= 20;
    const hasUpperCase = /[A-Z]/.test(password);
    const hasLowerCase = /[a-z]/.test(password);
    const hasNumber = /[0-9]/.test(password);
    const hasSpecialCharacter = PASSWORD_SPECIAL_REGEX.test(password);

    return (
      hasValidLength &&
      hasUpperCase &&
      hasLowerCase &&
      hasNumber &&
      hasSpecialCharacter
    );
  }

  function fileToDataUrl(file) {
    return new Promise((resolve, reject) => {
      if (!file) {
        reject(new Error("파일이 없습니다."));
        return;
      }

      const reader = new FileReader();

      reader.onload = () => {
        resolve(reader.result);
      };

      reader.onerror = () => {
        reject(reader.error);
      };

      reader.readAsDataURL(file);
    });
  }

  window.utils = {
    isValidEmail,
    isValidPassword,
    fileToDataUrl,
  };
})();