import { authStorage } from "../auth/authStorage.js";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";
const PUBLIC_PATHS = ["/login", "/signup"];
const NO_REFRESH_ENDPOINTS = new Set(["/sessions", "/sessions/refresh", "/users"]);

let refreshPromise = null;

async function sendRequest(endpoint, options = {}, includeAccessToken = true) {
    const accessToken = authStorage.getAccessToken();
    const headers = {
        "Content-Type": "application/json",
        ...options.headers,
    };

    if (includeAccessToken && accessToken) {
        headers.Authorization = `Bearer ${accessToken}`;
    }

    const response = await fetch(`${API_BASE_URL}${endpoint}`, {
        ...options,
        headers,
        credentials: "include",
    });

    return {
        response,
        data: await readResponseData(response),
    };
}

async function readResponseData(response) {
    const contentType = response.headers.get("content-type");
    const responseText = await response.text();

    if (!responseText) {
        return null;
    }

    return contentType?.includes("application/json")
        ? JSON.parse(responseText)
        : responseText;
}

function createRequestError(response, data) {
    const errorMessage =
        typeof data === "string"
            ? data
            : data?.message ?? "요청 처리에 실패했습니다.";

    const error = new Error(errorMessage);
    error.status = response.status;
    error.data = data;
    return error;
}

function clearAuthentication() {
    authStorage.removeAccessToken();

    if (!PUBLIC_PATHS.includes(window.location.pathname)) {
        window.location.replace("/login");
    }
}

async function performRefresh() {
    const result = await sendRequest(
        "/sessions/refresh",
        { method: "POST" },
        false,
    );

    if (!result.response.ok) {
        throw createRequestError(result.response, result.data);
    }

    if (!result.data?.accessToken) {
        throw new Error("액세스 토큰 재발급 응답이 올바르지 않습니다.");
    }

    authStorage.setAccessToken(result.data.accessToken);
    return result.data;
}

export function refreshAccessToken() {
    if (!refreshPromise) {
        refreshPromise = performRefresh().finally(() => {
            refreshPromise = null;
        });
    }

    return refreshPromise;
}

export async function request(endpoint, options = {}) {
    let result = await sendRequest(endpoint, options);

    if (result.response.status === 401 && !NO_REFRESH_ENDPOINTS.has(endpoint)) {
        try {
            await refreshAccessToken();
            result = await sendRequest(endpoint, options);
        } catch (error) {
            if (error.status === 401) {
                clearAuthentication();
            }

            throw error;
        }
    }

    if (!result.response.ok) {
        if (result.response.status === 401) {
            clearAuthentication();
        }

        throw createRequestError(result.response, result.data);
    }

    return result.data;
}
