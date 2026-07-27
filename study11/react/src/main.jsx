import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { ErrorBoundary } from "react-error-boundary";
import { BrowserRouter } from "react-router-dom";
import App from "./App.jsx";

import "./styles/common.css";

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <ErrorBoundary
      fallback={(
        <main className="error-view error-view--app">
          <h1>화면을 표시하지 못했습니다.</h1>
          <p>잠시 후 다시 시도해주세요.</p>
          <button type="button" onClick={() => window.location.reload()}>
            새로고침
          </button>
        </main>
      )}
    >
      <BrowserRouter>
        <App />
      </BrowserRouter>
    </ErrorBoundary>
  </StrictMode>,
);
