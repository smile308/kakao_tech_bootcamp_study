import { Link } from "react-router-dom";

import LogoMark from "../common/LogoMark.jsx";

function AppHeader({
    title = "대나무숲",
    showBack = false,
    backTo = "/login",
}) {
    return (
        <header className="page-header">
            {showBack && (
                <Link
                    to={backTo}
                    className="back-button"
                    aria-label="뒤로가기"
                />
            )}
            <LogoMark
                size="small"
                className="page-header__logo"
            />
            <h1 className="page-header__title">{title}</h1>
        </header>
    );
}

export default AppHeader;
