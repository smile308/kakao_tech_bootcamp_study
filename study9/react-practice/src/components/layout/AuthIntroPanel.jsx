import LogoMark from "../common/LogoMark.jsx";

function AuthIntroPanel({
    eyebrow,
    title,
    description,
    items = [],
    className = "",
}) {
    return (
        <section className={`auth-copy-panel ${className}`.trim()}>
            <LogoMark
                size="large"
                className="auth-copy-panel__logo"
            />
            <p className="auth-copy-panel__eyebrow">{eyebrow}</p>
            <h2 className="auth-copy-panel__title">{title}</h2>
            {description && (
                <p className="auth-copy-panel__description">{description}</p>
            )}
            {items.length > 0 && (
                <dl className="auth-copy-panel__items">
                    {items.map((item) => (
                        <div key={item.title} className="auth-copy-panel__item">
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
