const API_BASE_URL = "http://localhost:8080";

function getCurrentUserId() {
  const userId = Number(localStorage.getItem("userId"));

  if (!userId) {
    console.error("로그인 사용자 ID가 없습니다.");
    return null;
  }

  return userId;
}

function getAccessSession() {
  return localStorage.getItem("accessSession") || "000000";
}

function getDeletedPostIds() {
  try {
    return JSON.parse(localStorage.getItem("deletedPostIds") || "[]");
  } catch (error) {
    console.error("삭제 게시글 목록 파싱 실패:", error);
    return [];
  }
}

function rememberDeletedPostId(postId) {
  const id = Number(postId);

  if (!id) {
    return;
  }

  const deletedPostIds = getDeletedPostIds();

  if (!deletedPostIds.includes(id)) {
    deletedPostIds.push(id);
    localStorage.setItem("deletedPostIds", JSON.stringify(deletedPostIds));
  }
}

function filterDeletedPosts(posts) {
  const deletedPostIds = getDeletedPostIds();

  return posts.filter((post) => {
    const postId = Number(post.postId ?? post.id);
    return !deletedPostIds.includes(postId);
  });
}

async function request(endpoint, options = {}) {
  const response = await fetch(`${API_BASE_URL}${endpoint}`, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      ...options.headers,
    },
  });

  const contentType = response.headers.get("content-type");
  const responseText = await response.text();

  let data = null;

  if (responseText) {
    if (contentType && contentType.includes("application/json")) {
      data = JSON.parse(responseText);
    } else {
      data = responseText;
    }
  }

  if (!response.ok) {
    console.error("API 요청 실패:", {
      endpoint,
      status: response.status,
      data,
    });

    throw new Error(
      typeof data === "string"
        ? data
        : data?.message || JSON.stringify(data)
    );
  }

  return data;
}

window.api = {
  getCurrentUserId,
  getAccessSession,

  getUser(userId = getCurrentUserId()) {
    return request(`/users/${userId}`);
  },

  login({ email, password }) {
    return request("/sessions", {
      method: "POST",
      body: JSON.stringify({
        email,
        password,
      }),
    });
  },

  logout() {
    return request("/sessions", {
      method: "DELETE",
      body: JSON.stringify({
        userId: getCurrentUserId(),
        accessSession: getAccessSession(),
      }),
    });
  },

  signup(payload) {
    return request("/users", {
      method: "POST",
      body: JSON.stringify(payload),
    });
  },

  updateProfile(payload) {
  return request("/users", {
    method: "PATCH",
    body: JSON.stringify(payload),
  });
  },

  updatePassword(payload) {
    return request("/users/password", {
      method: "PATCH",
      body: JSON.stringify(payload),
    });
  },

  deleteUser(payload) {
    return request("/users", {
      method: "DELETE",
      body: JSON.stringify(payload),
    });
  },

async getPosts({ page = 0, size = 10 } = {}) {
  const result = await request("/posts");

  const posts = filterDeletedPosts(
    Array.isArray(result) ? result : result?.posts || []
  );

  const startIndex = page * size;
  const endIndex = startIndex + size;

  return {
    posts: posts.slice(startIndex, endIndex),
    hasNextPage: endIndex < posts.length,
  };
},

  getPost(postId) {
    return request(`/posts/${postId}`);
  },

  createPost(payload) {
    return request("/posts", {
      method: "POST",
      body: JSON.stringify(payload),
    });
  },

  updatePost(postId, payload) {
    return request(`/posts/${postId}`, {
      method: "PATCH",
      body: JSON.stringify(payload),
    });
  },

  async deletePost(postId, payload) {
  const result = await request(`/posts/${postId}`, {
    method: "DELETE",
    body: JSON.stringify(payload),
  });

  rememberDeletedPostId(postId);

  return result;
},

  likePost(postId, payload) {
    return request(`/posts/${postId}/likes`, {
      method: "POST",
      body: JSON.stringify(payload),
    });
  },

  unlikePost(postId, payload) {
    return request(`/posts/${postId}/likes`, {
      method: "DELETE",
      body: JSON.stringify(payload),
    });
  },

  createComment(postId, payload) {
    return request(`/posts/${postId}/comments`, {
      method: "POST",
      body: JSON.stringify(payload),
    });
  },

  updateComment(postId, payload) {
    return request(`/posts/${postId}/comments`, {
      method: "PATCH",
      body: JSON.stringify(payload),
    });
  },

  deleteComment(postId, payload) {
    return request(`/posts/${postId}/comments`, {
      method: "DELETE",
      body: JSON.stringify(payload),
    });
  },
};