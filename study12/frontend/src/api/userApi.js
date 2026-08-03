import { request } from "./api.js";

export const userApi = {
    getMyInfo({ signal } = {}) {
        return request("/users/me", { signal });
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

    deleteUser() {
        return request("/users", { method: "DELETE" });
    },
};
