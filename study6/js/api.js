const API_BASE_URL = "http://localhost:8080";

async function request(endpoint, options = {}) {
  const response = await fetch(`${API_BASE_URL}${endpoint}`, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      ...options.headers,
    },
  });

  const contentType = response.headers.get("content-type");
  const hasJsonBody = contentType && contentType.includes("application/json");

  const data = hasJsonBody ? await response.json() : null;

  if (!response.ok) {
    throw data || new Error("API 요청에 실패했습니다.");
  }

  return data;
}