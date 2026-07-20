import ActionButton from "../common/ActionButton.jsx";

const fields = [
    {
        name: "currentPassword",
        label: "현재 비밀번호",
        placeholder: "현재 비밀번호를 입력하세요",
    },
    {
        name: "password",
        label: "새 비밀번호",
        placeholder: "새 비밀번호를 입력하세요",
    },
    {
        name: "passwordCheck",
        label: "새 비밀번호 확인",
        placeholder: "새 비밀번호를 한번 더 입력하세요",
    },
];

function PasswordEditForm({
    form,
    errors,
    isValid,
    isSubmitting,
    onChange,
    onSubmit,
}) {
    return (
        <form className="password-edit-form" noValidate onSubmit={onSubmit}>
            {fields.map((field) => (
                <div className="password-form-group" key={field.name}>
                    <label className="password-label" htmlFor={field.name}>
                        {field.label}
                    </label>
                    <input
                        id={field.name}
                        name={field.name}
                        className="password-input"
                        type="password"
                        value={form[field.name]}
                        placeholder={field.placeholder}
                        autoComplete={
                            field.name === "currentPassword"
                                ? "current-password"
                                : "new-password"
                        }
                        onChange={onChange}
                    />
                    <p className="password-helper-text">{errors[field.name]}</p>
                </div>
            ))}

            <ActionButton
                className="password-edit-button"
                type="submit"
                disabled={!isValid || isSubmitting}
            >
                {isSubmitting ? "수정 중..." : "수정하기"}
            </ActionButton>
        </form>
    );
}

export default PasswordEditForm;
