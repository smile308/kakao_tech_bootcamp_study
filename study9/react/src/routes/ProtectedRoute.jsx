import {Navigate, Outlet} from "react-router-dom";
import {authStorage} from "../auth/authStorage.js";

function ProtectedRoute() {
    if (!authStorage.isLoggedIn()) {
        return <Navigate to="/login" replace />;
    }

    return <Outlet />;
}

export default ProtectedRoute;
