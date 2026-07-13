import {authStorage} from "../auth/authStorage.js";

const API_BASE_URL = "http://localhost:8080";

const PUBLIC_PATHS = ["/login", "/signup"];

async function request(endpoint, options = {}) {
    const accessToken = authStorage.getAcessToken();

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

    let data=null;

    if(responseText){
        if(contentType && contentType.includes("application/json")){
            data = JSON.parse(responseText);
        } else {
            data = responseText;
        }
    }

    if (!response.ok) {
        const errorMessage=typeof data==="string" ? data : data?.message||"요청 처리에 실패했습니다.";

        if(response.status===401){
            authStorage.removeAccessToken();
            if(!PUBLIC_PATHS.includes(window.location.pathname)){
                window.location.href="/login";
            }
        }

        const error = new Error(errorMessage);
        error.status = response.status;
        error.data = data;
        throw error;
    }

    return data;
}

export const api ={
    login({email, password}){
        return request("/sessions", {
            method: "POST",
            body: JSON.stringify({email, password}),
        });
    },
    logout(){
        return request("/sessions", {
            method: "DELETE",
        });
    },
    signup(payload){
        return request("/users", {
            method: "POST",
            body: JSON.stringify(payload),
        });
    },
    getUser(){
        return request("/users/me");
    },
    updateProfile(payload){
        return request("/users", {
            method: "PATCH",
            body: JSON.stringify(payload),
        });
    },
    updatePassword(payload){
        return request("/users/password", {
            method: "PATCH",
            body: JSON.stringify(payload),
        });
    },
    deleteUser(){
        return request("/users", {
            method: "DELETE",
        });
    },

    async getPosts ({page=0, size=10}={}){
        const params = new URLSearchParams({page:String(page), size: String(size)});
        const result = await request(`/posts?${params.toString()}`);

        return {
            posts: result?.posts ?? [],
            hasNextPage: Boolean(result?.hasNextPage,),
        };

    },

    getPost(postId){
        return request(`/posts/${postId}`);
    },

}