const backButton = document.querySelector("#backButton");
const postCreateForm = document.querySelector("#postCreateForm");
const postTitleInput = document.querySelector("#postTitleInput");
const postContentInput = document.querySelector("#postContentInput");
const postImageInput = document.querySelector("#postImageInput");
const selectedFileName = document.querySelector("#selectedFileName");
const postCreateHelper = document.querySelector("#postCreateHelper");
const postCreateSubmitButton = document.querySelector("#postCreateSubmitButton");

const MAX_TITLE_LENGTH = 26;
const EMPTY_MESSAGE = "*글 제목과 이야기 내용을 모두 작성해주세요";

const { fileToDataUrl } = window.utils;

let selectedImageFiles = [];

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

function renderSelectedFileNames(files) {
  if (!files || files.length === 0) {
    selectedFileName.textContent = "파일을 선택해주세요.";
    return;
  }

  const fileNames = files.map((file) => file.name).join(", ");
  selectedFileName.textContent = `${files.length}개 선택: ${fileNames}`;
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
  selectedImageFiles = Array.from(postImageInput.files || []);
  renderSelectedFileNames(selectedImageFiles);
});

postCreateForm.addEventListener("submit", async (event) => {
  event.preventDefault();

  const isValid = validateAndRenderHelper();

  if (!isValid) {
    updateSubmitButtonState();
    return;
  }

  try {
    const imageFiles = await Promise.all(
      selectedImageFiles.map((file) => fileToDataUrl(file))
    );

    await api.createPost({
      title: postTitleInput.value.trim(),
      content: postContentInput.value.trim(),
      imageFiles,
    });

    window.location.href = "./posts.html";
  } catch (error) {
    console.error("글 작성 실패:", error);
    postCreateHelper.textContent = "*글 작성에 실패했습니다.";
  }
});

backButton.addEventListener("click", () => {
  window.location.href = "./posts.html";
});

updateSubmitButtonState();
