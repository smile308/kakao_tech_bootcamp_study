const postsList = document.querySelector("#postsList");
const writePostButton = document.querySelector("#writePostButton");

const INITIAL_POST_COUNT = 10;
const ADDITIONAL_POST_COUNT = 10;
const MAX_TITLE_LENGTH = 26;

let currentPage = 0;
let isLoading = false;
let hasNextPage = true;

function truncateTitle(title) {
  if (!title) {
    return "";
  }

  if (title.length <= MAX_TITLE_LENGTH) {
    return title;
  }

  return title.slice(0, MAX_TITLE_LENGTH);
}

function formatCount(count) {
  const number = Number(count) || 0;

  if (number >= 1000) {
    return `${Math.floor(number / 1000)}k`;
  }

  return String(number);
}

function formatDateTime(dateTimeValue) {
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

function normalizePostForList(post) {
  return {
    postId: post.postId ?? post.id,
    title: post.title ?? post.postTitle ?? "",
    likeCount: post.likeCount ?? 0,
    commentCount: post.replyCount ?? post.commentCount ?? 0,
    viewCount: post.viewCount ?? 0,
    createdAt:
      post.createdAt ??
      post.createdAt ??
      new Date().toISOString(),
    authorNickname:
      post.userName ??
      post.userName ??
      post.authorNickname ??
      post.nickname ??
      "삭제된 사용자",
    authorProfileImage:
      post.userProfileImage ??
      post.userProfileImage ??
      post.authorProfileImage ??
      post.profileImage ??
      null,
  };
}

function createPostCard(rawPost) {
  const post = normalizePostForList(rawPost);

  const article = document.createElement("article");
  article.className = "post-card";

  article.innerHTML = `
    <div class="post-card__inner">
      <div class="post-card__content">
        <div class="post-card__top">
          <div class="post-card__top-inner">
            <div class="post-card__title-area">
              <h3 class="post-card__title">${truncateTitle(post.title)}</h3>

              <div class="post-card__meta">
                <span class="post-card__meta-item">좋아요 ${formatCount(post.likeCount)}</span>
                <span class="post-card__meta-item">댓글 ${formatCount(post.commentCount)}</span>
                <span class="post-card__meta-item">조회수 ${formatCount(post.viewCount)}</span>
              </div>
            </div>

            <p class="post-card__date">${formatDateTime(post.createdAt)}</p>
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
  if (!post.postId) {
    console.error("게시글 ID가 없습니다.", rawPost);
    return;
  }

  window.location.href = `./post-detail.html?postId=${post.postId}`;
});

  return article;
}

function renderPosts(posts) {
  posts.forEach((post) => {
    postsList.appendChild(createPostCard(post));
  });
}

async function loadPosts(size) {
  if (isLoading || !hasNextPage) {
    return;
  }

  isLoading = true;

  try {
    const result = await api.getPosts({
      page: currentPage,
      size,
    });

    renderPosts(result.posts);

    hasNextPage = result.hasNextPage;
    currentPage += 1;
  } catch (error) {
    console.error("게시글 목록 조회 실패:", error);
  } finally {
    isLoading = false;
  }
}

writePostButton.addEventListener("click", () => {
  window.location.href = "./post-create.html";
});

const scrollObserverTarget = document.createElement("div");
scrollObserverTarget.className = "scroll-observer-target";
postsList.after(scrollObserverTarget);

const observer = new IntersectionObserver((entries) => {
  const target = entries[0];

  if (target.isIntersecting) {
    loadPosts(ADDITIONAL_POST_COUNT);
  }
});

loadPosts(INITIAL_POST_COUNT).then(() => {
  observer.observe(scrollObserverTarget);
});