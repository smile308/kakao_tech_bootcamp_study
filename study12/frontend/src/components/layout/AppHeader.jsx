import { useEffect, useRef, useState } from "react";
import { Link, useNavigate } from "react-router-dom";

import { authApi } from "../../api/authApi.js";
import { userApi } from "../../api/userApi.js";
import { authStorage } from "../../auth/authStorage.js";

function AppHeader({
    title = "대나무숲",
    showBack = false,
    backTo = "/posts",
    showProfile = false,
    profileImage,
    backClassName = "detail-back-button",
}) {
    const navigate = useNavigate();
    const menuRef = useRef(null);
    const profileRequestRef = useRef(null);
    const [isMenuOpen, setIsMenuOpen] = useState(false);
    const [resolvedProfileImage, setResolvedProfileImage] = useState(
        profileImage ?? null,
    );

    useEffect(() => {
        if (!showProfile) {
            return;
        }

        if (profileImage !== undefined) {
            setResolvedProfileImage(profileImage);
            return;
        }

        let active = true;
        if (!profileRequestRef.current) {
            profileRequestRef.current = userApi.getMyInfo();
        }

        profileRequestRef.current
            .then((user) => {
                if (active) {
                    setResolvedProfileImage(user?.profileImage ?? null);
                }
            })
            .catch(() => {
                if (active) {
                    setResolvedProfileImage(null);
                }
            });

        return () => {
            active = false;
        };
    }, [profileImage, showProfile]);

    useEffect(() => {
        function closeMenu(event) {
            if (menuRef.current && !menuRef.current.contains(event.target)) {
                setIsMenuOpen(false);
            }
        }

        document.addEventListener("mousedown", closeMenu);
        return () => document.removeEventListener("mousedown", closeMenu);
    }, []);

    async function handleLogout() {
        try {
            await authApi.logout();
        } finally {
            authStorage.removeAccessToken();
            navigate("/login", { replace: true });
        }
    }

    return (
        <header className="page-header">
            {showBack && (
                <Link to={backTo} className={backClassName} aria-label="뒤로가기">
                    <span className={`${backClassName}__icon`} />
                </Link>
            )}

            <h1 className="page-header__title">{title}</h1>

            {showProfile && (
                <div className="header-profile" ref={menuRef}>
                    <button
                        type="button"
                        className={`header-profile__button ${resolvedProfileImage ? "" : "is-empty"}`.trim()}
                        aria-label="사용자 메뉴 열기"
                        aria-expanded={isMenuOpen}
                        onClick={() => setIsMenuOpen((open) => !open)}
                    >
                        {resolvedProfileImage && (
                            <img
                                src={resolvedProfileImage}
                                alt="프로필 이미지"
                                className="header-profile__image"
                            />
                        )}
                    </button>

                    <nav
                        className={`profile-menu ${isMenuOpen ? "is-open" : ""}`.trim()}
                        aria-label="사용자 메뉴"
                    >
                        <Link to="/profile/edit" className="profile-menu__item">
                            회원정보 수정
                        </Link>
                        <Link to="/password/edit" className="profile-menu__item">
                            비밀번호 수정
                        </Link>
                        <button
                            type="button"
                            className="profile-menu__item profile-menu__logout"
                            onClick={handleLogout}
                        >
                            로그아웃
                        </button>
                    </nav>
                </div>
            )}
        </header>
    );
}

export default AppHeader;
