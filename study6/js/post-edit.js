const backButton = document.querySelector("#backButton");
const postEditForm = document.querySelector("#postEditForm");
const postTitleInput = document.querySelector("#postTitleInput");
const postContentInput = document.querySelector("#postContentInput");
const postImageInput = document.querySelector("#postImageInput");
const selectedFileName = document.querySelector("#selectedFileName");
const postEditHelper = document.querySelector("#postEditHelper");

backButton.addEventListener("click", () => {
  window.location.href = "./post-detail.html";
});

postImageInput.addEventListener("change", () => {
  const file = postImageInput.files[0];

  if (!file) {
    selectedFileName.textContent = "기존 파일 명";
    return;
  }

  selectedFileName.textContent = file.name;
});

postEditForm.addEventListener("submit", (event) => {
  event.preventDefault();

  const title = postTitleInput.value.trim();
  const content = postContentInput.value.trim();
  const imageFile = postImageInput.files[0];

  if (!title || !content) {
    postEditHelper.textContent = "*제목, 내용을 모두 작성해주세요.";
    return;
  }

  postEditHelper.textContent = "";

  const formData = new FormData();
  formData.append("title", title);
  formData.append("content", content);

  if (imageFile) {
    formData.append("image", imageFile);
  }

  // 이후 백엔드 API 연동 예정
  // PATCH /posts/{postId}
  console.log("게시글 수정 요청", {
    title,
    content,
    imageFile,
  });

  window.location.href = "./post-detail.html";
});