import {Navigate, Route, Routes} from "react-router";

import ProtectedRoute from "./routes/ProtectedRoute";
import PublicOnlyRoute from "./routes/PublicOnlyRoute";

import LoginPage from "./pages/auth/LoginPage";
import SignupPage from "./pages/auth/SignupPage";

import PostsPage from "./pages/posts/PostsPage";
import PostDetailPage from "./pages/posts/PostDetailPage";
import PostEditPage from "./pages/posts/PostEditPage";
import PostCreatePage from "./pages/posts/PostCreatePage";

import ProfileEditPage from "./pages/user/ProfileEditPage";
import PasswordEditPage from "./pages/user/PasswordEditPage";

function App() {

  return (
    <Routes>
      <Route
        path="/"
        element={<Navigate to="/posts" replace />}
        />
      <Route element={<PublicOnlyRoute />}>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/signup" element={<SignupPage />} />
      </Route>
      <Route element={<ProtectedRoute />}>
        <Route path="/posts" element={<PostsPage />} />
        <Route path="/posts/:postId" element={<PostDetailPage />} />
        <Route path="/posts/:postId/edit" element={<PostEditPage />} />
        <Route path="/posts/new" element={<PostCreatePage />} />
        <Route path="/profile/edit" element={<ProfileEditPage />} />
        <Route path="/password/edit" element={<PasswordEditPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}

export default App
