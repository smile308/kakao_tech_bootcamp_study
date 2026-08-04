package kr.adapterz.springdatajpa.exception;

public enum ApiErrorCode {
    INVALID_REQUEST("INVALID_REQUEST", "입력한 내용을 확인해주세요."),
    ALREADY_LIKED("ALREADY_LIKED", "이미 좋아요한 게시글입니다."),
    NOT_LIKED("NOT_LIKED", "이미 좋아요가 취소된 게시글입니다."),
    ALREADY_REPORTED("ALREADY_REPORTED", "이미 신고한 게시글입니다."),
    CANNOT_REPORT_OWN_POST("CANNOT_REPORT_OWN_POST", "자신의 게시글은 신고할 수 없습니다."),
    POST_VERSION_CONFLICT("POST_VERSION_CONFLICT", "다른 요청으로 게시글이 변경되었습니다. 최신 내용을 다시 확인해주세요."),
    COUNTER_UPDATE_FAILED("COUNTER_UPDATE_FAILED", "게시글 통계 반영에 실패했습니다. 잠시 후 다시 시도해주세요."),
    EXISTED_EMAIL("EXISTED_EMAIL", "이미 사용 중인 이메일입니다."),
    EXISTED_NICKNAME("EXISTED_NICKNAME", "이미 사용 중인 닉네임입니다."),
    INVALID_PASSWORD("INVALID_PASSWORD", "비밀번호와 비밀번호 확인이 일치하지 않습니다."),
    INVALID_CURRENT_PASSWORD("INVALID_CURRENT_PASSWORD", "현재 비밀번호가 올바르지 않습니다."),
    SUSPENDED_ACCOUNT("SUSPENDED_ACCOUNT", "신고 누적으로 이용이 제한된 계정입니다."),
    LOGIN_FAILED("LOGIN_FAILED", "이메일 또는 비밀번호를 확인해주세요."),
    INVALID_TOKEN("INVALID_TOKEN", "로그인 정보가 유효하지 않습니다. 다시 로그인해주세요."),
    INVALID_REFRESH_TOKEN("INVALID_REFRESH_TOKEN", "로그인 시간이 만료되었습니다. 다시 로그인해주세요."),
    NO_POST("NO_POST", "삭제되었거나 존재하지 않는 게시글입니다."),
    NO_COMMENT("NO_COMMENT", "삭제되었거나 존재하지 않는 댓글입니다."),
    NO_USER("NO_USER", "존재하지 않는 사용자입니다."),
    NO_ACCOUNT("NO_ACCOUNT", "존재하지 않는 계정입니다."),
    FORBIDDEN_ACCESS("FORBIDDEN_ACCESS", "이 작업을 수행할 권한이 없습니다."),
    UNAUTHORIZED("UNAUTHORIZED", "로그인이 필요합니다."),
    FORBIDDEN("FORBIDDEN", "이 작업을 수행할 권한이 없습니다."),
    FORBIDDEN_ORIGIN("FORBIDDEN_ORIGIN", "허용되지 않은 요청입니다."),
    TOO_MANY_IMAGES("TOO_MANY_IMAGES", "게시글 이미지는 최대 3개까지 첨부할 수 있습니다."),
    IMAGE_TOO_LARGE("IMAGE_TOO_LARGE", "이미지 한 개의 크기는 최대 3MB입니다."),
    UNSUPPORTED_IMAGE_TYPE("UNSUPPORTED_IMAGE_TYPE", "JPEG, PNG, WebP, GIF 이미지만 사용할 수 있습니다."),
    INVALID_IMAGE_DATA("INVALID_IMAGE_DATA", "올바른 이미지 파일을 선택해주세요."),
    INVALID_PAGE("INVALID_PAGE", "페이지 번호가 올바르지 않습니다."),
    INVALID_PAGE_SIZE("INVALID_PAGE_SIZE", "페이지 크기가 올바르지 않습니다."),
    UNKNOWN_ERROR("UNKNOWN_ERROR", "요청 처리 중 오류가 발생했습니다.");

    private final String code;
    private final String message;

    ApiErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public static ApiErrorCode from(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            return UNKNOWN_ERROR;
        }

        String normalized = rawCode.trim().toUpperCase().replace('-', '_');
        for (ApiErrorCode errorCode : values()) {
            if (errorCode.code.equals(normalized)) {
                return errorCode;
            }
        }

        return UNKNOWN_ERROR;
    }
}
