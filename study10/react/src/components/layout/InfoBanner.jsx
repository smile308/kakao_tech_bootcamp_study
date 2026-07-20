function InfoBanner({ eyebrow, title, description }) {
    return (
        <aside className="account-side-panel" aria-label="계정 안내">
            <p>{eyebrow}</p>
            <strong>{title}</strong>
            <span>{description}</span>
        </aside>
    );
}

export default InfoBanner;
