import AppLink from "../common/AppLink.jsx";
import ActionButton from "../common/ActionButton.jsx";
import FormField from "../common/FormField.jsx";
import TextInput from "../common/TextInput.jsx";

function LoginForm({
    form,
    errors,
    isSubmitting,
    isValid,
    onChange,
    onSubmit,
    signupTo,
}) {
    return (
        <section className="auth-form-panel">
            <h2 className="login-section__title">로그인</h2>
            <form className="login-form" noValidate onSubmit={onSubmit}>
                <FormField
                    label="이메일"
                    htmlFor="email"
                    className="form-group"
                    error={errors.email}
                >
                    <TextInput
                        type="email"
                        id="email"
                        name="email"
                        value={form.email}
                        placeholder="이메일을 입력하세요"
                        autoComplete="email"
                        onChange={onChange}
                    />
                </FormField>
                <FormField
                    label="비밀번호"
                    htmlFor="password"
                    className="form-group"
                    error={errors.password}
                >
                    <TextInput
                        type="password"
                        id="password"
                        name="password"
                        value={form.password}
                        placeholder="비밀번호를 입력하세요"
                        autoComplete="current-password"
                        onChange={onChange}
                    />
                </FormField>
                <ActionButton
                    type="submit"
                    className="login-button"
                    disabled={!isValid || isSubmitting}
                >
                    {isSubmitting ? "로그인 중" : "로그인"}
                </ActionButton>
            </form>
            <AppLink to={signupTo} className="signup-link">
                회원가입
            </AppLink>
        </section>
    );
}

export default LoginForm;
