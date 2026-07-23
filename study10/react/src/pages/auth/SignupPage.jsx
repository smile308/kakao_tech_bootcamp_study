import { useState } from "react";
import { useNavigate } from "react-router-dom";

import { authApi } from "../../api/authApi.js";
import SignupForm from "../../components/auth/SignupForm.jsx";
import AuthLayout from "../../components/layout/AuthLayout.jsx";
import {
    getErrorMessage,
    hasErrorCode,
} from "../../utils/errorMessage.js";
import { fileToDataUrl } from "../../utils/file.js";
import {
    isValidEmail,
    isValidNickname,
    isValidPassword,
} from "../../utils/validation.js";
import "../../styles/auth.css";

const initialForm = {
    email: "",
    password: "",
    passwordCheck: "",
    nickname: "",
};

function SignupPage() {
    const navigate = useNavigate();
    const [form, setForm] = useState(initialForm);
    const [errors, setErrors] = useState({});
    const [profileImage, setProfileImage] = useState(null);
    const [previewUrl, setPreviewUrl] = useState(null);
    const [isSubmitting, setIsSubmitting] = useState(false);

    const isValid =
        isValidEmail(form.email) &&
        isValidPassword(form.password) &&
        form.password === form.passwordCheck &&
        isValidNickname(form.nickname);

    function validateField(name, value = form[name]) {
        if (name === "email") {
            return isValidEmail(value) ? "" : "*올바른 이메일 주소를 입력해주세요.";
        }
        if (name === "password") {
            return isValidPassword(value) ? "" : "*비밀번호 형식을 확인해주세요.";
        }
        if (name === "passwordCheck") {
            return value && value === form.password ? "" : "*비밀번호가 다릅니다.";
        }
        if (name === "nickname") {
            return isValidNickname(value) ? "" : "*닉네임은 공백 없이 10자 이하로 입력해주세요.";
        }
        return "";
    }

    function handleChange(event) {
        const { name, value } = event.target;
        setForm((previous) => ({ ...previous, [name]: value }));
        setErrors((previous) => ({ ...previous, [name]: "" }));
    }

    function handleBlur(event) {
        const { name, value } = event.target;
        setErrors((previous) => ({
            ...previous,
            [name]: validateField(name, value),
        }));
    }

    async function handleImageChange(event) {
        const file = event.target.files?.[0] ?? null;
        setProfileImage(file);
        setPreviewUrl(file ? await fileToDataUrl(file) : null);
    }

    async function handleSubmit(event) {
        event.preventDefault();
        const nextErrors = Object.fromEntries(
            Object.keys(form).map((name) => [name, validateField(name)]),
        );
        setErrors(nextErrors);

        if (!isValid) {
            return;
        }

        try {
            setIsSubmitting(true);
            await authApi.signup({
                ...form,
                email: form.email.trim(),
                nickname: form.nickname.trim(),
                profileImage: profileImage ? await fileToDataUrl(profileImage) : null,
            });
            navigate("/login", { replace: true });
        } catch (error) {
            if (hasErrorCode(error, "Suspended_Account")) {
                setErrors((previous) => ({
                    ...previous,
                    email: `*${getErrorMessage(error)}`,
                }));
            } else if (hasErrorCode(error, "Existed_Email")) {
                setErrors((previous) => ({
                    ...previous,
                    email: `*${getErrorMessage(error)}`,
                }));
            } else if (hasErrorCode(error, "Existed_Nickname")) {
                setErrors((previous) => ({
                    ...previous,
                    nickname: `*${getErrorMessage(error)}`,
                }));
            } else {
                setErrors((previous) => ({
                    ...previous,
                    email: `*${getErrorMessage(error, "회원가입에 실패했습니다.")}`,
                }));
            }
        } finally {
            setIsSubmitting(false);
        }
    }

    return (
        <AuthLayout
            pageClassName="signup-page"
            sectionClassName="signup-section"
            showBack
            intro={{
                eyebrow: "Night Pass",
                title: "이 숲에 처음 들어오는 밤, 작은 통행증을 만들어요",
                description: "이메일과 비밀번호, 닉네임과 사진 한 장이면 충분해요. 얼굴은 몰라도 마음은 편하게 나눠요.",
                className: "signup-copy-panel",
            }}
        >
            <SignupForm
                form={form}
                errors={errors}
                previewUrl={previewUrl}
                isSubmitting={isSubmitting}
                isValid={isValid}
                onChange={handleChange}
                onBlur={handleBlur}
                onImageChange={handleImageChange}
                onSubmit={handleSubmit}
                loginTo="/login"
            />
        </AuthLayout>
    );
}

export default SignupPage;
