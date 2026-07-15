const ACCESS_TOKEN_KEY = "accessToken";

export const authStorage = {
    getAccessToken() {
        return localStorage.getItem(ACCESS_TOKEN_KEY);
    },

    setAccessToken(accessToken) {
        localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
    },

    removeAccessToken() {
        localStorage.removeItem(ACCESS_TOKEN_KEY);
    },

    isLoggedIn() {
        return Boolean(localStorage.getItem(ACCESS_TOKEN_KEY));
    },
};
