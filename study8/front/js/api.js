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
  return localStorage.getItem("accessSession");
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
    const postId = Number(post.postId);
    return !deletedPostIds.includes(postId);
  });
}

async function request(endpoint, options = {}) {
  const accessToken = localStorage.getItem("accessToken");

  const headers = {
    "Content-Type": "application/json",
    ...options.headers,
  };

  if (accessToken) {
    headers.Authorization = `Bearer ${accessToken}`;
  }

  const response = await fetch(`${API_BASE_URL}${endpoint}`, {
    ...options,
    headers,
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
  const errorMessage =
    typeof data === "string"
      ? data
      : data?.message || JSON.stringify(data);

  if (response.status === 401 || response.status === 403) {
    const currentPage = window.location.pathname.split("/").pop();
    const isPublicPage = currentPage === "login.html" || currentPage === "signup.html";

    localStorage.removeItem("accessToken");
    localStorage.removeItem("userId");

    if (!isPublicPage) {
      window.location.replace("./login.html");
    }
  }

  throw new Error(errorMessage);
}

  return data;
}

window.api = {
  getCurrentUserId,
  getAccessSession,

  getUser() {
  return request("/users/me");
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
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
  });

  const result = await request(`/posts?${params.toString()}`);

  return {
    posts: result?.posts || [],
    hasNextPage: Boolean(result?.hasNextPage),
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

reportPost(postId) {
  return request(`/posts/${postId}/reports`, {
    method: "POST",
    body: JSON.stringify({}),
  });
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