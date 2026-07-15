import { useState } from "react";
import { useNavigate } from "react-router-dom";

import { authApi } from "../../api/authApi.js";
import { authStorage } from "../../auth/authStorage.js";
import LoginForm from "../../components/auth/LoginForm.jsx";
import AuthLayout from "../../components/layout/AuthLayout.jsx";
import { isValidEmail, isValidPassword } from "../../utils/validation.js";
import "../../styles/auth.css";

function LoginPage() {
    const navigate = useNavigate();
    const [form, setForm] = useState({ email: "", password: "" });
    const [helpMessage, setHelpMessage] = useState("");
    const [isSubmitting, setIsSubmitting] = useState(false);
    const isValid = isValidEmail(form.email) && isValidPassword(form.password);

    function handleChange(event) {
        const { name, value } = event.target;
        setForm((previous) => ({ ...previous, [name]: value }));
    }

    async function handleSubmit(event) {
        event.preventDefault();

        if (!form.email) {
            setHelpMessage("*이메일을 입력해주세요.");
            return;
        }
        if (!isValidEmail(form.email)) {
            setHelpMessage("올바른 이메일 주소 형식을 입력해주세요.");
            return;
        }
        if (!form.password || !isValidPassword(form.password)) {
            setHelpMessage("비밀번호 형식을 확인해주세요.");
            return;
        }

        try {
            setIsSubmitting(true);
            setHelpMessage("");
            const result = await authApi.login(form);
            authStorage.setAccessToken(result.accessToken);
            navigate("/posts", { replace: true });
        } catch {
            setHelpMessage("이메일 또는 비밀번호를 확인해주세요.");
        } finally {
            setIsSubmitting(false);
        }
    }

    return (
        <AuthLayout
            pageClassName="login-page"
            sectionClassName="login-section"
            intro={{
                eyebrow: "Open After Dark",
                title: "다들 잠든 시간, 여기선 마음을 꺼내도 괜찮아요",
                items: [
                    { title: "이름 없는 걸음", description: "누구인지 몰라도 편하게 남기는 하루" },
                    { title: "반딧불 댓글", description: "서로의 이야기에 살짝 불을 비춰주는 공감" },
                ],
            }}
        >
            <LoginForm
                form={form}
                helpMessage={helpMessage}
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
