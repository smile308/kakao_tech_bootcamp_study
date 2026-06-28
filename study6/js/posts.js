const postsList = document.querySelector("#postsList");
const writePostButton = document.querySelector("#writePostButton");

const INITIAL_POST_COUNT = 10;
const ADDITIONAL_POST_COUNT = 10;
const MAX_TITLE_LENGTH = 26;

let currentPage = 0;
let isLoading = false;
let hasNextPage = true;

/*
  지금은 백엔드 연결 전이므로 더미 데이터 사용.
  나중에는 fetchPostsFromServer() 안에서 실제 API를 호출하면 됩니다.
*/
const dummyPosts = Array.from({ length: 35 }, (_, index) => {
  const postNumber = index + 1;

  return {
    postId: postNumber,
    title: `제목 ${postNumber} - 게시글 제목이 26자를 넘어가면 잘려야 합니다`,
    likeCount: postNumber * 450,
    commentCount: postNumber * 120,
    viewCount: postNumber * 1300,
    createdAt: "2021-01-01T00:00:00",
    authorNickname: `더미 작성자 ${postNumber}`,
    authorProfileImage: null,
  };
});

function truncateTitle(title) {
  if (title.length <= MAX_TITLE_LENGTH) {
    return title;
  }

  return title.slice(0, MAX_TITLE_LENGTH);
}

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

function createPostCard(post) {
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
    window.location.href = `./post-detail.html?postId=${post.postId}`;
  });

  return article;
}

function renderPosts(posts) {
  posts.forEach((post) => {
    const postCard = createPostCard(post);
    postsList.appendChild(postCard);
  });
}

function fetchPostsFromServer(page, size) {
  const startIndex = page * size;
  const endIndex = startIndex + size;

  const posts = dummyPosts.slice(startIndex, endIndex);

  return new Promise((resolve) => {
    setTimeout(() => {
      resolve({
        posts,
        hasNextPage: endIndex < dummyPosts.length,
      });
    }, 300);
  });
}

async function loadPosts(size) {
  if (isLoading || !hasNextPage) {
    return;
  }

  isLoading = true;

  try {
    const result = await fetchPostsFromServer(currentPage, size);

    renderPosts(result.posts);

    hasNextPage = result.hasNextPage;
    currentPage += 1;
  } catch (error) {
    console.error("게시글 목록 조회 실패:", error);
  } finally {
    isLoading = false;
  }
}

const scrollObserverTarget = document.createElement("div");
scrollObserverTarget.className = "scroll-observer-target";
postsList.after(scrollObserverTarget);

const observer = new IntersectionObserver((entries) => {
  const target = entries[0];

  if (target.isIntersecting) {
    loadPosts(ADDITIONAL_POST_COUNT);
  }
});

writePostButton.addEventListener("click", () => {
  window.location.href = "./post-create.html";
});

loadPosts(INITIAL_POST_COUNT).then(() => {
  observer.observe(scrollObserverTarget);
});