import { request } from "./api.js";

export const userApi = {
    getMe() {
        return request("/users/me");
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
        return request("/users", {
            method: "DELETE",
        });
    },
};