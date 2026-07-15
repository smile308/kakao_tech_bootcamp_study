const loginForm = document.querySelector("#loginForm");
const emailInput = document.querySelector("#email");
const passwordInput = document.querySelector("#password");
const loginButton = document.querySelector("#loginButton");
const emailHelper = document.querySelector("#emailHelper");
const passwordHelper = document.querySelector("#passwordHelper");
const signupLink = document.querySelector("#signupLink");

const EMAIL_EMPTY_MESSAGE = "*이메일을 입력해주세요.";
const EMAIL_INVALID_MESSAGE =
  "*올바른 이메일 주소 형식을 입력해주세요. (예: example@example.com)";
const PASSWORD_EMPTY_MESSAGE = "*비밀번호를 입력해주세요";
const PASSWORD_INVALID_MESSAGE =
  "*비밀번호는 8자 이상, 20자 이하이며, 대문자, 소문자, 숫자, 특수문자를 각각 최소 1개 포함해야 합니다.";
const LOGIN_FAIL_MESSAGE = "*아이디 또는 비밀번호를 확인해주세요";

const { isValidEmail, isValidPassword } = window.utils;

function getEmailError(email) {
  if (!email) {
    return EMAIL_EMPTY_MESSAGE;
  }

  if (!isValidEmail(email)) {
    return EMAIL_INVALID_MESSAGE;
  }

  return "";
}

function getPasswordError(password) {
  if (!password) {
    return PASSWORD_EMPTY_MESSAGE;
  }

  if (!isValidPassword(password)) {
    return PASSWORD_INVALID_MESSAGE;
  }

  return "";
}

function renderEmailHelper() {
  const email = emailInput.value.trim();
  emailHelper.textContent = getEmailError(email);
}

function renderPasswordHelper() {
  const password = passwordInput.value;
  passwordHelper.textContent = getPasswordError(password);
}

function updateLoginButtonState() {
  const email = emailInput.value.trim();
  const password = passwordInput.value;

  const isValid = isValidEmail(email) && isValidPassword(password);

  loginButton.disabled = !isValid;
}

emailInput.addEventListener("input", () => {
  renderEmailHelper();
  updateLoginButtonState();
});

passwordInput.addEventListener("input", () => {
  renderPasswordHelper();
  updateLoginButtonState();
});

loginForm.addEventListener("submit", async (event) => {
  event.preventDefault();

  const email = emailInput.value.trim();
  const password = passwordInput.value;

  const emailError = getEmailError(email);
  const passwordError = getPasswordError(password);

  if (emailError || passwordError) {
    emailHelper.textContent = emailError;
    passwordHelper.textContent = passwordError;
    updateLoginButtonState();
    return;
  }

  try {
    loginButton.disabled = true;

    const result = await api.login({
      email,
      password,
    });

    localStorage.setItem("accessToken", result.accessToken);

    window.location.href = "./posts.html";
  } catch (error) {
    console.error("로그인 실패:", error);
    passwordHelper.textContent = LOGIN_FAIL_MESSAGE;
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
