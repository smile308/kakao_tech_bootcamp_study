import { authStorage } from "../auth/authStorage.js";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";
const PUBLIC_PATHS = ["/login", "/signup"];

export async function request(endpoint, options = {}) {
    const accessToken = authStorage.getAccessToken();
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
    const contentType = response.headers.get("Content-Type");
    const responseText = await response.text();
    let data = null;

    if (responseText) {
        data = contentType?.includes("application/json")
            ? JSON.parse(responseText)
            : responseText;
    }

    if (!response.ok) {
        if (response.status === 401) {
            authStorage.removeAccessToken();
            if (!PUBLIC_PATHS.includes(window.location.pathname)) {
                window.location.href = "/login";
            }
        }

        const message = typeof data === "string"
            ? data
            : data?.message ?? "요청 처리에 실패했습니다.";
        const error = new Error(message);
        error.status = response.status;
        error.data = data;
        throw error;
    }

    return data;
}

