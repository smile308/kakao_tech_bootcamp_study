function ProfileImagePicker({ previewUrl, onChange, variant = "profile" }) {
    if (variant === "signup") {
        return (
            <section className="profile-upload-area">
                <label className="profile-label">프로필 사진*</label>
                <label
                    htmlFor="profileImage"
                    className={`profile-image-circle ${previewUrl ? "has-image" : ""}`.trim()}
                >
                    {previewUrl && (
                        <img
                            src={previewUrl}
                            className="profile-preview-image"
                            alt="프로필 미리보기"
                        />
                    )}
                    <span className="profile-plus-icon" aria-hidden="true" />
                </label>
                <input
                    type="file"
                    id="profileImage"
                    className="profile-image-input"
                    accept="image/*"
                    onChange={onChange}
                />
            </section>
        );
    }

    return (
        <section className="profile-image-area">
            <label className="profile-image-label">프로필 사진*</label>
            <label htmlFor="profileImageInput" className="profile-image-box">
                {previewUrl && (
                    <img
                        src={previewUrl}
                        alt="회원 이미지"
                        className="profile-edit-image"
                    />
                )}
                <span className="profile-image-change-button">
                    <span className="profile-image-change-text">변경</span>
                </span>
            </label>
            <input
                type="file"
                id="profileImageInput"
                className="profile-image-input"
                accept="image/*"
                onChange={onChange}
            />
        </section>
    );
}

export default ProfileImagePicker;
