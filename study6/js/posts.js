const postsList = document.querySelector("#postsList");
const writePostButton = document.querySelector("#writePostButton");

/* 지금은 기본 더미 1개 */
const posts = [
  {
    postId: 1,
    title: "제목 1",
    likeCount: 0,
    commentCount: 0,
    viewCount: 0,
    createdAt: "2021-01-01 00:00:00",
    authorNickname: "대머 작성자 1",
    authorProfileImage: null,
  },
];

/* 상단 프로필 이미지 예시 */
const currentUserProfileImage = null;

function setHeaderProfileImage(imageUrl) {
  const headerProfileButton = document.querySelector(".header-profile__button");
  const headerProfileImage = document.querySelector(".header-profile__image");

  if (!imageUrl) {
    headerProfileButton.classList.add("is-empty");
    headerProfileImage.removeAttribute("src");
    return;
  }

  headerProfileButton.classList.remove("is-empty");
  headerProfileImage.src = imageUrl;
}

function createPostCard(post) {
  const article = document.createElement("article");
  article.className = "post-card";

  article.innerHTML = `
    <div class="post-card__inner">
      <div class="post-card__content">
        <div class="post-card__top">
          <div class="post-card__top-inner">
            <div class="post-card__title-area">
              <h3 class="post-card__title">${post.title}</h3>
              <div class="post-card__meta">
                <span class="post-card__meta-item">좋아요 ${post.likeCount}</span>
                <span class="post-card__meta-item">댓글 ${post.commentCount}</span>
                <span class="post-card__meta-item">조회수 ${post.viewCount}</span>
              </div>
            </div>
            <p class="post-card__date">${post.createdAt}</p>
          </div>
        </div>

        <div class="post-card__divider"></div>

        <div class="post-card__author">
          <div class="post-card__author-image-box ${post.authorProfileImage ? "" : "is-empty"}">
            <img
              class="post-card__author-image"
              src="${post.authorProfileImage || ""}"
              alt="작성자 프로필 이미지"
            />
          </div>
          <p class="post-card__author-name">${post.authorNickname}</p>
        </div>
      </div>
    </div>
  `;

  article.addEventListener("click", () => {
    window.location.href = `./post-detail.html?postId=${post.postId}`;
  });

  return article;
}

function renderPosts(postArray) {
  postsList.innerHTML = "";

  postArray.forEach((post) => {
    const card = createPostCard(post);
    postsList.appendChild(card);
  });
}

writePostButton.addEventListener("click", () => {
  window.location.href = "./post-create.html";
});

setHeaderProfileImage(currentUserProfileImage);
renderPosts(posts);

//백엔드연결
async function fetchPosts() {
  const response = await fetch("http://localhost:8080/posts");
  const data = await response.json();
  renderPosts(data);
}

fetchPosts();