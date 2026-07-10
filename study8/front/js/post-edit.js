const backButton = document.querySelector("#backButton");
const postEditForm = document.querySelector("#postEditForm");
const postTitleInput = document.querySelector("#postTitleInput");
const postContentInput = document.querySelector("#postContentInput");
const postImageInput = document.querySelector("#postImageInput");
const selectedFileName = document.querySelector("#selectedFileName");
const postEditHelper = document.querySelector("#postEditHelper");

const MAX_TITLE_LENGTH = 26;

const urlParams = new URLSearchParams(window.location.search);
const postId = urlParams.get("postId");

const { fileToDataUrl } = window.utils;

if (!postId) {
  alert("글 정보를 찾을 수 없습니다.");
  window.location.href = "./posts.html";
  throw new Error("postId가 없어 글 수정 페이지 초기화를 중단합니다.");
}

let originalPost = null;
let selectedNewImageFiles = null;

function normalizeImageFiles(post) {
  if (Array.isArray(post.imageFiles) && post.imageFiles.length > 0) {
    return post.imageFiles.filter(
      (imageFile) => imageFile && !String(imageFile).startsWith("null")
    );
  }

  if (post.imageFile) {
    return [post.imageFile];
  }

  return [];
}

function normalizePostForEdit(post) {
  const imageFiles = normalizeImageFiles(post);

  return {
    postId: post.postId,
    title: post.postTitle ?? "",
    content: post.postContent ?? "",
    imageFileName: imageFiles[0] ?? null,
    imageFiles,
  };
}

function renderImageFileSummary(imageFiles) {
  if (!imageFiles || imageFiles.length === 0) {
    selectedFileName.textContent = "파일을 선택해주세요.";
    return;
  }

  selectedFileName.textContent = `${imageFiles.length}개 이미지가 첨부되어 있습니다.`;
}

function renderSelectedFileNames(files) {
  if (!files || files.length === 0) {
    renderImageFileSummary(originalPost?.imageFiles ?? []);
    return;
  }

  const fileNames = files.map((file) => file.name).join(", ");
  selectedFileName.textContent = `${files.length}개 선택: ${fileNames}`;
}

function renderOriginalPost(post) {
  originalPost = normalizePostForEdit(post);

  postTitleInput.value = originalPost.title;
  postContentInput.value = originalPost.content;
  renderImageFileSummary(originalPost.imageFiles);
}

function validatePostEditForm() {
  const title = postTitleInput.value.trim();
  const content = postContentInput.value.trim();

  if (!title || !content) {
    postEditHelper.textContent = "*글 제목과 이야기 내용을 모두 작성해주세요.";
    return false;
  }

  postEditHelper.textContent = "";
  return true;
}

async function loadPost() {
  try {
    const post = await api.getPost(postId);

    if (post.isMine !== true) {
      alert("글 작성자만 수정할 수 있습니다.");

      window.location.replace(
        `./post-detail.html?postId=${postId}`
      );

      return;
    }

    renderOriginalPost(post);
  } catch (error) {
    console.error("글 조회 실패:", error);
    postEditHelper.textContent =
      "*글 정보를 불러오지 못했습니다.";
  }
}

postTitleInput.addEventListener("input", () => {
  if (postTitleInput.value.length > MAX_TITLE_LENGTH) {
    postTitleInput.value = postTitleInput.value.slice(0, MAX_TITLE_LENGTH);
  }
});

postImageInput.addEventListener("change", () => {
  const files = Array.from(postImageInput.files || []);

  selectedNewImageFiles = files.length > 0 ? files : null;
  renderSelectedFileNames(selectedNewImageFiles);
});

postEditForm.addEventListener("submit", async (event) => {
  event.preventDefault();

  const isValid = validatePostEditForm();

  if (!isValid) {
    return;
  }

  try {
    const imageFiles = selectedNewImageFiles
      ? await Promise.all(
          selectedNewImageFiles.map((file) => fileToDataUrl(file))
        )
      : originalPost.imageFiles;

    await api.updatePost(postId, {
      title: postTitleInput.value.trim(),
      contents: postContentInput.value.trim(),

      imageFile: imageFiles[0] ?? null,

      imageFiles,
    });

    window.location.href = `./post-detail.html?postId=${postId}`;
  } catch (error) {
  console.error("글 수정 실패:", error);

  if (error.status === 403) {
    alert("이 게시글을 수정할 권한이 없습니다.");

    window.location.replace(
      `./post-detail.html?postId=${postId}`
    );

    return;
  }

  if (error.status === 404) {
    alert("존재하지 않거나 삭제된 게시글입니다.");
    window.location.replace("./posts.html");
    return;
  }

  postEditHelper.textContent =
    "*글 수정에 실패했습니다.";
}
});

backButton.addEventListener("click", () => {
  window.location.href = `./post-detail.html?postId=${postId}`;
});

loadPost();