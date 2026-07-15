import { Navigate, Outlet } from "react-router-dom";

import { authStorage } from "../auth/authStorage.js";

function ProtectedRoute() {
    return authStorage.isLoggedIn()
        ? <Outlet />
        : <Navigate to="/login" replace />;
}

export default ProtectedRoute;

