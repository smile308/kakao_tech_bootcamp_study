function Avatar({ src, alt = "프로필 이미지", className = "" }) {
    return src ? <img src={src} alt={alt} className={className} /> : null;
}

export default Avatar;
