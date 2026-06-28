const backButton = document.querySelector("#backButton");
const postEditForm = document.querySelector("#postEditForm");
const postTitleInput = document.querySelector("#postTitleInput");
const postContentInput = document.querySelector("#postContentInput");
const postImageInput = document.querySelector("#postImageInput");
const selectedFileName = document.querySelector("#selectedFileName");
const postEditHelper = document.querySelector("#postEditHelper");

const MAX_TITLE_LENGTH = 26;

const urlParams = new URLSearchParams(window.location.search);
const postId = urlParams.get("postId") || "1";

let originalPost = null;
let selectedNewImageFile = null;

function normalizePostForEdit(post) {
  return {
    postId: post.postId ?? post.post_id,
    title: post.postTitle ?? post.post_title ?? post.title ?? "",
    content: post.postContent ?? post.post_content ?? post.content ?? "",
    imageFileName: post.imageFile ?? post.image_file ?? null,
  };
}

function renderOriginalPost(post) {
  originalPost = normalizePostForEdit(post);

  postTitleInput.value = originalPost.title;
  postContentInput.value = originalPost.content;

  if (originalPost.imageFileName) {
    selectedFileName.textContent = originalPost.imageFileName;
  } else {
    selectedFileName.textContent = "파일을 선택해주세요.";
  }
}

function validatePostEditForm() {
  const title = postTitleInput.value.trim();
  const content = postContentInput.value.trim();

  if (!title || !content) {
    postEditHelper.textContent = "*제목, 내용을 모두 작성해주세요.";
    return false;
  }

  postEditHelper.textContent = "";
  return true;
}

async function loadPost() {
  try {
    const post = await api.getPost(postId);
    renderOriginalPost(post);
  } catch (error) {
    console.error("게시글 조회 실패:", error);
    postEditHelper.textContent = "*게시글 정보를 불러오지 못했습니다.";
  }
}

postTitleInput.addEventListener("input", () => {
  if (postTitleInput.value.length > MAX_TITLE_LENGTH) {
    postTitleInput.value = postTitleInput.value.slice(0, MAX_TITLE_LENGTH);
  }
});

postImageInput.addEventListener("change", () => {
  const file = postImageInput.files[0];

  if (!file) {
    selectedNewImageFile = null;

    if (originalPost && originalPost.imageFileName) {
      selectedFileName.textContent = originalPost.imageFileName;
    } else {
      selectedFileName.textContent = "파일을 선택해주세요.";
    }

    return;
  }

  selectedNewImageFile = file;
  selectedFileName.textContent = file.name;
});

postEditForm.addEventListener("submit", async (event) => {
  event.preventDefault();

  const isValid = validatePostEditForm();

  if (!isValid) {
    return;
  }

  try {
    await api.updatePost(postId, {
      userId: api.getCurrentUserId(),
      title: postTitleInput.value.trim(),
      contents: postContentInput.value.trim(),
      imageFile: selectedNewImageFile
        ? selectedNewImageFile.name
        : originalPost.imageFileName,
    });

    window.location.href = `./post-detail.html?postId=${postId}`;
  } catch (error) {
    console.error("게시글 수정 실패:", error);
    postEditHelper.textContent = "*게시글 수정에 실패했습니다.";
  }
});

backButton.addEventListener("click", () => {
  window.location.href = `./post-detail.html?postId=${postId}`;
});

loadPost();