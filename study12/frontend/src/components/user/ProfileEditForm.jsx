import ActionButton from "../common/ActionButton.jsx";
import ProfileImagePicker from "./ProfileImagePicker.jsx";

function ProfileEditForm({
    email,
    nickname,
    previewUrl,
    nicknameError,
    isSubmitting,
    onNicknameChange,
    onImageChange,
    onSubmit,
    onWithdraw,
}) {
    return (
        <form className="profile-edit-form" noValidate onSubmit={onSubmit}>
            <ProfileImagePicker previewUrl={previewUrl} onChange={onImageChange} />

            <div className="profile-form-area">
                <div className="profile-form-group">
                    <label className="profile-form-label" htmlFor="profileEmail">
                        이메일
                    </label>
                    <p id="profileEmail" className="profile-email-text">
                        {email}
                    </p>
                </div>

                <div className="profile-form-group">
                    <label className="profile-form-label" htmlFor="profileNickname">
                        닉네임
                    </label>
                    <input
                        id="profileNickname"
                        className="profile-form-input"
                        type="text"
                        value={nickname}
                        maxLength={10}
                        placeholder="닉네임을 입력하세요"
                        onChange={onNicknameChange}
                    />
                    <p className="profile-helper-text">{nicknameError}</p>
                </div>

                <ActionButton
                    className="profile-submit-button"
                    type="submit"
                    disabled={isSubmitting}
                >
                    {isSubmitting ? "수정 중..." : "수정하기"}
                </ActionButton>
                <ActionButton className="withdraw-button" onClick={onWithdraw}>
                    회원 탈퇴
                </ActionButton>
            </div>
        </form>
    );
}

export default ProfileEditForm;
