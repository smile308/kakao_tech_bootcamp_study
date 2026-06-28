const backButton = document.querySelector("#backButton");
const postCreateForm = document.querySelector("#postCreateForm");
const postTitleInput = document.querySelector("#postTitleInput");
const postContentInput = document.querySelector("#postContentInput");
const postImageInput = document.querySelector("#postImageInput");
const selectedFileName = document.querySelector("#selectedFileName");
const postCreateHelper = document.querySelector("#postCreateHelper");
const postCreateSubmitButton = document.querySelector("#postCreateSubmitButton");

const MAX_TITLE_LENGTH = 26;
const EMPTY_MESSAGE = "*제목,내용을 모두 작성해주세요";

let selectedImageFile = null;

function isPostCreateFormValid() {
  const title = postTitleInput.value.trim();
  const content = postContentInput.value.trim();

  return title.length > 0 && content.length > 0;
}

function updateSubmitButtonState() {
  if (isPostCreateFormValid()) {
    postCreateSubmitButton.classList.add("is-active");
    return;
  }

  postCreateSubmitButton.classList.remove("is-active");
}

function validateAndRenderHelper() {
  if (!isPostCreateFormValid()) {
    postCreateHelper.textContent = EMPTY_MESSAGE;
    return false;
  }

  postCreateHelper.textContent = "";
  return true;
}

postTitleInput.addEventListener("input", () => {
  if (postTitleInput.value.length > MAX_TITLE_LENGTH) {
    postTitleInput.value = postTitleInput.value.slice(0, MAX_TITLE_LENGTH);
  }

  updateSubmitButtonState();

  if (isPostCreateFormValid()) {
    postCreateHelper.textContent = "";
  }
});

postContentInput.addEventListener("input", () => {
  updateSubmitButtonState();

  if (isPostCreateFormValid()) {
    postCreateHelper.textContent = "";
  }
});

postImageInput.addEventListener("change", () => {
  const file = postImageInput.files[0];

  if (!file) {
    selectedImageFile = null;
    selectedFileName.textContent = "파일을 선택해주세요.";
    return;
  }

  selectedImageFile = file;
  selectedFileName.textContent = file.name;
});

postCreateForm.addEventListener("submit", (event) => {
  event.preventDefault();

  const isValid = validateAndRenderHelper();

  if (!isValid) {
    updateSubmitButtonState();
    return;
  }

  const newPost = {
    title: postTitleInput.value.trim(),
    content: postContentInput.value.trim(),
    imageFileName: selectedImageFile ? selectedImageFile.name : null,
    createdAt: new Date().toISOString(),
  };

  /*
    백엔드 연결 전 임시 저장.
    실제 API 연결 시 POST /posts 요청으로 교체하면 됩니다.
  */
  const savedPosts = JSON.parse(localStorage.getItem("posts")) || [];
  savedPosts.push(newPost);
  localStorage.setItem("posts", JSON.stringify(savedPosts));

  window.location.href = "./posts.html";
});

backButton.addEventListener("click", () => {
  window.location.href = "./posts.html";
});

updateSubmitButtonState();