import {Navigate, Route, Routes} from "react-router-dom";

import ProtectedRoute from "./components/ProtectedRoute";
import PublicRoute from "./components/PublicRoute";

import LoginPage from "./pages/LoginPage";
import SignupPage from "./pages/SignupPage";

import PostsPage from "./pages/PostsPage";
import PostDetailPage from "./pages/PostDetailPage";
import PostEditPage from "./pages/PostEditPage";
import PostCreatePage from "./pages/PostCreatePage";

import ProfileEditPage from "./pages/ProfileEditPage";
import PasswordEditPage from "./pages/PasswordEditPage";

function App() {

  return (
    <Routes>
      <Route
        path="/"
        element={<Navigate to="/posts" replace />}
        />
    </Routes>
  )
}

export default App
