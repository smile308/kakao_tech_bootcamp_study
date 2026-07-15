function FormField({ label, htmlFor, className = "", error, children }) {
    return (
        <div className={className}>
            <label htmlFor={htmlFor} className="form-label">
                {label}
            </label>
            {children}
            {error && <p className="signup-helper-text">{error}</p>}
        </div>
    );
}

export default FormField;
