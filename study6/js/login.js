const loginForm = document.querySelector("#loginForm");
const emailInput = document.querySelector("#email");
const passwordInput = document.querySelector("#password");
const loginButton = document.querySelector("#loginButton");
const loginHelper = document.querySelector("#loginHelper");
const signupLink = document.querySelector("#signupLink");

const EMAIL_EMPTY_MESSAGE = "*이메일을 입력해주세요.";
const EMAIL_INVALID_MESSAGE =
  "*올바른 이메일 주소 형식을 입력해주세요. (예: example@example.com)";
const PASSWORD_EMPTY_MESSAGE = "*비밀번호를 입력해주세요";
const PASSWORD_INVALID_MESSAGE =
  "*비밀번호는 8자 이상, 20자 이하이며, 대문자, 소문자, 숫자, 특수문자를 각각 최소 1개 포함해야 합니다.";
const LOGIN_FAIL_MESSAGE = "*아이디 또는 비밀번호를 확인해주세요";

function isValidEmail(email) {
  const emailRegex = /^[A-Za-z]+@[A-Za-z]+(\.[A-Za-z]+)+$/;
  return emailRegex.test(email);
}

function isValidPassword(password) {
  const hasValidLength = password.length >= 8 && password.length <= 20;
  const hasUpperCase = /[A-Z]/.test(password);
  const hasLowerCase = /[a-z]/.test(password);
  const hasNumber = /[0-9]/.test(password);
  const hasSpecialCharacter = /[!@#$%^&*(),.?":{}|<>_\-+=\[\]\\;'\/~]/.test(
    password
  );

  return (
    hasValidLength &&
    hasUpperCase &&
    hasLowerCase &&
    hasNumber &&
    hasSpecialCharacter
  );
}

function getValidationError(email, password) {
  if (!email) {
    return EMAIL_EMPTY_MESSAGE;
  }

  if (!isValidEmail(email)) {
    return EMAIL_INVALID_MESSAGE;
  }

  if (!password) {
    return PASSWORD_EMPTY_MESSAGE;
  }

  if (!isValidPassword(password)) {
    return PASSWORD_INVALID_MESSAGE;
  }

  return "";
}

function setHelperMessage(message) {
  loginHelper.textContent = message;
}

function updateLoginButtonState() {
  const email = emailInput.value.trim();
  const password = passwordInput.value;

  const isValid = isValidEmail(email) && isValidPassword(password);

  loginButton.disabled = !isValid;
}

emailInput.addEventListener("input", updateLoginButtonState);
passwordInput.addEventListener("input", updateLoginButtonState);

loginForm.addEventListener("submit", async (event) => {
  event.preventDefault();

  const email = emailInput.value.trim();
  const password = passwordInput.value;

  const errorMessage = getValidationError(email, password);

  if (errorMessage) {
    setHelperMessage(errorMessage);
    updateLoginButtonState();
    return;
  }

  try {
    loginButton.disabled = true;

    const result = await api.login({
      email,
      password,
    });

    localStorage.setItem("access_session", result.access_session);
    localStorage.setItem("user_id", result.user_id);

    window.location.href = "./posts.html";
  } catch (error) {
    console.error("로그인 실패:", error);
    setHelperMessage(LOGIN_FAIL_MESSAGE);
  } finally {
    updateLoginButtonState();
  }
});

if (signupLink) {
  signupLink.addEventListener("click", (event) => {
    event.preventDefault();
    window.location.href = "./signup.html";
  });
}

updateLoginButtonState();