import { request } from "./api.js";

export const authApi = {
    login({ email, password }) {
        return request("/sessions", {
            method: "POST",
            body: JSON.stringify({ email, password }),
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
};
