const passwordEditForm = document.querySelector("#passwordEditForm");
const currentPasswordInput = document.querySelector("#currentPasswordInput");
const passwordInput = document.querySelector("#passwordInput");
const passwordConfirmInput = document.querySelector("#passwordConfirmInput");
const currentPasswordHelper = document.querySelector("#currentPasswordHelper");
const passwordHelper = document.querySelector("#passwordHelper");
const passwordConfirmHelper = document.querySelector("#passwordConfirmHelper");
const passwordEditButton = document.querySelector("#passwordEditButton");
const toastMessage = document.querySelector("#toastMessage");

const CURRENT_PASSWORD_EMPTY_MESSAGE = "*현재 비밀번호를 입력해주세요";
const CURRENT_PASSWORD_NOT_MATCH_MESSAGE = "*현재 비밀번호가 일치하지 않습니다.";
const PASSWORD_EMPTY_MESSAGE = "*비밀번호를 입력해주세요";
const PASSWORD_INVALID_MESSAGE =
  "*비밀번호는 8자 이상, 20자 이하이며, 대문자, 소문자, 숫자, 특수문자를 각각 최소 1개 포함해야 합니다.";
const PASSWORD_CONFIRM_EMPTY_MESSAGE = "*비밀번호를 한번더 입력해주세요";
const PASSWORD_NOT_MATCH_MESSAGE = "*비밀번호가 다릅니다.";

let toastTimer = null;

const { isValidPassword } = window.utils;

function validateCurrentPassword() {
  if (!currentPasswordInput.value) {
    currentPasswordHelper.textContent = CURRENT_PASSWORD_EMPTY_MESSAGE;
    return false;
  }

  currentPasswordHelper.textContent = "";
  return true;
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
    passwordConfirmHelper.textContent = PASSWORD_NOT_MATCH_MESSAGE;
    return false;
  }

  passwordConfirmHelper.textContent = "";
  return true;
}

function isPasswordEditFormValid() {
  const currentPassword = currentPasswordInput.value;
  const password = passwordInput.value;
  const passwordConfirm = passwordConfirmInput.value;

  return (
    Boolean(currentPassword) &&
    isValidPassword(password) &&
    password === passwordConfirm
  );
}

function updatePasswordEditButtonState() {
  passwordEditButton.disabled = !isPasswordEditFormValid();
}

function validateAllAndRenderHelpers() {
  const isCurrentPasswordValidResult = validateCurrentPassword();
  const isPasswordValidResult = validatePassword();
  const isPasswordConfirmValidResult = validatePasswordConfirm();

  updatePasswordEditButtonState();

  return (
    isCurrentPasswordValidResult &&
    isPasswordValidResult &&
    isPasswordConfirmValidResult
  );
}

function showToast(message) {
  toastMessage.textContent = message;
  toastMessage.classList.remove("is-hidden");

  if (toastTimer) {
    clearTimeout(toastTimer);
  }

  toastTimer = setTimeout(() => {
    toastMessage.classList.add("is-hidden");
  }, 2000);
}

currentPasswordInput.addEventListener("input", () => {
  updatePasswordEditButtonState();

  if (currentPasswordHelper.textContent) {
    validateCurrentPassword();
  }
});

passwordInput.addEventListener("input", () => {
  updatePasswordEditButtonState();

  if (passwordHelper.textContent) {
    validatePassword();
  }

  if (passwordConfirmInput.value) {
    validatePasswordConfirm();
  }
});

passwordConfirmInput.addEventListener("input", () => {
  updatePasswordEditButtonState();

  if (passwordConfirmHelper.textContent) {
    validatePasswordConfirm();
  }
});

currentPasswordInput.addEventListener("blur", () => {
  validateCurrentPassword();
  updatePasswordEditButtonState();
});

passwordInput.addEventListener("blur", () => {
  validatePassword();

  if (passwordConfirmInput.value) {
    validatePasswordConfirm();
  }

  updatePasswordEditButtonState();
});

passwordConfirmInput.addEventListener("blur", () => {
  validatePasswordConfirm();
  updatePasswordEditButtonState();
});

passwordEditForm.addEventListener("submit", async (event) => {
  event.preventDefault();

  const isValid = validateAllAndRenderHelpers();

  if (!isValid) {
    return;
  }

  try {
    await api.updatePassword({
      currentPassword: currentPasswordInput.value,
      password: passwordInput.value,
      passwordCheck: passwordConfirmInput.value,
    });

    showToast("수정 완료");

    currentPasswordInput.value = "";
    passwordInput.value = "";
    passwordConfirmInput.value = "";
    updatePasswordEditButtonState();
  } catch (error) {
    console.error("비밀번호 수정 실패:", error);

    if (error.message === "Invalid_Current_Password") {
      currentPasswordHelper.textContent = CURRENT_PASSWORD_NOT_MATCH_MESSAGE;
      currentPasswordInput.focus();
      return;
    }

    passwordHelper.textContent = "*비밀번호 수정에 실패했습니다.";
  }
});

updatePasswordEditButtonState();
