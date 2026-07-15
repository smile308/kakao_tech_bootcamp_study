import { Navigate, Outlet } from "react-router-dom";

import { authStorage } from "../auth/authStorage.js";

function PublicOnlyRoute() {
    if (authStorage.isLoggedIn()) {
        return <Navigate to="/posts" replace />;
    }

    return <Outlet />;
}

export default PublicOnlyRoute;
