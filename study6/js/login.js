const loginForm = document.querySelector("#loginForm");
const emailInput = document.querySelector("#email");
const passwordInput = document.querySelector("#password");
const loginHelper = document.querySelector("#loginHelper");
const loginButton = document.querySelector("#loginButton");

let isSubmitting = false;

const EMAIL_EMPTY_MESSAGE = "*이메일을 입력해주세요.";
const EMAIL_INVALID_MESSAGE =
  '*올바른 이메일 주소 형식을 입력해주세요.(예:<span class="helper-text__underline">example#adapterz.kr</span>)';

const PASSWORD_EMPTY_MESSAGE = "*비밀번호를 입력해주세요";
const PASSWORD_INVALID_MESSAGE =
  "*비밀번호는 8자 이상, 20자 이하이며, 대문자, 소문자, 숫자, 특수문자를 각각 최소 1개 포함해야 합니다.";

const LOGIN_FAILED_MESSAGE = "*아이디 또는 비밀번호를 확인해주세요";

function isValidEmail(email) {
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  return emailRegex.test(email);
}

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

function getValidationError() {
  const email = emailInput.value.trim();
  const password = passwordInput.value;

  if (!email) {
    return {
      message: EMAIL_EMPTY_MESSAGE,
      isHtml: false,
    };
  }

  if (!isValidEmail(email)) {
    return {
      message: EMAIL_INVALID_MESSAGE,
      isHtml: true,
    };
  }

  if (!password) {
    return {
      message: PASSWORD_EMPTY_MESSAGE,
      isHtml: false,
    };
  }

  if (!isValidPassword(password)) {
    return {
      message: PASSWORD_INVALID_MESSAGE,
      isHtml: false,
    };
  }

  return null;
}

function setHelperMessage(message, isHtml = false) {
  if (isHtml) {
    loginHelper.innerHTML = message;
    return;
  }

  loginHelper.textContent = message;
}

function clearHelperMessage() {
  loginHelper.textContent = "";
}

function isFormValid() {
  return getValidationError() === null;
}

function updateLoginButtonState() {
  loginButton.disabled = isSubmitting || !isFormValid();
}

function validateAndRenderMessage() {
  const validationError = getValidationError();

  if (validationError) {
    setHelperMessage(validationError.message, validationError.isHtml);
  } else {
    clearHelperMessage();
  }

  updateLoginButtonState();
}

emailInput.addEventListener("input", validateAndRenderMessage);
passwordInput.addEventListener("input", validateAndRenderMessage);

loginForm.addEventListener("submit", async (event) => {
  event.preventDefault();

  const validationError = getValidationError();

  if (validationError) {
    setHelperMessage(validationError.message, validationError.isHtml);
    updateLoginButtonState();
    return;
  }

  const email = emailInput.value.trim();
  const password = passwordInput.value;

  try {
    isSubmitting = true;
    updateLoginButtonState();

    const result = await request("/sessions", {
      method: "POST",
      body: JSON.stringify({
        email,
        password,
      }),
    });

    if (result && result.access_session) {
      localStorage.setItem("access_session", result.access_session);
    }

    if (result && result.user_id) {
      localStorage.setItem("user_id", result.user_id);
    }

    window.location.href = "./posts.html";
  } catch (error) {
    setHelperMessage(LOGIN_FAILED_MESSAGE);
  } finally {
    isSubmitting = false;
    updateLoginButtonState();
  }
});

updateLoginButtonState();