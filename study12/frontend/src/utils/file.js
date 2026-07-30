export const MAX_POST_IMAGE_COUNT = 3;
export const MAX_IMAGE_FILE_SIZE = 3 * 1024 * 1024;
export const IMAGE_FILE_ACCEPT = "image/jpeg,image/png,image/webp,image/gif";

const ALLOWED_IMAGE_TYPES = new Set([
    "image/jpeg",
    "image/png",
    "image/webp",
    "image/gif",
]);

export function getImageFileError(file) {
    if (!file) {
        return "";
    }

    if (!ALLOWED_IMAGE_TYPES.has(file.type)) {
        return "JPEG, PNG, WebP, GIF 이미지만 사용할 수 있습니다.";
    }

    if (file.size > MAX_IMAGE_FILE_SIZE) {
        return "이미지 한 개의 크기는 최대 3MB입니다.";
    }

    return "";
}

export function getPostImageFilesError(files) {
    if (files.length > MAX_POST_IMAGE_COUNT) {
        return "게시글 이미지는 최대 3개까지 첨부할 수 있습니다.";
    }

    for (const file of files) {
        const error = getImageFileError(file);
        if (error) {
            return error;
        }
    }

    return "";
}

export function fileToDataUrl(file){
    return new Promise((resolve, reject)=>{
        if(!file){
            reject(new Error("파일이 없습니다."));
            return;
        }
        const reader=new FileReader();
        reader.onload=()=>{
            resolve(reader.result);
        };
        reader.onerror=()=>{
            reject(reader.error);
        };
        reader.readAsDataURL(file);
    });
}
