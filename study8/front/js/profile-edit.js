const profileImageInput = document.querySelector("#profileImageInput");
const profileEditImage = document.querySelector(".profile-edit-image");
const headerProfileButton = document.querySelector(".header-profile__button");
const headerProfileImage = document.querySelector(".header-profile__image");

const profileEditForm = document.querySelector("#profileEditForm");
const nicknameInput = document.querySelector("#nickname");
const nicknameHelper = document.querySelector("#nicknameHelper");
const toastMessage = document.querySelector("#toastMessage");

const withdrawButton = document.querySelector("#withdrawButton");
const modalOverlay = document.querySelector("#modalOverlay");
const modalCancelButton = document.querySelector("#modalCancelButton");
const modalConfirmButton = document.querySelector("#modalConfirmButton");

const NICKNAME_EMPTY_MESSAGE = "*닉네임을 입력해주세요.";
const NICKNAME_DUPLICATED_MESSAGE = "*중복된 닉네임 입니다.";
const NICKNAME_LENGTH_MESSAGE = "*닉네임은 최대 10자 까지 작성 가능합니다.";

let selectedProfileImageFile = null;
let selectedProfileImageDataUrl = null;
let currentProfileImage = null;
let toastTimer = null;

const { fileToDataUrl } = window.utils;

function validateNickname() {
  const nickname = nicknameInput.value.trim();

  if (!nickname) {
    nicknameHelper.textContent = NICKNAME_EMPTY_MESSAGE;
    return false;
  }

  if (nickname.length > 10) {
    nicknameHelper.textContent = NICKNAME_LENGTH_MESSAGE;
    return false;
  }

  nicknameHelper.textContent = "";
  return true;
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

function openWithdrawModal() {
  modalOverlay.classList.remove("is-hidden");
  modalOverlay.setAttribute("aria-hidden", "false");
  document.body.classList.add("modal-open");
}

function closeWithdrawModal() {
  modalOverlay.classList.add("is-hidden");
  modalOverlay.setAttribute("aria-hidden", "true");
  document.body.classList.remove("modal-open");
}

function setHeaderProfileImage(imageUrl) {
  if (!imageUrl) {
    headerProfileButton.classList.add("is-empty");
    headerProfileImage.removeAttribute("src");
    return;
  }

  headerProfileButton.classList.remove("is-empty");
  headerProfileImage.src = imageUrl;
}

profileImageInput.addEventListener("change", async () => {
  const file = profileImageInput.files[0];

  if (!file) {
    return;
  }

  selectedProfileImageFile = file;
  selectedProfileImageDataUrl = await fileToDataUrl(file);

  profileEditImage.src = selectedProfileImageDataUrl;
  setHeaderProfileImage(selectedProfileImageDataUrl);
});

nicknameInput.addEventListener("blur", () => {
  validateNickname();
});

profileEditForm.addEventListener("submit", async (event) => {
  event.preventDefault();

  if (!validateNickname()) {
    return;
  }

  try {
  await api.updateProfile({
    nickname: nicknameInput.value.trim(),
    profileImage: selectedProfileImageDataUrl,
  });

  showToast("수정 완료");
} catch (error) {
  console.error("회원정보 수정 실패:", error);
  alert(error.message || "회원정보 수정에 실패했습니다.");
}
});

withdrawButton.addEventListener("click", () => {
  openWithdrawModal();
});

modalCancelButton.addEventListener("click", () => {
  closeWithdrawModal();
});

modalConfirmButton.addEventListener("click", async () => {
  try {
    await api.deleteUser();

    localStorage.removeItem("accessToken");
    closeWithdrawModal();

    window.location.href = "./login.html";
  } catch (error) {
    console.error("회원 탈퇴 실패:", error);
    alert(error.message || "회원 탈퇴에 실패했습니다.");
  }
});

const emailText =
  document.querySelector("#profileEmail") ||
  document.querySelector(".profile-email-text");


async function loadUserInfo() {
  try {
    const user = await api.getUser();

    nicknameInput.value = user.nickname ?? "";

    if (emailText) {
      emailText.textContent = user.email ?? "";
    }

    currentProfileImage = user.profileImage ?? null;

if (currentProfileImage) {
  profileEditImage.src = currentProfileImage;
  setHeaderProfileImage(currentProfileImage);
} else {
  setHeaderProfileImage(null);
}
  } catch (error) {
    console.error("회원정보 조회 실패:", error);
  }
}

loadUserInfo();