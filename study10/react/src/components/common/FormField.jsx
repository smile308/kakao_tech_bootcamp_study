function FormField({ label, htmlFor, className = "", error, children }) {
    return (
        <div className={className}>
            <label htmlFor={htmlFor} className="form-label">
                {label}
            </label>
            {children}
            <p className="form-field__error" aria-live="polite">
                {error}
            </p>
        </div>
    );
}

export default FormField;
