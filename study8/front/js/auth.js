(function () {
  const PUBLIC_PAGES = ["login.html", "signup.html"];

  const PRIVATE_PAGES = [
    "posts.html",
    "post-detail.html",
    "post-create.html",
    "post-edit.html",
    "profile-edit.html",
    "password-edit.html",
  ];

  function getCurrentPageName() {
    return window.location.pathname.split("/").pop();
  }

  function isLoggedIn() {
    const accessToken = localStorage.getItem("accessToken");
    const userId = localStorage.getItem("userId");

    return Boolean(accessToken && userId);
  }

  function clearAuth() {
    localStorage.removeItem("accessToken");
    localStorage.removeItem("userId");
  }

  function requireLogin() {
    if (!isLoggedIn()) {
      clearAuth();
      window.location.replace("./login.html");
      return false;
    }

    return true;
  }

  function redirectIfLoggedIn() {
    if (isLoggedIn()) {
      window.location.replace("./posts.html");
      return true;
    }

    return false;
  }

  const currentPageName = getCurrentPageName();

  if (PRIVATE_PAGES.includes(currentPageName)) {
    requireLogin();
  }

  if (PUBLIC_PAGES.includes(currentPageName)) {
    redirectIfLoggedIn();
  }

  
})();