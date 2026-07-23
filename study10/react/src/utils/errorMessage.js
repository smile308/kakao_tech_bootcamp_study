const ERROR_CODE_MESSAGES = Object.freeze({
    invalid_request: "입력한 내용을 확인해주세요.",
    Already_Liked: "이미 좋아요한 게시글입니다.",
    Not_Liked: "이미 좋아요가 취소된 게시글입니다.",
    Already_Reported: "이미 신고한 게시글입니다.",
    Cannot_Report_Own_Post: "자신의 게시글은 신고할 수 없습니다.",
    Existed_Email: "이미 사용 중인 이메일입니다.",
    Existed_Nickname: "이미 사용 중인 닉네임입니다.",
    Invalid_Password: "비밀번호와 비밀번호 확인이 일치하지 않습니다.",
    Invalid_Current_Password: "현재 비밀번호가 올바르지 않습니다.",
    Suspended_Account: "신고 누적으로 이용이 제한된 계정입니다.",
    Login_Failed: "이메일 또는 비밀번호를 확인해주세요.",
    Invalid_Token: "로그인 정보가 유효하지 않습니다. 다시 로그인해주세요.",
    Invalid_Refresh_Token: "로그인 시간이 만료되었습니다. 다시 로그인해주세요.",
    No_Post: "삭제되었거나 존재하지 않는 게시글입니다.",
    No_Comment: "삭제되었거나 존재하지 않는 댓글입니다.",
    No_User: "존재하지 않는 사용자입니다.",
    No_Account: "존재하지 않는 계정입니다.",
    Forbidden_Access: "이 작업을 수행할 권한이 없습니다.",
    Unauthorized: "로그인이 필요합니다.",
    Forbidden: "이 작업을 수행할 권한이 없습니다.",
    Forbidden_Origin: "허용되지 않은 요청입니다.",
    Too_Many_Images: "게시글 이미지는 최대 3개까지 첨부할 수 있습니다.",
    Image_Too_Large: "이미지 한 개의 크기는 최대 3MB입니다.",
    Unsupported_Image_Type: "JPEG, PNG, WebP, GIF 이미지만 사용할 수 있습니다.",
    Invalid_Image_Data: "올바른 이미지 파일을 선택해주세요.",
});

const STATUS_MESSAGES = Object.freeze({
    400: "요청 내용을 확인해주세요.",
    401: "로그인이 필요합니다.",
    403: "이 작업을 수행할 권한이 없습니다.",
    404: "요청한 정보를 찾을 수 없습니다.",
    409: "현재 상태에서는 요청을 처리할 수 없습니다.",
    500: "서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요.",
});

export function getErrorCode(error) {
    if (typeof error === "string") {
        return error;
    }

    if (typeof error?.code === "string") {
        return error.code;
    }

    if (typeof error?.data?.message === "string") {
        return error.data.message;
    }

    return typeof error?.message === "string" ? error.message : "";
}

export function hasErrorCode(error, ...codes) {
    return codes.includes(getErrorCode(error));
}

export function getErrorMessage(
    error,
    fallback = "요청 처리에 실패했습니다. 잠시 후 다시 시도해주세요.",
) {
    const code = getErrorCode(error);

    return (
        ERROR_CODE_MESSAGES[code]
        ?? STATUS_MESSAGES[error?.status]
        ?? fallback
    );
}
