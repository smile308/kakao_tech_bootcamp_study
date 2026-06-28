const signupForm = document.querySelector("#signupForm");

const profileImageInput = document.querySelector("#profileImage");
const profileImageCircle = document.querySelector(".profile-image-circle");
const profilePreview = document.querySelector("#profilePreview");

const emailInput = document.querySelector("#signupEmail");
const passwordInput = document.querySelector("#signupPassword");
const passwordConfirmInput = document.querySelector("#signupPasswordConfirm");
const nicknameInput = document.querySelector("#nickname");

const profileHelper = document.querySelector("#profileHelper");
const emailHelper = document.querySelector("#emailHelper");
const passwordHelper = document.querySelector("#passwordHelper");
const passwordConfirmHelper = document.querySelector("#passwordConfirmHelper");
const nicknameHelper = document.querySelector("#nicknameHelper");

const signupButton = document.querySelector("#signupButton");
const backButton = document.querySelector(".back-button");

const duplicatedEmails = ["test@example.com", "startupcode@gmail.com"];
const duplicatedNicknames = ["더미 작성자 1", "관리자"];

let selectedProfileImageFile = null;
let selectedProfileImageUrl = null;

const EMAIL_EMPTY_MESSAGE = "*이메일을 입력해주세요.";
const EMAIL_INVALID_MESSAGE =
  "*올바른 이메일 주소 형식을 입력해주세요. (예: example@example.com)";
const EMAIL_DUPLICATED_MESSAGE = "*중복된 이메일 입니다.";

const PROFILE_EMPTY_MESSAGE = "*프로필 사진을 추가해주세요.";

const PASSWORD_EMPTY_MESSAGE = "*비밀번호를 입력해주세요";
const PASSWORD_INVALID_MESSAGE =
  "*비밀번호는 8자 이상, 20자 이하이며, 대문자, 소문자, 숫자, 특수문자를 각각 최소 1개 포함해야 합니다.";
const PASSWORD_CONFIRM_EMPTY_MESSAGE = "*비밀번호를 한번더 입력해주세요";
const PASSWORD_NOT_MATCH_MESSAGE = "*비밀번호가 다릅니다.";

const NICKNAME_EMPTY_MESSAGE = "*닉네임을 입력해주세요.";
const NICKNAME_SPACE_MESSAGE = "*띄어쓰기를 없애주세요";
const NICKNAME_LENGTH_MESSAGE = "*닉네임은 최대 10자 까지 작성 가능합니다.";
const NICKNAME_DUPLICATED_MESSAGE = "*중복된 닉네임 입니다.";

function isValidEmail(email) {
  const emailRegex = /^[A-Za-z]+@[A-Za-z]+(\.[A-Za-z]+)+$/;
  return emailRegex.test(email);
}

function isDuplicatedEmail(email) {
  return duplicatedEmails.includes(email);
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

function hasWhiteSpace(value) {
  return /\s/.test(value);
}

function isDuplicatedNickname(nickname) {
  return duplicatedNicknames.includes(nickname);
}


function validateEmail() {
  const email = emailInput.value.trim();

  if (!email) {
    emailHelper.textContent = EMAIL_EMPTY_MESSAGE;
    return false;
  }

  if (!isValidEmail(email)) {
    emailHelper.textContent = EMAIL_INVALID_MESSAGE;
    return false;
  }

  if (isDuplicatedEmail(email)) {
    emailHelper.textContent = EMAIL_DUPLICATED_MESSAGE;
    return false;
  }

  emailHelper.textContent = "";
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

function validateNickname() {
  const nickname = nicknameInput.value.trim();

  if (!nickname) {
    nicknameHelper.textContent = NICKNAME_EMPTY_MESSAGE;
    return false;
  }

  if (hasWhiteSpace(nicknameInput.value)) {
    nicknameHelper.textContent = NICKNAME_SPACE_MESSAGE;
    return false;
  }

  if (nickname.length > 10) {
    nicknameHelper.textContent = NICKNAME_LENGTH_MESSAGE;
    return false;
  }

  if (isDuplicatedNickname(nickname)) {
    nicknameHelper.textContent = NICKNAME_DUPLICATED_MESSAGE;
    return false;
  }

  nicknameHelper.textContent = "";
  return true;
}

function isSignupFormValid() {

  const email = emailInput.value.trim();
  const password = passwordInput.value;
  const passwordConfirm = passwordConfirmInput.value;
  const nickname = nicknameInput.value.trim();

  const isEmailValid = email && isValidEmail(email) && !isDuplicatedEmail(email);
  const isPasswordValidResult = password && isValidPassword(password);
  const isPasswordConfirmValid =
    passwordConfirm && password === passwordConfirm;
  const isNicknameValid =
    nickname &&
    !hasWhiteSpace(nicknameInput.value) &&
    nickname.length <= 10 &&
    !isDuplicatedNickname(nickname);

  return (
    isEmailValid &&
    isPasswordValidResult &&
    isPasswordConfirmValid &&
    isNicknameValid
  );
}

function updateSignupButtonState() {
  signupButton.disabled = !isSignupFormValid();
}

function validateAllAndRenderHelpers() {
  const isEmailValidResult = validateEmail();
  const isPasswordValidResult = validatePassword();
  const isPasswordConfirmValidResult = validatePasswordConfirm();
  const isNicknameValidResult = validateNickname();

  updateSignupButtonState();

  return (
    isEmailValidResult &&
    isPasswordValidResult &&
    isPasswordConfirmValidResult &&
    isNicknameValidResult
  );
}

function resetProfileImage() {
  selectedProfileImageFile = null;

  if (selectedProfileImageUrl) {
    URL.revokeObjectURL(selectedProfileImageUrl);
  }

  selectedProfileImageUrl = null;
  profileImageInput.value = "";
  profilePreview.removeAttribute("src");
  profileImageCircle.classList.remove("has-image");
}

profileImageInput.addEventListener("click", () => {
  if (selectedProfileImageFile) {
    resetProfileImage();
    profileHelper.textContent = PROFILE_EMPTY_MESSAGE;
    updateSignupButtonState();
  }
});

profileImageInput.addEventListener("change", () => {
  const file = profileImageInput.files[0];

  if (!file) {
    resetProfileImage();
    profileHelper.textContent = PROFILE_EMPTY_MESSAGE;
    updateSignupButtonState();
    return;
  }

  selectedProfileImageFile = file;

  if (selectedProfileImageUrl) {
    URL.revokeObjectURL(selectedProfileImageUrl);
  }

  selectedProfileImageUrl = URL.createObjectURL(file);
  profilePreview.src = selectedProfileImageUrl;
  profileImageCircle.classList.add("has-image");

  profileHelper.textContent = "";
  updateSignupButtonState();
});

emailInput.addEventListener("blur", () => {
  validateEmail();
  updateSignupButtonState();
});

passwordInput.addEventListener("blur", () => {
  validatePassword();

  if (passwordConfirmInput.value) {
    validatePasswordConfirm();
  }

  updateSignupButtonState();
});

passwordConfirmInput.addEventListener("blur", () => {
  validatePasswordConfirm();
  updateSignupButtonState();
});

nicknameInput.addEventListener("blur", () => {
  validateNickname();
  updateSignupButtonState();
});

emailInput.addEventListener("input", updateSignupButtonState);
passwordInput.addEventListener("input", updateSignupButtonState);
passwordConfirmInput.addEventListener("input", updateSignupButtonState);
nicknameInput.addEventListener("input", updateSignupButtonState);

signupForm.addEventListener("submit", (event) => {
  event.preventDefault();

  const isValid = validateAllAndRenderHelpers();

  if (!isValid) {
    return;
  }

  const userInfo = {
    email: emailInput.value.trim(),
    password: passwordInput.value,
    nickname: nicknameInput.value.trim(),
    profileImageName: selectedProfileImageFile
    ? selectedProfileImageFile.name
    : null,
  };

  const savedUsers = JSON.parse(localStorage.getItem("users")) || [];
  savedUsers.push(userInfo);

  localStorage.setItem("users", JSON.stringify(savedUsers));

  window.location.href = "./login.html";
});

if (backButton) {
  backButton.addEventListener("click", () => {
    window.location.href = "./login.html";
  });
}

updateSignupButtonState();