const profileMenuButton = document.querySelector("#profileMenuButton");
const profileMenu = document.querySelector("#profileMenu");
const logoutButton = document.querySelector("#logoutButton");

function openProfileMenu() {
  if (!profileMenu) {
    return;
  }

  profileMenu.classList.add("is-open");
}

function closeProfileMenu() {
  if (!profileMenu) {
    return;
  }

  profileMenu.classList.remove("is-open");
}

function toggleProfileMenu() {
  if (!profileMenu) {
    return;
  }

  profileMenu.classList.toggle("is-open");
}

if (profileMenuButton && profileMenu) {
  profileMenuButton.addEventListener("click", (event) => {
    event.stopPropagation();
    toggleProfileMenu();
  });

  profileMenu.addEventListener("click", (event) => {
    event.stopPropagation();
  });

  document.addEventListener("click", () => {
    closeProfileMenu();
  });

  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape") {
      closeProfileMenu();
    }
  });
}

if (logoutButton) {
  logoutButton.addEventListener("click", async () => {
    try {
      if (window.api && typeof api.logout === "function") {
        await api.logout();
      }
    } catch (error) {
      console.error("로그아웃 실패:", error);
    } finally {
      localStorage.removeItem("userId");
      localStorage.removeItem("accessToken");
      window.location.href = "./login.html";
    }
  });
}