const CURRENT_USER_ID = api.getCurrentUserId();

const backButton = document.querySelector("#backButton");
const postEditButton = document.querySelector("#postEditButton");
const postDeleteButton = document.querySelector("#postDeleteButton");

const postTitle = document.querySelector("#postTitle");
const authorImageBox = document.querySelector("#authorImageBox");
const authorImage = document.querySelector("#authorImage");
const authorNickname = document.querySelector("#authorNickname");
const postCreatedAt = document.querySelector("#postCreatedAt");
const postImageBox = document.querySelector("#postImageBox");
const postImage = document.querySelector("#postImage");
const postContent = document.querySelector("#postContent");

const likeButton = document.querySelector("#likeButton");
const likeCount = document.querySelector("#likeCount");
const viewCount = document.querySelector("#viewCount");
const commentCount = document.querySelector("#commentCount");

const commentForm = document.querySelector("#commentForm");
const commentInput = document.querySelector("#commentInput");
const commentSubmitButton = document.querySelector("#commentSubmitButton");
const commentList = document.querySelector("#commentList");

const modalOverlay = document.querySelector("#modalOverlay");
const modalTitle = document.querySelector("#modalTitle");
const modalDescription = document.querySelector("#modalDescription");
const modalCancelButton = document.querySelector("#modalCancelButton");
const modalConfirmButton = document.querySelector("#modalConfirmButton");
const postActions = document.querySelector(".detail-post-actions");





const urlParams = new URLSearchParams(window.location.search);
const postId = urlParams.get("postId");

if (!postId) {
  alert("글 정보를 찾을 수 없습니다.");
  window.location.href = "./posts.html";
}

let post = null;
let comments = [];
let modalConfirmHandler = null;
let editingCommentId = null;
setPostActionsVisible(false);

function setPostActionsVisible(isVisible) {
  postActions.classList.toggle("is-hidden", !isVisible);
  postEditButton.classList.toggle("is-hidden", !isVisible);
  postDeleteButton.classList.toggle("is-hidden", !isVisible);
}

function isCurrentUserPost() {
  return Number(post?.authorId) === Number(CURRENT_USER_ID);
}

function formatCount(count) {
  const number = Number(count) || 0;

  if (number >= 1000) {
    return `${Math.floor(number / 1000)}k`;
  }

  return String(number);
}

function formatDateTime(dateTimeValue) {
  if (!dateTimeValue) {
    return "";
  }

  const date = new Date(dateTimeValue);

  if (Number.isNaN(date.getTime())) {
    return "";
  }

  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  const hours = String(date.getHours()).padStart(2, "0");
  const minutes = String(date.getMinutes()).padStart(2, "0");
  const seconds = String(date.getSeconds()).padStart(2, "0");

  return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`;
}

function normalizePostForDetail(rawPost) {
  return {
    postId: rawPost.postId,
    title: rawPost.postTitle ?? "",
    authorId: rawPost.userId ?? null,
    authorNickname: rawPost.userName ?? "삭제된 사용자",
    authorProfileImage: rawPost.userProfileImage ?? null,
    createdAt: rawPost.createdAt ?? new Date().toISOString(),
    imageUrl: rawPost.imageFile ?? null,
    imageUrls: rawPost.imageFiles ?? [],
    content: rawPost.postContent ?? "",
    likeCount: rawPost.likeCount ?? 0,
    viewCount: rawPost.viewCount ?? 0,
    commentCount: rawPost.replyCount ?? 0,
    isLiked: rawPost.isLiked ?? false,
    comments: rawPost.comments ?? [],
  };
}

function normalizeCommentForDetail(rawComment) {
  return {
    commentId: rawComment.commentId,
    authorId: rawComment.userId ?? null,
    authorNickname: rawComment.userName ?? "삭제된 사용자",
    authorProfileImage: rawComment.userProfileImage ?? null,
    createdAt: rawComment.createdAt ?? "",
    content: rawComment.content ?? "",
  };
}

function setImage(imageBoxElement, imageElement, imageUrl) {
  if (!imageBoxElement || !imageElement) {
    return;
  }

  if (!imageUrl) {
    imageBoxElement.classList.add("is-empty");
    imageElement.removeAttribute("src");
    return;
  }

  imageBoxElement.classList.remove("is-empty");
  imageElement.src = imageUrl;
}

function renderPost() {
  if (!post) {
    return;
  }

  postTitle.textContent = post.title;
  authorNickname.textContent = post.authorNickname;
  postCreatedAt.textContent = formatDateTime(post.createdAt);
  postContent.textContent = post.content;

  const isMyPost = isCurrentUserPost();
  setPostActionsVisible(isMyPost);

  likeCount.textContent = formatCount(post.likeCount);
  viewCount.textContent = formatCount(post.viewCount);
  commentCount.textContent = formatCount(comments.length);

  if (post.isLiked) {
    likeButton.classList.add("is-liked");
  } else {
    likeButton.classList.remove("is-liked");
  }

  setImage(authorImageBox, authorImage, post.authorProfileImage);

  const firstImageUrl = post.imageUrls.length > 0
    ? post.imageUrls[0]
    : post.imageUrl;

  if (!firstImageUrl) {
    postImageBox.classList.add("is-hidden");
    postImage.removeAttribute("src");
  } else {
    postImageBox.classList.remove("is-hidden");
    postImage.src = firstImageUrl;
  }
}

function createCommentItem(comment) {
  const commentItem = document.createElement("article");
  commentItem.className = "comment-item";


  const isMyComment = Number(comment.authorId) === Number(CURRENT_USER_ID);


  commentItem.innerHTML = `
    <div class="comment-author-image-box ${comment.authorProfileImage ? "" : "is-empty"}">
      <img
        class="comment-author-image"
        src="${comment.authorProfileImage || ""}"
        alt="댓글 작성자 프로필 이미지"
      />
    </div>

    <p class="comment-author-name">${comment.authorNickname}</p>
    <p class="comment-created-at">${formatDateTime(comment.createdAt)}</p>
    <p class="comment-content">${comment.content}</p>

    ${
      isMyComment
        ? `
          <div class="comment-actions">
            <button
              type="button"
              class="comment-action-button"
              data-action="edit"
              data-comment-id="${comment.commentId}"
            >
              수정
            </button>

            <button
              type="button"
              class="comment-action-button"
              data-action="delete"
              data-comment-id="${comment.commentId}"
            >
              삭제
            </button>
          </div>
        `
        : ""
    }
  `;

  return commentItem;
}

function renderComments() {
  commentList.innerHTML = "";

  comments.forEach((comment) => {
    commentList.appendChild(createCommentItem(comment));
  });

  commentCount.textContent = formatCount(comments.length);
}

function updateCommentSubmitButton() {
  const comment = commentInput.value.trim();
  commentSubmitButton.disabled = comment.length === 0;
}

function resetCommentForm() {
  editingCommentId = null;
  commentInput.value = "";
  commentSubmitButton.textContent = "댓글 등록";
  updateCommentSubmitButton();
}

function openModal({ title, description, onConfirm }) {
  modalTitle.textContent = title;
  modalDescription.textContent = description;
  modalConfirmHandler = onConfirm;

  modalOverlay.classList.remove("is-hidden");
  modalOverlay.setAttribute("aria-hidden", "false");
  document.body.classList.add("modal-open");
}

function closeModal() {
  modalOverlay.classList.add("is-hidden");
  modalOverlay.setAttribute("aria-hidden", "true");
  document.body.classList.remove("modal-open");

  modalConfirmHandler = null;
}

async function loadPostDetail() {
  if (!postId) {
    alert("글 정보를 찾을 수 없습니다.");
    window.location.href = "./posts.html";
    return;
  }

  try {
    const rawPost = await api.getPost(postId);
    console.log("상세조회 응답:", rawPost);

    if (!rawPost || typeof rawPost !== "object") {
      throw new Error("상세조회 응답이 객체가 아닙니다.");
    }

    post = normalizePostForDetail(rawPost);

    comments = Array.isArray(post.comments)
      ? post.comments.map((comment) => normalizeCommentForDetail(comment))
      : [];

    console.log("정규화된 글:", post);

    renderPost();
    renderComments();
  } catch (error) {
    console.error("글 상세조회 실패:", error);

    postTitle.textContent = "글을 불러오지 못했습니다.";
    authorNickname.textContent = "";
    postCreatedAt.textContent = "";
    postContent.textContent =
      "백엔드 서버 실행 여부, 글 ID, 콘솔의 상세조회 응답을 확인하세요.";
    setPostActionsVisible(false);

    comments = [];
    renderComments();
  }
}

backButton.addEventListener("click", () => {
  window.location.href = "./posts.html";
});

postEditButton.addEventListener("click", () => {
  if (!post || !post.postId) {
    console.error("수정할 글 ID가 없습니다.", post);
    return;
  }

  if (!isCurrentUserPost()) {
    alert("글 작성자만 수정할 수 있습니다.");
    return;
  }

  window.location.href = `./post-edit.html?postId=${post.postId}`;
});


postDeleteButton.addEventListener("click", () => {
  if (!post || !post.postId) {
    console.error("삭제할 글 ID가 없습니다.", post);
    alert("글 ID를 찾을 수 없습니다.");
    return;
  }

  if (!isCurrentUserPost()) {
    alert("글 작성자만 삭제할 수 있습니다.");
    return;
  }

  openModal({
    title: "글을 삭제하겠습니까?",
    description: "삭제한 내용은 복구 할 수 없습니다.",
    onConfirm: async () => {
      try {
        const userId = api.getCurrentUserId();

        if (!userId) {
          alert("로그인이 필요합니다.");
          window.location.href = "./login.html";
          return;
        }

        await api.deletePost(post.postId);

        window.location.href = "./posts.html";
      } catch (error) {
        console.error("글 삭제 실패:", error);
        alert("글 삭제에 실패했습니다. 작성자 본인인지 확인해주세요.");
      }
    },
  });
});

likeButton.addEventListener("click", async () => {
  if (!post || !post.postId) {
    console.error("좋아요 처리할 글 ID가 없습니다.", post);
    return;
  }

  try {
    if (post.isLiked) {
      const result = await api.unlikePost(post.postId);

      post.isLiked = false;
      post.likeCount = result?.likeCount ?? post.likeCount - 1;
    } else {
      const result = await api.likePost(post.postId);

      post.isLiked = true;
      post.likeCount = result?.likeCount ?? post.likeCount + 1;
    }

    renderPost();
  } catch (error) {
    console.error("좋아요 처리 실패:", error);
  }
});

commentInput.addEventListener("input", updateCommentSubmitButton);

commentForm.addEventListener("submit", async (event) => {
  event.preventDefault();

  if (!post || !post.postId) {
    console.error("댓글을 등록할 글 ID가 없습니다.", post);
    return;
  }

  const content = commentInput.value.trim();

  if (!content) {
    return;
  }

  try {
    if (editingCommentId !== null) {
      await api.updateComment(post.postId, {
        commentId: editingCommentId,
        commentContent: content,
      });
    } else {
      await api.createComment(post.postId, {
        commentContent: content,
      });
    }

    await loadPostDetail();
    resetCommentForm();
  } catch (error) {
    console.error("댓글 저장 실패:", error);
  }
});

commentList.addEventListener("click", (event) => {
  const button = event.target.closest(".comment-action-button");

  if (!button) {
    return;
  }

  const commentId = Number(button.dataset.commentId);
  const action = button.dataset.action;

  const targetComment = comments.find(
    (comment) => Number(comment.commentId) === commentId
  );

  if (!targetComment) {
    return;
  }

  if (action === "edit") {
    editingCommentId = commentId;
    commentInput.value = targetComment.content;
    commentSubmitButton.textContent = "댓글 수정";
    updateCommentSubmitButton();
    commentInput.focus();
    return;
  }

  if (action === "delete") {
    openModal({
      title: "댓글을 삭제하겠습니까?",
      description: "삭제한 내용은 복구 할 수 없습니다.",
      onConfirm: async () => {
        try {
          await api.deleteComment(post.postId, {
            commentId,
          });

          await loadPostDetail();
          resetCommentForm();
        } catch (error) {
          console.error("댓글 삭제 실패:", error);
        }
      },
    });
  }
});

modalCancelButton.addEventListener("click", closeModal);

modalConfirmButton.addEventListener("click", async () => {
  try {
    if (modalConfirmHandler) {
      await modalConfirmHandler();
    }
    closeModal();
  } catch (error) {
    alert(error.message || "요청 처리에 실패했습니다.");
  }
});

loadPostDetail();
updateCommentSubmitButton();
