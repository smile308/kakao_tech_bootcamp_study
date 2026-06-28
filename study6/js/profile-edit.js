const profileMenuButton = document.querySelector("#profileMenuButton");
const profileMenu = document.querySelector("#profileMenu");
const logoutButton = document.querySelector("#logoutButton");

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
let selectedProfileImageUrl = null;
let toastTimer = null;

function toggleProfileMenu() {
  profileMenu.classList.toggle("is-open");
}

function closeProfileMenu() {
  profileMenu.classList.remove("is-open");
}

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

profileMenuButton.addEventListener("click", (event) => {
  event.stopPropagation();
  toggleProfileMenu();
});

profileMenu.addEventListener("click", (event) => {
  event.stopPropagation();
});

document.addEventListener("click", () => {
  closeProfileMenu();
});

document.addEventListener("keydown", (event) => {
  if (event.key === "Escape") {
    closeProfileMenu();
    closeWithdrawModal();
  }
});

logoutButton.addEventListener("click", async () => {
  try {
    await api.logout();
  } catch (error) {
    console.error("로그아웃 실패:", error);
  } finally {
    window.location.href = "./login.html";
  }
});

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

  const isValid = validateNickname();

  if (!isValid) {
    return;
  }

  try {
    await api.updateProfile({
      userId: api.getCurrentUserId(),
      nickname: nicknameInput.value.trim(),
      profileImage: selectedProfileImageDataUrl,
    });

    showToast("수정 완료");
  } catch (error) {
    const message = error.message || "";

    console.error("회원정보 수정 실패:", error);

    if (message.includes("중복")) {
      nicknameHelper.textContent = NICKNAME_DUPLICATED_MESSAGE;
      return;
    }

    nicknameHelper.textContent = "*회원정보 수정에 실패했습니다.";
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
    await api.deleteUser({
      userId: api.getCurrentUserId(),
    });
  } catch (error) {
    console.error("회원 탈퇴 실패:", error);
  } finally {
    closeWithdrawModal();
    window.location.href = "./login.html";
  }
});

const emailText =
  document.querySelector("#profileEmail") ||
  document.querySelector(".profile-email-text");

let selectedProfileImageDataUrl = null;

function fileToDataUrl(file) {
  return new Promise((resolve, reject) => {
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

async function loadUserInfo() {
  try {
    const user = await api.getUser();

    nicknameInput.value = user.nickname ?? "";

    if (emailText) {
      emailText.textContent = user.email ?? "";
    }

    if (user.profileImage) {
      profileEditImage.src = user.profileImage;
      setHeaderProfileImage(user.profileImage);
    } else {
      setHeaderProfileImage(null);
    }
  } catch (error) {
    console.error("회원정보 조회 실패:", error);
  }
}

loadUserInfo();