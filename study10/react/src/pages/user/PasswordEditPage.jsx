import { useState } from "react";
import { useNavigate } from "react-router-dom";

import { userApi } from "../../api/userApi.js";
import { authStorage } from "../../auth/authStorage.js";
import { ErrorBoundary } from "react-error-boundary";
import InfoBanner from "../../components/layout/InfoBanner.jsx";
import PageLayout from "../../components/layout/PageLayout.jsx";
import PasswordEditForm from "../../components/user/PasswordEditForm.jsx";
import {
    getErrorMessage,
    hasErrorCode,
} from "../../utils/errorMessage.js";
import { isValidPassword } from "../../utils/validation.js";
import "../../styles/user.css";

const EMPTY_FORM = {
    currentPassword: "",
    password: "",
    passwordCheck: "",
};

const EMPTY_ERRORS = {
    currentPassword: "",
    password: "",
    passwordCheck: "",
};

function PasswordEditPage() {
    const navigate = useNavigate();
    const [form, setForm] = useState(EMPTY_FORM);
    const [errors, setErrors] = useState(EMPTY_ERRORS);
    const [isSubmitting, setIsSubmitting] = useState(false);
    const isFormValid =
        Boolean(form.currentPassword) &&
        isValidPassword(form.password) &&
        form.password === form.passwordCheck;

    function handleChange(event) {
        const { name, value } = event.target;
        setForm((previous) => ({ ...previous, [name]: value }));
        setErrors((previous) => ({ ...previous, [name]: "" }));
    }

    function validate() {
        const nextErrors = { ...EMPTY_ERRORS };
        if (!form.currentPassword) {
            nextErrors.currentPassword = "현재 비밀번호를 입력해주세요.";
        }
        if (!isValidPassword(form.password)) {
            nextErrors.password = "8~20자의 영문 대·소문자, 숫자, 특수문자를 사용해주세요.";
        }
        if (form.password !== form.passwordCheck) {
            nextErrors.passwordCheck = "새 비밀번호가 일치하지 않습니다.";
        }
        setErrors(nextErrors);
        return !Object.values(nextErrors).some(Boolean);
    }

    async function handleSubmit(event) {
        event.preventDefault();
        if (!validate()) {
            return;
        }

        try {
            setIsSubmitting(true);
            await userApi.updatePassword(form);
            authStorage.removeAccessToken();
            navigate("/login", { replace: true });
        } catch (error) {
            if (hasErrorCode(error, "Invalid_Current_Password")) {
                setErrors((previous) => ({
                    ...previous,
                    currentPassword: getErrorMessage(error),
                }));
            } else {
                setErrors((previous) => ({
                    ...previous,
                    passwordCheck: getErrorMessage(error, "비밀번호 수정에 실패했습니다."),
                }));
            }
        } finally {
            setIsSubmitting(false);
        }
    }

    return (
        <PageLayout
            pageClassName="password-edit-page"
            mainClassName="password-edit-section"
            title="계정 보안"
            showBack
            backTo="/posts"
        >
            <h2 className="password-edit-title">비밀번호 수정</h2>
            <InfoBanner
                eyebrow="Security"
                title="대나무숲 계정을 안전하게 보호하세요"
                description="글과 댓글을 편안하게 관리할 수 있도록 비밀번호를 새롭게 설정하세요."
            />
            <ErrorBoundary fallback={<p>비밀번호 수정 양식을 표시하지 못했습니다.</p>}>
                <PasswordEditForm
                    form={form}
                    errors={errors}
                    isValid={isFormValid}
                    isSubmitting={isSubmitting}
                    onChange={handleChange}
                    onSubmit={handleSubmit}
                />
            </ErrorBoundary>
        </PageLayout>
    );
}

export default PasswordEditPage;
