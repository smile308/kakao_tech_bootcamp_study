const CURRENT_USER_ID = 1;

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

let modalConfirmHandler = null;
let editingCommentId = null;

let post = {
  postId: 1,
  authorId: 1,
  title: "제목 1",
  authorNickname: "더미 작성자 1",
  authorProfileImage: null,
  createdAt: "2021-01-01T00:00:00",
  imageUrl: null,
  content:
    "무엇을 얘기할까요? 아무말이라면, 삶은 항상 회전문 같습니다. 생각합니다. 우리는 매일 새로운 경험을 하고 배우며 성장합니다.",
  likeCount: 1230,
  viewCount: 10000,
  commentCount: 1,
  isLiked: false,
};

let comments = [
  {
    commentId: 1,
    authorId: 1,
    authorNickname: "더미 작성자 1",
    authorProfileImage: null,
    createdAt: "2021-01-01T00:00:00",
    content: "댓글 내용",
  },
];

function formatCount(count) {
  if (count >= 1000) {
    return `${Math.floor(count / 1000)}k`;
  }

  return String(count);
}

function formatDateTime(dateTimeValue) {
  const date = new Date(dateTimeValue);

  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  const hours = String(date.getHours()).padStart(2, "0");
  const minutes = String(date.getMinutes()).padStart(2, "0");
  const seconds = String(date.getSeconds()).padStart(2, "0");

  return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`;
}

function setImage(imageBoxElement, imageElement, imageUrl) {
  if (!imageUrl) {
    imageBoxElement.classList.add("is-empty");
    imageElement.removeAttribute("src");
    return;
  }

  imageBoxElement.classList.remove("is-empty");
  imageElement.src = imageUrl;
}

function renderPost() {
  postTitle.textContent = post.title;
  authorNickname.textContent = post.authorNickname;
  postCreatedAt.textContent = formatDateTime(post.createdAt);
  postContent.textContent = post.content;

  likeCount.textContent = formatCount(post.likeCount);
  viewCount.textContent = formatCount(post.viewCount);
  commentCount.textContent = formatCount(comments.length);

  if (post.isLiked) {
    likeButton.classList.add("is-liked");
  } else {
    likeButton.classList.remove("is-liked");
  }

  setImage(authorImageBox, authorImage, post.authorProfileImage);

  if (!post.imageUrl) {
    postImageBox.classList.add("is-hidden");
    postImage.removeAttribute("src");
  } else {
    postImageBox.classList.remove("is-hidden");
    postImage.src = post.imageUrl;
  }
}

function createCommentItem(comment) {
  const commentItem = document.createElement("article");
  commentItem.className = "comment-item";

  const isMyComment = comment.authorId === CURRENT_USER_ID;

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
            <button type="button" class="comment-action-button" data-action="edit" data-comment-id="${comment.commentId}">
              수정
            </button>
            <button type="button" class="comment-action-button" data-action="delete" data-comment-id="${comment.commentId}">
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

  post.commentCount = comments.length;
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

backButton.addEventListener("click", () => {
  window.location.href = "./posts.html";
});

postEditButton.addEventListener("click", () => {
  window.location.href = `./post-edit.html?postId=${post.postId}`;
});

postDeleteButton.addEventListener("click", () => {
  openModal({
    title: "게시글을 삭제하겠습니까?",
    description: "삭제한 내용은 복구 할 수 없습니다.",
    onConfirm: () => {
      // 이후 백엔드 연결 시 DELETE /posts/{postId}
      post = null;
      window.location.href = "./posts.html";
    },
  });
});

likeButton.addEventListener("click", () => {
  if (post.isLiked) {
    post.isLiked = false;
    post.likeCount -= 1;
  } else {
    post.isLiked = true;
    post.likeCount += 1;
  }

  renderPost();
});

commentInput.addEventListener("input", updateCommentSubmitButton);

commentForm.addEventListener("submit", (event) => {
  event.preventDefault();

  const content = commentInput.value.trim();

  if (!content) {
    return;
  }

  if (editingCommentId !== null) {
    comments = comments.map((comment) => {
      if (comment.commentId !== editingCommentId) {
        return comment;
      }

      return {
        ...comment,
        content,
        createdAt: new Date().toISOString(),
      };
    });

    resetCommentForm();
    renderComments();
    return;
  }

  const newComment = {
    commentId: Date.now(),
    authorId: CURRENT_USER_ID,
    authorNickname: "더미 작성자 1",
    authorProfileImage: null,
    createdAt: new Date().toISOString(),
    content,
  };

  comments.push(newComment);

  resetCommentForm();
  renderComments();
});

commentList.addEventListener("click", (event) => {
  const button = event.target.closest(".comment-action-button");

  if (!button) {
    return;
  }

  const commentId = Number(button.dataset.commentId);
  const action = button.dataset.action;

  const targetComment = comments.find((comment) => comment.commentId === commentId);

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
      onConfirm: () => {
        comments = comments.filter((comment) => comment.commentId !== commentId);
        resetCommentForm();
        renderComments();
      },
    });
  }
});

modalCancelButton.addEventListener("click", closeModal);

modalConfirmButton.addEventListener("click", () => {
  if (modalConfirmHandler) {
    modalConfirmHandler();
  }

  closeModal();
});

modalOverlay.addEventListener("click", (event) => {
  if (event.target === modalOverlay) {
    return;
  }
});

renderPost();
renderComments();
updateCommentSubmitButton();