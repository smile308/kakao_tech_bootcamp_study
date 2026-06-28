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

const urlParams = new URLSearchParams(window.location.search);

const postId =
  urlParams.get("postId") ||
  urlParams.get("post_id") ||
  urlParams.get("id");

if (!postId) {
  alert("게시글 정보를 찾을 수 없습니다.");
  window.location.href = "./posts.html";
}

let post = null;
let comments = [];
let modalConfirmHandler = null;
let editingCommentId = null;

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
    postId: rawPost.postId ?? rawPost.post_id,
    title: rawPost.postTitle ?? rawPost.title ?? rawPost.post_title ?? "",
    authorNickname:
      rawPost.userName ??
      rawPost.user_name ??
      rawPost.authorNickname ??
      "삭제된 사용자",
    authorProfileImage:
      rawPost.userProfileImage ??
      rawPost.user_profile_image ??
      rawPost.authorProfileImage ??
      null,
    createdAt:
      rawPost.createdAt ??
      rawPost.created_at ??
      new Date().toISOString(),
    imageUrl:
      rawPost.imageFile ??
      rawPost.image_file ??
      rawPost.imageUrl ??
      null,
    content:
      rawPost.postContent ??
      rawPost.post_content ??
      rawPost.content ??
      rawPost.contents ??
      "",
    likeCount: rawPost.likeCount ?? rawPost.like_count ?? 0,
    viewCount: rawPost.viewCount ?? rawPost.view_count ?? 0,
    commentCount: rawPost.replyCount ?? rawPost.reply_count ?? 0,
    isLiked: rawPost.isLiked ?? false,
    comments: rawPost.comments ?? [],
  };
}

function normalizeCommentForDetail(rawComment) {
  return {
    commentId: rawComment.commentId ?? rawComment.comment_id,
    authorId: rawComment.userId ?? rawComment.user_id ?? null,
    authorNickname:
      rawComment.userName ??
      rawComment.user_name ??
      rawComment.authorNickname ??
      "삭제된 사용자",
    authorProfileImage:
      rawComment.userProfileImage ??
      rawComment.user_profile_image ??
      rawComment.authorProfileImage ??
      null,
    createdAt:
      rawComment.createdAt ??
      rawComment.created_at ??
      "",
    content:
      rawComment.content ??
      rawComment.commentContent ??
      rawComment.comment_content ??
      "",
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
    return;
  }

  try {
    const rawPost = await api.getPost(postId);

    console.log("상세조회 응답:", rawPost);

    post = normalizePostForDetail(rawPost);
    comments = post.comments.map((comment) =>
      normalizeCommentForDetail(comment)
    );

    console.log("정규화된 게시글:", post);

    renderPost();
    renderComments();
  } catch (error) {
    console.error("게시글 상세조회 실패:", error);
  }
}

backButton.addEventListener("click", () => {
  window.location.href = "./posts.html";
});

postEditButton.addEventListener("click", () => {
  if (!post || !post.postId) {
    console.error("수정할 게시글 ID가 없습니다.", post);
    return;
  }

  window.location.href = `./post-edit.html?postId=${post.postId}`;
});

postDeleteButton.addEventListener("click", () => {
  if (!post || !post.postId) {
    console.error("삭제할 게시글 ID가 없습니다.", post);
    return;
  }

  openModal({
    title: "게시글을 삭제하겠습니까?",
    description: "삭제한 내용은 복구 할 수 없습니다.",
    onConfirm: async () => {
      try {
        const userId = api.getCurrentUserId();

        if (!userId) {
          alert("로그인이 필요합니다.");
          window.location.href = "./login.html";
          return;
        }

        await api.deletePost(post.postId, {
          userId,
        });

        window.location.href = "./posts.html";
      } catch (error) {
        console.error("게시글 삭제 실패:", error);
        alert("게시글 삭제에 실패했습니다. 작성자 본인인지 확인해주세요.");
      }
    },
  });
});

likeButton.addEventListener("click", async () => {
  if (!post || !post.postId) {
    console.error("좋아요 처리할 게시글 ID가 없습니다.", post);
    return;
  }

  try {
    if (post.isLiked) {
      const result = await api.unlikePost(post.postId, {
        userId: api.getCurrentUserId(),
      });

      post.isLiked = false;
      post.likeCount = result?.likeCount ?? post.likeCount - 1;
    } else {
      const result = await api.likePost(post.postId, {
        userId: api.getCurrentUserId(),
      });

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
    console.error("댓글을 등록할 게시글 ID가 없습니다.", post);
    return;
  }

  const content = commentInput.value.trim();

  if (!content) {
    return;
  }

  try {
    if (editingCommentId !== null) {
      await api.updateComment(post.postId, {
        userId: api.getCurrentUserId(),
        commentId: editingCommentId,
        commentContent: content,
      });
    } else {
      await api.createComment(post.postId, {
        userId: api.getCurrentUserId(),
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
            userId: api.getCurrentUserId(),
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
  if (modalConfirmHandler) {
    await modalConfirmHandler();
  }

  closeModal();
});

loadPostDetail();
updateCommentSubmitButton();