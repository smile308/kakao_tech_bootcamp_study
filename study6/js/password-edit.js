const passwordEditForm = document.querySelector("#passwordEditForm");
const passwordInput = document.querySelector("#password");
const passwordConfirmInput = document.querySelector("#passwordConfirm");
const passwordHelper = document.querySelector("#passwordHelper");
const passwordConfirmHelper = document.querySelector("#passwordConfirmHelper");
const passwordSubmitButton = document.querySelector("#passwordSubmitButton");

const PASSWORD_EMPTY_MESSAGE = "*비밀번호를 입력해주세요";
const PASSWORD_INVALID_MESSAGE =
  "*비밀번호는 8자 이상, 20자 이하이며, 대문자, 소문자, 숫자, 특수문자를 각각 최소 1개 포함해야 합니다.";
const PASSWORD_CONFIRM_EMPTY_MESSAGE = "*비밀번호를 한번 더 입력해주세요.";
const PASSWORD_CONFIRM_INVALID_MESSAGE = "*비밀번호가 다릅니다.";

function isValidPassword(password) {
  const hasValidLength = password.length >= 8 && password.length <= 20;
  const hasUpperCase = /[A-Z]/.test(password);
  const hasLowerCase = /[a-z]/.test(password);
  const hasNumber = /[0-9]/.test(password);
  const hasSpecialCharacter = /[!@#$%^&*(),.?":{}|<>_\-+=\[\]\\;'\/~]/.test(password);

  return (
    hasValidLength &&
    hasUpperCase &&
    hasLowerCase &&
    hasNumber &&
    hasSpecialCharacter
  );
}

function validatePassword() {
  const password = passwordInput.value;

  if (!password) {
    passwordHelper.textContent = PASSWORD_EMPTY_MESSAGE;
    return false;
  }

  if (!isValidPassword(password)) {
    passwordHelper.textContent = PASSWORD_INVALID_MESSAGE;
    return false;
  }

  passwordHelper.textContent = "";
  return true;
}

function validatePasswordConfirm() {
  const password = passwordInput.value;
  const passwordConfirm = passwordConfirmInput.value;

  if (!passwordConfirm) {
    passwordConfirmHelper.textContent = PASSWORD_CONFIRM_EMPTY_MESSAGE;
    return false;
  }

  if (password !== passwordConfirm) {
    passwordConfirmHelper.textContent = PASSWORD_CONFIRM_INVALID_MESSAGE;
    return false;
  }

  passwordConfirmHelper.textContent = "";
  return true;
}

function updateSubmitButtonState() {
  const isPasswordValid = isValidPassword(passwordInput.value);
  const isPasswordConfirmValid =
    passwordConfirmInput.value.length > 0 &&
    passwordInput.value === passwordConfirmInput.value;

  passwordSubmitButton.disabled = !(isPasswordValid && isPasswordConfirmValid);
}

function handleInput() {
  validatePassword();
  validatePasswordConfirm();
  updateSubmitButtonState();
}

passwordInput.addEventListener("input", handleInput);
passwordConfirmInput.addEventListener("input", handleInput);

passwordEditForm.addEventListener("submit", (event) => {
  event.preventDefault();

  const isPasswordValid = validatePassword();
  const isPasswordConfirmValid = validatePasswordConfirm();

  if (!isPasswordValid || !isPasswordConfirmValid) {
    updateSubmitButtonState();
    return;
  }

  // 이후 백엔드 API 연동 예정
  // PATCH /users/password 또는 프로젝트 백엔드 명세에 맞는 endpoint 호출

  alert("비밀번호가 수정되었습니다.");
});

updateSubmitButtonState();