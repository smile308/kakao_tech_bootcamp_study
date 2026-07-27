function Toast({ isVisible, message }) {
    if (!isVisible) {
        return null;
    }

    return (
        <div className="toast-message" role="status" aria-live="polite">
            {message}
        </div>
    );
}

export default Toast;
