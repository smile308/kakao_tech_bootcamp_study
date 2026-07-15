import { Navigate, Outlet } from "react-router-dom";

import { authStorage } from "../auth/authStorage.js";

function PublicOnlyRoute() {
    return authStorage.isLoggedIn()
        ? <Navigate to="/posts" replace />
        : <Outlet />;
}

export default PublicOnlyRoute;

