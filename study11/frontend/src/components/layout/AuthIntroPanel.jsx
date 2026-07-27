function AuthIntroPanel({ eyebrow, title, description, items = [], className = "" }) {
    return (
        <section
            className={`auth-copy-panel ${className}`.trim()}
            aria-label="서비스 소개"
        >
            <p>{eyebrow}</p>
            <h2>{title}</h2>
            {description && <span>{description}</span>}
            {items.length > 0 && (
                <dl>
                    {items.map((item) => (
                        <div key={item.title}>
                            <dt>{item.title}</dt>
                            <dd>{item.description}</dd>
                        </div>
                    ))}
                </dl>
            )}
        </section>
    );
}

export default AuthIntroPanel;
