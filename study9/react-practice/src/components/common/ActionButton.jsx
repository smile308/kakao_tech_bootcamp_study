function ActionButton({ className = "", type = "button", children, ...props }) {
    return (
        <button type={type} className={className} {...props}>
            {children}
        </button>
    );
}

export default ActionButton;
