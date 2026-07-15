import { Navigate, Route, Routes } from "react-router-dom";

import LoginPage from "../pages/auth/LoginPage.jsx";
import SignupPage from "../pages/auth/SignupPage.jsx";
import PasswordEditPage from "../pages/user/PasswordEditPage.jsx";
import ProfileEditPage from "../pages/user/ProfileEditPage.jsx";
import PostCreatePage from "../pages/posts/PostCreatePage.jsx";
import PostDetailPage from "../pages/posts/PostDetailPage.jsx";
import PostEditPage from "../pages/posts/PostEditPage.jsx";
import PostsPage from "../pages/posts/PostsPage.jsx";
import ProtectedRoute from "./ProtectedRoute.jsx";
import PublicOnlyRoute from "./PublicOnlyRoute.jsx";

function AppRoutes() {
    return (
        <Routes>
            <Route path="/" element={<Navigate to="/posts" replace />} />

            <Route element={<PublicOnlyRoute />}>
                <Route path="/login" element={<LoginPage />} />
                <Route path="/signup" element={<SignupPage />} />
            </Route>

            <Route element={<ProtectedRoute />}>
                <Route path="/posts" element={<PostsPage />} />
                <Route path="/posts/new" element={<PostCreatePage />} />
                <Route path="/posts/:postId" element={<PostDetailPage />} />
                <Route path="/posts/:postId/edit" element={<PostEditPage />} />
                <Route path="/profile/edit" element={<ProfileEditPage />} />
                <Route path="/password/edit" element={<PasswordEditPage />} />
            </Route>

            <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
    );
}

export default AppRoutes;

