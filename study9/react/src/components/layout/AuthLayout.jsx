import ErrorBoundary from "../common/error/ErrorBoundary.jsx";
import ErrorView from "../common/ErrorView.jsx";
import AppHeader from "./AppHeader.jsx";
import AuthIntroPanel from "./AuthIntroPanel.jsx";

function AuthLayout({
    pageClassName,
    sectionClassName,
    showBack = false,
    intro,
    children,
}) {
    return (
        <div className={pageClassName}>
            <ErrorBoundary fallback={<ErrorView title="헤더를 표시하지 못했습니다." />}>
                <AppHeader
                    showBack={showBack}
                    backTo="/login"
                    backClassName="back-button"
                />
            </ErrorBoundary>
            <main className={sectionClassName}>
                <ErrorBoundary fallback={<ErrorView title="소개 영역을 표시하지 못했습니다." />}>
                    <AuthIntroPanel {...intro} />
                </ErrorBoundary>
                <ErrorBoundary fallback={<ErrorView title="인증 양식을 표시하지 못했습니다." />}>
                    {children}
                </ErrorBoundary>
            </main>
        </div>
    );
}

export default AuthLayout;
