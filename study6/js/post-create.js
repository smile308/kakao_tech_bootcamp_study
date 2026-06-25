const backButton = document.querySelector("#backButton");
const postCreateForm = document.querySelector("#postCreateForm");
const postTitleInput = document.querySelector("#postTitleInput");
const postContentInput = document.querySelector("#postContentInput");
const postImageInput = document.querySelector("#postImageInput");
const selectedFileName = document.querySelector("#selectedFileName");
const postCreateHelper = document.querySelector("#postCreateHelper");
const postCreateSubmitButton = document.querySelector("#postCreateSubmitButton");

const TITLE_EMPTY_MESSAGE = "*제목을 입력해주세요.";
const CONTENT_EMPTY_MESSAGE = "*내용을 입력해주세요.";

backButton.addEventListener("click", () => {
  window.location.href = "./posts.html";
});

function validateForm() {
  const title = postTitleInput.value.trim();
  const content = postContentInput.value.trim();

  return title.length > 0 && content.length > 0;
}

function updateSubmitButtonState() {
  postCreateSubmitButton.disabled = !validateForm();
}

function validateAndRenderHelper() {
  const title = postTitleInput.value.trim();
  const content = postContentInput.value.trim();

  if (!title) {
    postCreateHelper.textContent = TITLE_EMPTY_MESSAGE;
    updateSubmitButtonState();
    return false;
  }

  if (!content) {
    postCreateHelper.textContent = CONTENT_EMPTY_MESSAGE;
    updateSubmitButtonState();
    return false;
  }

  postCreateHelper.textContent = "";
  updateSubmitButtonState();
  return true;
}

postTitleInput.addEventListener("input", validateAndRenderHelper);
postContentInput.addEventListener("input", validateAndRenderHelper);

postImageInput.addEventListener("change", () => {
  const file = postImageInput.files[0];

  if (!file) {
    selectedFileName.textContent = "파일을 선택해주세요.";
    return;
  }

  selectedFileName.textContent = file.name;
});

postCreateForm.addEventListener("submit", (event) => {
  event.preventDefault();

  const isValid = validateAndRenderHelper();

  if (!isValid) {
    return;
  }

  const title = postTitleInput.value.trim();
  const content = postContentInput.value.trim();
  const imageFile = postImageInput.files[0];

  const formData = new FormData();
  formData.append("title", title);
  formData.append("content", content);

  if (imageFile) {
    formData.append("image", imageFile);
  }

  // 이후 백엔드 API 연동 예정
  // POST /posts
  console.log("게시글 작성 요청", {
    title,
    content,
    imageFile,
  });

  window.location.href = "./posts.html";
});

updateSubmitButtonState();