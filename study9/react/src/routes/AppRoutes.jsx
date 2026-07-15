import { lazy } from "react";
import { Navigate, Route, Routes } from "react-router-dom";

import PageBoundary from "../components/layout/PageBoundary.jsx";
import ProtectedRoute from "./ProtectedRoute.jsx";
import PublicOnlyRoute from "./PublicOnlyRoute.jsx";

const LoginPage = lazy(() => import("../pages/auth/LoginPage.jsx"));
const SignupPage = lazy(() => import("../pages/auth/SignupPage.jsx"));
const PostsPage = lazy(() => import("../pages/posts/PostsPage.jsx"));
const PostDetailPage = lazy(() => import("../pages/posts/PostDetailPage.jsx"));
const PostCreatePage = lazy(() => import("../pages/posts/PostCreatePage.jsx"));
const PostEditPage = lazy(() => import("../pages/posts/PostEditPage.jsx"));
const ProfileEditPage = lazy(() => import("../pages/user/ProfileEditPage.jsx"));
const PasswordEditPage = lazy(() => import("../pages/user/PasswordEditPage.jsx"));

function page(element) {
    return <PageBoundary>{element}</PageBoundary>;
}

function AppRoutes() {
    return (
        <Routes>
            <Route path="/" element={<Navigate to="/posts" replace />} />

            <Route element={<PublicOnlyRoute />}>
                <Route path="/login" element={page(<LoginPage />)} />
                <Route path="/signup" element={page(<SignupPage />)} />
            </Route>

            <Route element={<ProtectedRoute />}>
                <Route path="/posts" element={page(<PostsPage />)} />
                <Route path="/posts/new" element={page(<PostCreatePage />)} />
                <Route path="/posts/:postId" element={page(<PostDetailPage />)} />
                <Route path="/posts/:postId/edit" element={page(<PostEditPage />)} />
                <Route path="/profile/edit" element={page(<ProfileEditPage />)} />
                <Route path="/password/edit" element={page(<PasswordEditPage />)} />
            </Route>

            <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
    );
}

export default AppRoutes;
