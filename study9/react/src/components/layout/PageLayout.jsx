import ErrorBoundary from "../common/error/ErrorBoundary.jsx";
import ErrorView from "../common/ErrorView.jsx";
import AppHeader from "./AppHeader.jsx";

function PageLayout({
    pageClassName,
    mainClassName,
    title,
    showBack = false,
    backTo = "/posts",
    profileImage,
    children,
}) {
    return (
        <div className={pageClassName}>
            <ErrorBoundary fallback={<ErrorView title="헤더를 표시하지 못했습니다." />}>
                <AppHeader
                    title={title}
                    showBack={showBack}
                    backTo={backTo}
                    showProfile
                    profileImage={profileImage}
                />
            </ErrorBoundary>
            <main className={mainClassName}>{children}</main>
        </div>
    );
}

export default PageLayout;
