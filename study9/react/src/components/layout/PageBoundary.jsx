import { Suspense } from "react";
import { useLocation, useNavigate } from "react-router-dom";

import ErrorView from "../common/ErrorView.jsx";
import LoadingView from "../common/LoadingView.jsx";
import ErrorBoundary from "../common/error/ErrorBoundary.jsx";

function PageBoundary({ children }) {
    const location = useLocation();
    const navigate = useNavigate();

    return (
        <ErrorBoundary
            key={location.key}
            fallback={({ reset }) => (
                <ErrorView
                    message="페이지를 다시 열거나 게시글 목록으로 이동해주세요."
                    onRetry={() => {
                        reset();
                        navigate(0);
                    }}
                />
            )}
        >
            <Suspense fallback={<LoadingView />}>
                {children}
            </Suspense>
        </ErrorBoundary>
    );
}

export default PageBoundary;
