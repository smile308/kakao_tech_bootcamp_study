function ErrorView({
    title = "화면을 표시하지 못했습니다.",
    message = "잠시 후 다시 시도해주세요.",
    onRetry,
}) {
    return (
        <section className="error-view" role="alert">
            <h2>{title}</h2>
            <p>{message}</p>
            {onRetry && (
                <button type="button" onClick={onRetry}>
                    다시 시도
                </button>
            )}
        </section>
    );
}

export default ErrorView;
