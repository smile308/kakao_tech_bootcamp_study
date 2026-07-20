import { useState } from "react";
import { useNavigate } from "react-router-dom";

import { authApi } from "../../api/authApi.js";
import { authStorage } from "../../auth/authStorage.js";
import ConfirmModal from "../../components/common/ConfirmModal.jsx";
import LoginForm from "../../components/auth/LoginForm.jsx";
import AuthLayout from "../../components/layout/AuthLayout.jsx";
import { isValidEmail, isValidPassword } from "../../utils/validation.js";
import "../../styles/auth.css";

const EMAIL_EMPTY_MESSAGE = "*이메일을 입력해주세요.";
const EMAIL_INVALID_MESSAGE = "*올바른 이메일 주소 형식을 입력해주세요.";
const PASSWORD_EMPTY_MESSAGE = "*비밀번호를 입력해주세요.";
const PASSWORD_INVALID_MESSAGE = "*비밀번호 형식을 확인해주세요.";
const LOGIN_FAIL_MESSAGE = "*이메일 또는 비밀번호를 확인해주세요.";
const SUSPENDED_ACCOUNT_MESSAGE = "Suspended_Account";

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
    const [isSuspendedModalOpen, setIsSuspendedModalOpen] = useState(false);
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
        } catch (error) {
            if (error.message === SUSPENDED_ACCOUNT_MESSAGE) {
                setIsSuspendedModalOpen(true);
                return;
            }
            setErrors((previous) => ({ ...previous, password: LOGIN_FAIL_MESSAGE }));
        } finally {
            setIsSubmitting(false);
        }
    }

    return (
        <AuthLayout
            pageClassName="login-page"
            sectionClassName="login-section"
            intro={{
                eyebrow: "Anonymous Bamboo Forest",
                title: "이름을 숨긴 고민에, 이름 있는 위로를 건네는 밤",
                items: [
                    { title: "익명의 고백", description: "게시글에서는 누구인지 드러내지 않고 마음을 털어놓아요" },
                    { title: "책임 있는 위로", description: "댓글은 계정으로 남겨 서로에게 조심스러운 온기를 건네요" },
                ],
            }}
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
            <ConfirmModal
                isOpen={isSuspendedModalOpen}
                title="정지된 계정입니다"
                description="신고가 누적되어 로그인이 제한되었습니다."
                confirmText="확인"
                onConfirm={() => setIsSuspendedModalOpen(false)}
            />
        </AuthLayout>
    );
}

export default LoginPage;
