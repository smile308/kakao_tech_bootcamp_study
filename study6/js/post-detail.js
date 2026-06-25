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

const likeCount = document.querySelector("#likeCount");
const viewCount = document.querySelector("#viewCount");
const commentCount = document.querySelector("#commentCount");

const commentForm = document.querySelector("#commentForm");
const commentInput = document.querySelector("#commentInput");
const commentSubmitButton = document.querySelector("#commentSubmitButton");
const commentList = document.querySelector("#commentList");

const dummyPost = {
  postId: 1,
  title: "제목 1",
  authorNickname: "더미 작성자 1",
  authorProfileImage: null,
  createdAt: "2021-01-01 00:00:00",
  imageUrl: null,
  content:
    "무엇을 얘기할까요? 아무말이라면, 삶은 항상 회전문 같습니다. 생각합니다. 우리는 매일 새로운 경험을 하고 배우며 성장합니다. 때로는 어려움과 도전이 있지만, 그것들이 우리를 더 강하고 지혜롭게 만듭니다. 또한 우리는 주변의 사람들과 연결되어 사랑과 지지를 받습니다. 그래서 우리의 삶은 소중하고 의미가 있습니다.",
  likeCount: 123,
  viewCount: 123,
  commentCount: 123,
};

const dummyComments = [
  {
    commentId: 1,
    authorNickname: "더미 작성자 1",
    authorProfileImage: null,
    createdAt: "2021-01-01 00:00:00",
    content: "댓글 내용",
  },
];

function setImage(imageBoxElement, imageElement, imageUrl) {
  if (!imageUrl) {
    imageBoxElement.classList.add("is-empty");
    imageElement.removeAttribute("src");
    return;
  }

  imageBoxElement.classList.remove("is-empty");
  imageElement.src = imageUrl;
}

function renderPost(post) {
  postTitle.textContent = post.title;
  authorNickname.textContent = post.authorNickname;
  postCreatedAt.textContent = post.createdAt;
  postContent.textContent = post.content;

  likeCount.textContent = post.likeCount;
  viewCount.textContent = post.viewCount;
  commentCount.textContent = post.commentCount;

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

  commentItem.innerHTML = `
    <div class="comment-author-image-box ${comment.authorProfileImage ? "" : "is-empty"}">
      <img
        class="comment-author-image"
        src="${comment.authorProfileImage || ""}"
        alt="댓글 작성자 프로필 이미지"
      />
    </div>

    <p class="comment-author-name">${comment.authorNickname}</p>
    <p class="comment-created-at">${comment.createdAt}</p>
    <p class="comment-content">${comment.content}</p>

    <div class="comment-actions">
      <button type="button" class="comment-action-button">수정</button>
      <button type="button" class="comment-action-button">삭제</button>
    </div>
  `;

  return commentItem;
}

function renderComments(comments) {
  commentList.innerHTML = "";

  if (!comments || comments.length === 0) {
    return;
  }

  comments.forEach((comment) => {
    commentList.appendChild(createCommentItem(comment));
  });
}

function updateCommentSubmitButton() {
  const comment = commentInput.value.trim();
  commentSubmitButton.disabled = comment.length === 0;
}

backButton.addEventListener("click", () => {
  window.location.href = "./posts.html";
});

postEditButton.addEventListener("click", () => {
  const postId = dummyPost.postId;
  window.location.href = `./post-edit.html?postId=${postId}`;
});

postDeleteButton.addEventListener("click", () => {
  const confirmed = confirm("게시글을 삭제하시겠습니까?");

  if (!confirmed) {
    return;
  }

  // 이후 DELETE /posts/{postId} 연동
  window.location.href = "./posts.html";
});

commentInput.addEventListener("input", updateCommentSubmitButton);

commentForm.addEventListener("submit", (event) => {
  event.preventDefault();

  const content = commentInput.value.trim();

  if (!content) {
    return;
  }

  // 이후 POST /posts/{postId}/comments 연동
  console.log("댓글 등록:", content);

  commentInput.value = "";
  updateCommentSubmitButton();
});

renderPost(dummyPost);
renderComments(dummyComments);
updateCommentSubmitButton();