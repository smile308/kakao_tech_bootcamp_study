import { authStorage } from "../auth/authStorage.js";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";
const PUBLIC_PATHS = new Set(["/login", "/signup"]);

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

    const contentType = response.headers.get("content-type");
    const responseText = await response.text();
    let data = null;

    if (responseText) {
        data = contentType?.includes("application/json")
            ? JSON.parse(responseText)
            : responseText;
    }

    if (!response.ok) {
        const errorMessage =
            typeof data === "string"
                ? data
                : data?.message ?? "요청 처리에 실패했습니다.";

        if (response.status === 401) {
            authStorage.removeAccessToken();

            if (!PUBLIC_PATHS.has(window.location.pathname)) {
                window.location.replace("/login");
            }
        }

        const error = new Error(errorMessage);
        error.status = response.status;
        error.data = data;
        throw error;
    }

    return data;
}
