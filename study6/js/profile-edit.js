const profileImageInput = document.querySelector("#profileImageInput");
const profileEditImage = document.querySelector(".profile-edit-image");
const headerProfileImage = document.querySelector(".header-profile__image");
const profileEditForm = document.querySelector("#profileEditForm");
const nicknameInput = document.querySelector("#nickname");
const nicknameHelper = document.querySelector("#nicknameHelper");

const headerProfileButton = document.querySelector(".header-profile__button");
const headerProfileImage = document.querySelector(".header-profile__image");

function setHeaderProfileImage(imageUrl) {
  if (!imageUrl) {
    headerProfileButton.classList.add("is-empty");
    headerProfileImage.removeAttribute("src");
    return;
  }

  headerProfileButton.classList.remove("is-empty");
  headerProfileImage.src = imageUrl;
}

profileImageInput.addEventListener("change", () => {
  const file = profileImageInput.files[0];

  if (!file) {
    return;
  }

  const imageUrl = URL.createObjectURL(file);

  profileEditImage.src = imageUrl;
  setHeaderProfileImage(imageUrl);
});

profileEditForm.addEventListener("submit", (event) => {
  event.preventDefault();

  const nickname = nicknameInput.value.trim();

  if (!nickname) {
    nicknameHelper.textContent = "*닉네임을 입력해주세요.";
    return;
  }

  nicknameHelper.textContent = "";

  // 이후 백엔드 API 연동 예정
});