import ErrorBoundary from "./ErrorBoundary.jsx";

function AppErrorFallback() {
    return (
        <main className="error-view error-view--app">
            <h1>화면을 표시하지 못했습니다.</h1>
            <p>잠시 후 다시 시도해주세요.</p>
            <button type="button" onClick={() => window.location.reload()}>
                새로고침
            </button>
        </main>
    );
}

function AppErrorBoundary({ children }) {
    return (
        <ErrorBoundary fallback={<AppErrorFallback />}>
            {children}
        </ErrorBoundary>
    );
}

export default AppErrorBoundary;
