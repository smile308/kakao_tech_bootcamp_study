import { IMAGE_FILE_ACCEPT } from "../../utils/file.js";

function PostImagePicker({ mode, existingImageCount = 0, files, onChange }) {
    const names = files?.length
        ? `${files.length}개 선택: ${files.map((file) => file.name).join(", ")}`
        : existingImageCount > 0
          ? `${existingImageCount}개 이미지가 첨부되어 있습니다.`
          : "파일을 선택해주세요.";
    const prefix = mode === "edit" ? "post-image" : "post-create";

    return (
        <div className={`${prefix}-file-area`}>
            <label htmlFor="postImageInput" className={`${prefix}-file-button`}>
                파일 선택
            </label>
            <span className={`${prefix}-file-name`}>{names}</span>
            <input
                type="file"
                id="postImageInput"
                className={mode === "edit" ? "post-image-input" : "post-create-file-input"}
                accept={IMAGE_FILE_ACCEPT}
                multiple
                onChange={onChange}
            />
        </div>
    );
}

export default PostImagePicker;
