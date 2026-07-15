import { useState } from "react";
import { useNavigate } from "react-router-dom";

import { authApi } from "../../api/authApi.js";
import { authStorage } from "../../auth/authStorage.js";
import LoginForm from "../../components/auth/LoginForm.jsx";
import AuthLayout from "../../components/layout/AuthLayout.jsx";
import { isValidEmail, isValidPassword } from "../../utils/validation.js";

const LOGIN_INTRO = {
    eyebrow: "Open After Dark",
    title: "다들 잠든 시간, 여기선 마음을 꺼내도 괜찮아요",
    items: [
        {
            title: "이름 없는 걸음",
            description: "누구인지 몰라도 편하게 남기는 하루",
        },
        {
            title: "반딧불 댓글",
            description: "서로의 이야기에 살짝 불을 비춰주는 공감",
        },
    ],
};

const EMAIL_EMPTY_MESSAGE = "*이메일을 입력해주세요.";
const EMAIL_INVALID_MESSAGE = "*올바른 이메일 주소 형식을 입력해주세요.";
const PASSWORD_EMPTY_MESSAGE = "*비밀번호를 입력해주세요.";
const PASSWORD_INVALID_MESSAGE = "*비밀번호 형식을 확인해주세요.";
const LOGIN_FAIL_MESSAGE = "*이메일 또는 비밀번호를 확인해주세요.";

function getFieldError(name, value) {
    if (name === "email") {
        const trimmedEmail = value.trim();

        if (!trimmedEmail) {
            return EMAIL_EMPTY_MESSAGE;
        }
        if (!isValidEmail(trimmedEmail)) {
            return EMAIL_INVALID_MESSAGE;
        }
    }

    if (name === "password") {
        if (!value) {
            return PASSWORD_EMPTY_MESSAGE;
        }
        if (!isValidPassword(value)) {
            return PASSWORD_INVALID_MESSAGE;
        }
    }

    return "";
}

function LoginPage() {
    const navigate = useNavigate();
    const [form, setForm] = useState({ email: "", password: "" });
    const [errors, setErrors] = useState({ email: "", password: "" });
    const [isSubmitting, setIsSubmitting] = useState(false);
    const isValid = isValidEmail(form.email.trim()) && isValidPassword(form.password);

    function handleChange(event) {
        const { name, value } = event.target;
        setForm((previous) => ({ ...previous, [name]: value }));
        setErrors((previous) => ({
            ...previous,
            [name]: getFieldError(name, value),
        }));
    }

    async function handleSubmit(event) {
        event.preventDefault();

        if (!isValid) {
            setErrors({
                email: getFieldError("email", form.email),
                password: getFieldError("password", form.password),
            });
            return;
        }

        try {
            setIsSubmitting(true);
            setErrors({ email: "", password: "" });
            const result = await authApi.login({
                email: form.email.trim(),
                password: form.password,
            });
            authStorage.setAccessToken(result.accessToken);
            navigate("/posts", { replace: true });
        } catch {
            setErrors((previous) => ({ ...previous, password: LOGIN_FAIL_MESSAGE }));
        } finally {
            setIsSubmitting(false);
        }
    }

    return (
        <AuthLayout
            pageClassName="login-page"
            sectionClassName="login-section"
            intro={LOGIN_INTRO}
        >
            <LoginForm
                form={form}
                errors={errors}
                isSubmitting={isSubmitting}
                isValid={isValid}
                onChange={handleChange}
                onSubmit={handleSubmit}
                signupTo="/signup"
            />
        </AuthLayout>
    );
}

export default LoginPage;
