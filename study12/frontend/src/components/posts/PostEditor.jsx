import { useEffect, useState } from "react";

import TextArea from "../common/TextArea.jsx";
import { getErrorMessage } from "../../utils/errorMessage.js";
import {
    fileToDataUrl,
    getPostImageFilesError,
} from "../../utils/file.js";
import PostImagePicker from "./PostImagePicker.jsx";

function PostEditor({ mode, initialValues, onSubmit }) {
    const [form, setForm] = useState({ title: "", content: "" });
    const [files, setFiles] = useState([]);
    const [error, setError] = useState("");
    const [isSubmitting, setIsSubmitting] = useState(false);

    useEffect(() => {
        setForm({
            title: initialValues?.title ?? "",
            content: initialValues?.content ?? "",
        });
    }, [initialValues]);

    function handleFilesChange(event) {
        const nextFiles = Array.from(event.target.files ?? []);
        const imageError = getPostImageFilesError(nextFiles);

        if (imageError) {
            event.target.value = "";
            setFiles([]);
            setError(`*${imageError}`);
            return;
        }

        setFiles(nextFiles);
        setError("");
    }

    async function handleSubmit(event) {
        event.preventDefault();
        const title = form.title.trim();
        const content = form.content.trim();
        const imageError = getPostImageFilesError(files);

        if (!title || !content) {
            setError("*글 제목과 이야기 내용을 모두 작성해주세요.");
            return;
        }

        if (imageError) {
            setError(`*${imageError}`);
            return;
        }

        try {
            setIsSubmitting(true);
            setError("");
            const imageFiles = files.length
                ? await Promise.all(files.map(fileToDataUrl))
                : initialValues?.imageUrls ?? [];
            await onSubmit({
                title,
                content,
                imageFiles,
            });
        } catch (submitError) {
            setError(`*${getErrorMessage(submitError, "요청 처리에 실패했습니다.")}`);
        } finally {
            setIsSubmitting(false);
        }
    }

    const isEdit = mode === "edit";
    const classPrefix = isEdit ? "post-edit" : "post-create";

    return (
        <form className={`${classPrefix}-form`} noValidate onSubmit={handleSubmit}>
            <label htmlFor="postTitleInput" className={`${classPrefix}-label ${classPrefix}-label--title`}>
                글 제목*
            </label>
            <input
                type="text"
                id="postTitleInput"
                className={isEdit ? "post-title-input" : "post-create-title-input"}
                value={form.title}
                maxLength="26"
                placeholder="글 제목을 입력해주세요. (최대 26글자)"
                onChange={(event) => setForm((previous) => ({ ...previous, title: event.target.value }))}
            />
            <label htmlFor="postContentInput" className={`${classPrefix}-label ${classPrefix}-label--content`}>
                이야기 내용*
            </label>
            <TextArea
                id="postContentInput"
                className={isEdit ? "post-content-input" : "post-create-content-input"}
                value={form.content}
                placeholder="오늘 대나무숲에 남기고 싶은 이야기를 입력해주세요."
                onChange={(event) => setForm((previous) => ({ ...previous, content: event.target.value }))}
            />
            <p className={`${classPrefix}-helper`}>{error}</p>
            <label className={`${classPrefix}-label ${classPrefix}-label--image`}>
                첨부 이미지
            </label>
            <PostImagePicker
                mode={mode}
                existingImageCount={initialValues?.imageUrls?.length ?? 0}
                files={files}
                onChange={handleFilesChange}
            />
            <button
                type="submit"
                className={`${classPrefix}-submit-button`}
                disabled={!form.title.trim() || !form.content.trim() || isSubmitting}
            >
                {isSubmitting ? "처리 중" : isEdit ? "글 수정" : "완료"}
            </button>
        </form>
    );
}

export default PostEditor;
