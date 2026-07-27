import { useEffect } from "react";

function ConfirmModal({
    isOpen,
    title,
    description,
    confirmText = "확인",
    cancelText,
    onConfirm,
    onCancel,
}) {
    useEffect(() => {
        document.body.classList.toggle("modal-open", isOpen);
        return () => document.body.classList.remove("modal-open");
    }, [isOpen]);

    if (!isOpen) {
        return null;
    }

    return (
        <div className="modal-overlay" aria-hidden="false">
            <section className="confirm-modal" role="dialog" aria-modal="true">
                <h2 className="confirm-modal__title">{title}</h2>
                <p className="confirm-modal__description">{description}</p>
                <div className="confirm-modal__actions">
                    {onCancel ? (
                        <button
                            type="button"
                            className="confirm-modal__button confirm-modal__button--cancel"
                            onClick={onCancel}
                        >
                            {cancelText ?? "취소"}
                        </button>
                    ) : null}
                    <button
                        type="button"
                        className="confirm-modal__button confirm-modal__button--confirm"
                        onClick={onConfirm}
                    >
                        {confirmText}
                    </button>
                </div>
            </section>
        </div>
    );
}

export default ConfirmModal;
