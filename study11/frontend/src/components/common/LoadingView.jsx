function LoadingView({ message = "화면을 불러오는 중입니다." }) {
    return (
        <div className="loading-view" role="status" aria-live="polite">
            {message}
        </div>
    );
}

export default LoadingView;
