function PostImageGallery({ imageUrls }) {
    if (!imageUrls?.length) {
        return null;
    }

    return (
        <section className="detail-post-image-box">
            {imageUrls.map((imageUrl, index) => (
                <img
                    key={`${imageUrl.slice(0, 40)}-${index}`}
                    src={imageUrl}
                    className="detail-post-image"
                    alt={`글 첨부 이미지 ${index + 1}`}
                />
            ))}
        </section>
    );
}

export default PostImageGallery;
