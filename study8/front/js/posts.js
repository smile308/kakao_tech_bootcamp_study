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

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

function normalizePostForList(post) {
  return {
    postId: post.postId,
    title: post.title ?? "",
    likeCount: post.likeCount ?? 0,
    commentCount: post.replyCount ?? 0,
    viewCount: post.viewCount ?? 0,
    reportCount: post.reportCount ?? 0,
    createdAt: post.createdAt ?? new Date().toISOString(),
    authorNickname: post.userName ?? "삭제된 사용자",
    authorProfileImage: post.userProfileImage ?? null,
  };
}

const BAMBOO_MAX_VALUE = { like: 100, view: 100, report: 5 };
// 각 지표가 "가득 자랐을 때(최대치)" 땅 전체 너비 중 차지하는 비율(%).
// 셋 다 최대치면 합이 100%가 되어 바닥을 꽉 채운다.
const BAMBOO_SEGMENT_MAX_PERCENT = { like: 50, view: 35, report: 15 };
const BAMBOO_LEAF_MAX = 24;
const BAMBOO_LEAF_COLORS = ["post-card__bamboo-leaf--a", "post-card__bamboo-leaf--b"];

function createBambooLeaf(xPercent, bottom) {
  const leaf = document.createElement("span");
  const colorClass = BAMBOO_LEAF_COLORS[Math.floor(Math.random() * BAMBOO_LEAF_COLORS.length)];
  leaf.className = `post-card__bamboo-leaf ${colorClass}`;

  const isDownward = Math.random() < 0.15;
  const angle = isDownward ? 150 + Math.random() * 60 : -55 + Math.random() * 110;

  leaf.style.left = `${xPercent}%`;
  leaf.style.bottom = `${bottom}px`;
  leaf.style.transform = `rotate(${angle}deg)`;

  return leaf;
}

/**
 * 카드 하단, 본문과 살짝 분리된 "땅" 영역에 좋아요/조회수/신고수 비례
 * 대나무 마디를 그린다. 각 지표는 땅 전체 너비 중 정해진 비율만큼만
 * 차지할 수 있고, 지표가 최대치에 도달하면 그 비율을 꽉 채우면서
 * 은은하게 빛난다(is-full). 댓글 수만큼 잎이 무작위로 흩어져 붙는다.
 * 좋아요, 조회수, 댓글, 신고가 모두 0이면 땅을 그리지 않는다.
 */
function buildGroundDecoration(post) {
  const likeCount = post.likeCount || 0;
  const viewCount = post.viewCount || 0;
  const reportCount = post.reportCount || 0;
  const commentCount = post.commentCount || 0;

  if (likeCount === 0 && viewCount === 0 && commentCount === 0 && reportCount === 0) {
    return null;
  }

  const segments = [
    { type: "like", value: likeCount },
    { type: "view", value: viewCount },
    { type: "report", value: reportCount },
  ]
    .map((segment) => {
      const ratio = Math.min(segment.value, BAMBOO_MAX_VALUE[segment.type]) / BAMBOO_MAX_VALUE[segment.type];
      return {
        type: segment.type,
        percent: ratio * BAMBOO_SEGMENT_MAX_PERCENT[segment.type],
        isFull: ratio >= 1,
      };
    })
    .filter((segment) => segment.percent > 0);

  const ground = document.createElement("div");
  ground.className = "post-card__ground";
  ground.setAttribute("aria-hidden", "true");

  const leafCount = Math.min(commentCount, BAMBOO_LEAF_MAX);

  if (segments.length > 0) {
    const bar = document.createElement("div");
    bar.className = "post-card__bamboo-bar";

    segments.forEach((segment) => {
      const segmentEl = document.createElement("span");
      segmentEl.className = `post-card__bamboo-segment post-card__bamboo-segment--${segment.type}${
        segment.isFull ? " is-full" : ""
      }`;
      segmentEl.style.width = `${segment.percent}%`;
      bar.appendChild(segmentEl);
    });

    ground.appendChild(bar);
  }

  for (let i = 0; i < leafCount; i += 1) {
    const xPercent = Math.random() * 92;
    ground.appendChild(createBambooLeaf(xPercent, 8));
  }

  return ground;
}

function createPostCard(rawPost) {
  const post = normalizePostForList(rawPost);

  const article = document.createElement("article");
  article.className = "post-card";

  article.innerHTML = `
    <div class="post-card__info">
      <div class="post-card__author">
        <div class="post-card__author-image-box ${post.authorProfileImage ? "" : "is-empty"}">
          <img
            class="post-card__author-image"
            src="${escapeHtml(post.authorProfileImage || "")}"
            alt="작성자 프로필 이미지"
          />
        </div>

        <div class="post-card__author-text">
          <p class="post-card__author-name">${escapeHtml(post.authorNickname)}</p>
        </div>
      </div>

      <div class="post-card__title-area">
        <h3 class="post-card__title">${escapeHtml(truncateTitle(post.title) || "대나무숲 글")}</h3>
      </div>

      <span class="post-card__date">${formatDateTime(post.createdAt)}</span>
      <span class="post-card__metric">${formatCount(post.likeCount)}</span>
      <span class="post-card__metric">${formatCount(post.commentCount)}</span>
      <span class="post-card__metric">${formatCount(post.viewCount)}</span>
    </div>
  `;

  article.addEventListener("click", () => {
    if (!post.postId) {
      console.error("글 ID가 없습니다.", rawPost);
      return;
    }

    window.location.href = `./post-detail.html?postId=${post.postId}`;
  });

  const ground = buildGroundDecoration(post);
  if (ground) {
    article.appendChild(ground);
  }

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
    console.error("글 목록 조회 실패:", error);
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
