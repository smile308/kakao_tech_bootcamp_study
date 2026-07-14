import {useState} from 'react';
import {Link, useNavigate} from 'react-router';

import {api} from '../../api/api';
import {authStorage} from '../../auth/authStorage';
import {isValidEmail, isValidPassword} from '../../utils/validation';

const EMAIL_EMPTY_MESSAGE = "*이메일을 입력해주세요.";
const EMAIL_INVALID_MESSAGE = "올바른 이메일 주소 형식을 입력해주세요. (예: example@example.com)";
const PASSWORD_EMPTY_MESSAGE = "*비밀번호를 입력해주세요.";
const PASSWORD_INVALID_MESSAGE = "비밀번호는 8자 이상, 20자 이하이며, 대문자, 소문자, 숫자, 특수문자를 각각 최소 1개 포함해야 합니다.";
const LOGIN_FAILED_MESSAGE = "이메일 또는 비밀번호를 확인해주세요.";

function getValidationErrors(email, password) {
    if(!email){
        return EMAIL_EMPTY_MESSAGE;
    }

    if(!isValidEmail(email)){
        return EMAIL_INVALID_MESSAGE;
    }

    if(!password){
        return PASSWORD_EMPTY_MESSAGE;
    }

    if(!isValidPassword(password)){
        return PASSWORD_INVALID_MESSAGE;
    }
    
    return null;
}


function LoginPage() {
    const navigate = useNavigate();

    const [form, setForm] = useState({
        email: "",
        password: "",
    });

    const [helpMessage, setHelpMessage] = useState("");

    const [isSubmitting, setIsSubmitting] = useState(false);

    const isFormValid = isValidEmail(form.email) && isValidPassword(form.password);
    
    function handleInputChange(event) {
        const { name, value } = event.target;
        setForm(prevForm => ({
            ...prevForm,
            [name]: value
        }));
    }

    async function handleSubmit(event) {
        event.preventDefault();

        const validationError = getValidationErrors(form.email, form.password);
        if(validationError){
            setHelpMessage(validationError);
            return;
        }

        try{
            setIsSubmitting(true);
            setHelpMessage("");

            const result = await api.login(form);
            authStorage.setAccessToken(result.accessToken);
            navigate("/posts",{replace:true});
        } catch (error) {
            setHelpMessage(LOGIN_FAILED_MESSAGE);
        } finally {
            setIsSubmitting(false);
        }
    }

    return (
        <div className="login-page">
            <h1>로그인</h1>
            </div>
    )
}

export default LoginPage;