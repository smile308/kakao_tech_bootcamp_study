import AppLink from "../common/AppLink.jsx";
import FormField from "../common/FormField.jsx";
import TextInput from "../common/TextInput.jsx";
import ProfileImagePicker from "../user/ProfileImagePicker.jsx";

function SignupForm({
    form,
    errors,
    previewUrl,
    isSubmitting,
    isValid,
    onChange,
    onBlur,
    onImageChange,
    onSubmit,
    loginTo,
}) {
    return (
        <section className="auth-form-panel signup-form-panel">
            <h2 className="signup-section__title">회원가입</h2>
            <form className="signup-form" noValidate onSubmit={onSubmit}>
                <ProfileImagePicker
                    previewUrl={previewUrl}
                    onChange={onImageChange}
                    variant="signup"
                />

                <section className="signup-input-area">
                    <FormField
                        label="이메일*"
                        htmlFor="signupEmail"
                        className="signup-form-group"
                        error={errors.email}
                    >
                        <TextInput
                            type="email"
                            id="signupEmail"
                            name="email"
                            value={form.email}
                            placeholder="이메일을 입력하세요"
                            autoComplete="email"
                            onChange={onChange}
                            onBlur={onBlur}
                        />
                    </FormField>
                    <FormField
                        label="비밀번호*"
                        htmlFor="signupPassword"
                        className="signup-form-group"
                        error={errors.password}
                    >
                        <TextInput
                            type="password"
                            id="signupPassword"
                            name="password"
                            value={form.password}
                            placeholder="비밀번호를 입력하세요"
                            autoComplete="new-password"
                            onChange={onChange}
                            onBlur={onBlur}
                        />
                    </FormField>
                    <FormField
                        label="비밀번호 확인*"
                        htmlFor="signupPasswordConfirm"
                        className="signup-form-group"
                        error={errors.passwordCheck}
                    >
                        <TextInput
                            type="password"
                            id="signupPasswordConfirm"
                            name="passwordCheck"
                            value={form.passwordCheck}
                            placeholder="비밀번호를 한번 더 입력하세요"
                            autoComplete="new-password"
                            onChange={onChange}
                            onBlur={onBlur}
                        />
                    </FormField>
                    <FormField
                        label="닉네임*"
                        htmlFor="nickname"
                        className="signup-form-group"
                        error={errors.nickname}
                    >
                        <TextInput
                            type="text"
                            id="nickname"
                            name="nickname"
                            value={form.nickname}
                            placeholder="닉네임을 입력하세요"
                            autoComplete="nickname"
                            onChange={onChange}
                            onBlur={onBlur}
                        />
                    </FormField>
                    <button
                        type="submit"
                        className="signup-button"
                        disabled={!isValid || isSubmitting}
                    >
                        {isSubmitting ? "가입 중" : "회원가입"}
                    </button>
                </section>
            </form>
            <AppLink to={loginTo} className="go-login-link">
                로그인하러 가기
            </AppLink>
        </section>
    );
}

export default SignupForm;
