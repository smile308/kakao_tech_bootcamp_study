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

postCreateForm.addEventListener("submit", async (event) => {
  event.preventDefault();

  const isValid = validateAndRenderHelper();

  if (!isValid) {
    updateSubmitButtonState();
    return;
  }

  try {
    const imageFile = selectedImageFile
      ? await fileToDataUrl(selectedImageFile)
      : null;

    await api.createPost({
      userId: api.getCurrentUserId(),
      title: postTitleInput.value.trim(),
      contents: postContentInput.value.trim(),
      imageFile,
    });

    window.location.href = "./posts.html";
  } catch (error) {
    console.error("게시글 작성 실패:", error);
    postCreateHelper.textContent = "*게시글 작성에 실패했습니다.";
  }
});

backButton.addEventListener("click", () => {
  window.location.href = "./posts.html";
});

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

updateSubmitButtonState();