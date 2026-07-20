import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";

import { userApi } from "../../api/userApi.js";
import { authStorage } from "../../auth/authStorage.js";
import ConfirmModal from "../../components/common/ConfirmModal.jsx";
import ErrorView from "../../components/common/ErrorView.jsx";
import Toast from "../../components/common/Toast.jsx";
import { ErrorBoundary } from "react-error-boundary";
import InfoBanner from "../../components/layout/InfoBanner.jsx";
import PageLayout from "../../components/layout/PageLayout.jsx";
import ProfileEditForm from "../../components/user/ProfileEditForm.jsx";
import { fileToDataUrl } from "../../utils/file.js";
import { isValidNickname } from "../../utils/validation.js";
import "../../styles/user.css";

function ProfileEditPage() {
    const navigate = useNavigate();
    const toastTimerRef = useRef(null);
    const profileRequestRef = useRef(null);
    const [user, setUser] = useState(null);
    const [nickname, setNickname] = useState("");
    const [newProfileImage, setNewProfileImage] = useState(null);
    const [previewUrl, setPreviewUrl] = useState(null);
    const [nicknameError, setNicknameError] = useState("");
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [isWithdrawModalOpen, setIsWithdrawModalOpen] = useState(false);
    const [toast, setToast] = useState({ isVisible: false, message: "" });

    useEffect(() => {
        let active = true;

        if (!profileRequestRef.current) {
            profileRequestRef.current = userApi.getMyInfo();
        }

        profileRequestRef.current
            .then((result) => {
                if (!active) {
                    return;
                }
                setUser(result);
                setNickname(result.nickname ?? "");
                setPreviewUrl(result.profileImage ?? null);
            })
            .catch((error) => {
                if (active) {
                    console.error("회원정보 조회 실패", error);
                }
            });

        return () => {
            active = false;
            window.clearTimeout(toastTimerRef.current);
        };
    }, []);

    function showToast(message) {
        window.clearTimeout(toastTimerRef.current);
        setToast({ isVisible: true, message });
        toastTimerRef.current = window.setTimeout(() => {
            setToast({ isVisible: false, message: "" });
        }, 2000);
    }

    function handleNicknameChange(event) {
        setNickname(event.target.value);
        setNicknameError("");
    }

    async function handleImageChange(event) {
        const file = event.target.files?.[0];
        if (!file) {
            return;
        }

        try {
            const convertedImage = await fileToDataUrl(file);
            setNewProfileImage(convertedImage);
            setPreviewUrl(convertedImage);
        } catch {
            window.alert("프로필 이미지를 읽지 못했습니다.");
        }
    }

    async function handleSubmit(event) {
        event.preventDefault();
        if (!isValidNickname(nickname)) {
            setNicknameError("닉네임은 공백 없이 1~10자로 입력해주세요.");
            return;
        }

        try {
            setIsSubmitting(true);
            setNicknameError("");
            await userApi.updateProfile({
                nickname,
                profileImage: newProfileImage ?? user?.profileImage ?? null,
            });
            setUser((previous) => ({
                ...previous,
                nickname,
                profileImage: newProfileImage ?? previous.profileImage,
            }));
            setNewProfileImage(null);
            showToast("회원정보가 수정되었습니다.");
        } catch (error) {
            if (error.message?.includes("Nickname") || error.message?.includes("닉네임")) {
                setNicknameError("이미 사용 중인 닉네임입니다.");
            } else {
                setNicknameError(error.message || "회원정보 수정에 실패했습니다.");
            }
        } finally {
            setIsSubmitting(false);
        }
    }

    async function handleWithdraw() {
        try {
            await userApi.deleteUser();
            authStorage.removeAccessToken();
            navigate("/login", { replace: true });
        } catch (error) {
            setIsWithdrawModalOpen(false);
            window.alert(error.message || "회원 탈퇴에 실패했습니다.");
        }
    }

    return (
        <PageLayout
            pageClassName="profile-edit-page"
            mainClassName="profile-edit-section"
            title="계정 설정"
            showBack
            backTo="/posts"
            profileImage={previewUrl}
        >
            <h2 className="profile-edit-title">회원정보 수정</h2>
            <InfoBanner
                eyebrow="Night Profile"
                title="숲에서 나를 나타내는 조각들"
                description="글과 댓글에 함께 붙는 닉네임과 사진을 정돈해요."
            />

            {user ? (
                <ErrorBoundary fallback={<ErrorView title="회원정보 수정 양식을 표시하지 못했습니다." />}>
                    <ProfileEditForm
                        email={user.email}
                        nickname={nickname}
                        previewUrl={previewUrl}
                        nicknameError={nicknameError}
                        isSubmitting={isSubmitting}
                        onNicknameChange={handleNicknameChange}
                        onImageChange={handleImageChange}
                        onSubmit={handleSubmit}
                        onWithdraw={() => setIsWithdrawModalOpen(true)}
                    />
                </ErrorBoundary>
            ) : null}

            <ConfirmModal
                isOpen={isWithdrawModalOpen}
                title="회원 탈퇴하시겠습니까?"
                description="탈퇴한 계정은 복구할 수 없습니다. 작성한 글과 댓글은 남습니다."
                confirmText="탈퇴"
                onCancel={() => setIsWithdrawModalOpen(false)}
                onConfirm={handleWithdraw}
            />
            <Toast isVisible={toast.isVisible} message={toast.message} />
        </PageLayout>
    );
}

export default ProfileEditPage;
