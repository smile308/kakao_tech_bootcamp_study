package kr.adapterz.springdatajpa.validation;

import kr.adapterz.springdatajpa.exception.InvalidRequestException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Set;

public final class ImageDataUrlValidator {

    public static final int MAX_POST_IMAGE_COUNT = 3;
    public static final int MAX_IMAGE_BYTES = 3 * 1024 * 1024;

    private static final int MAX_BASE64_LENGTH =
            ((MAX_IMAGE_BYTES + 2) / 3) * 4;

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/gif"
    );

    private ImageDataUrlValidator() {
    }

    public static void validatePostImages(List<String> imageFiles) {
        if (imageFiles == null) {
            return;
        }

        if (imageFiles.size() > MAX_POST_IMAGE_COUNT) {
            throw new InvalidRequestException("Too_Many_Images");
        }

        for (String imageFile : imageFiles) {
            validateImage(imageFile);
        }
    }

    public static void validateProfileImage(String profileImage) {
        if (profileImage == null) {
            return;
        }

        validateImage(profileImage);
    }

    private static void validateImage(String dataUrl) {
        if (dataUrl == null || dataUrl.isBlank()) {
            throw new InvalidRequestException("Invalid_Image_Data");
        }

        int separatorIndex = dataUrl.indexOf(',');
        if (separatorIndex <= 5) {
            throw new InvalidRequestException("Invalid_Image_Data");
        }

        String header = dataUrl.substring(0, separatorIndex);
        String mimeType = extractMimeType(header);
        String encodedData = dataUrl.substring(separatorIndex + 1);

        if (!ALLOWED_IMAGE_TYPES.contains(mimeType)) {
            throw new InvalidRequestException("Unsupported_Image_Type");
        }

        if (encodedData.length() > MAX_BASE64_LENGTH) {
            throw new InvalidRequestException("Image_Too_Large");
        }

        byte[] imageBytes;
        try {
            imageBytes = Base64.getDecoder().decode(encodedData);
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestException("Invalid_Image_Data");
        }

        if (imageBytes.length > MAX_IMAGE_BYTES) {
            throw new InvalidRequestException("Image_Too_Large");
        }

        if (!hasMatchingSignature(mimeType, imageBytes)) {
            throw new InvalidRequestException("Invalid_Image_Data");
        }
    }

    private static String extractMimeType(String header) {
        if (!header.startsWith("data:") || !header.endsWith(";base64")) {
            throw new InvalidRequestException("Invalid_Image_Data");
        }

        return header.substring("data:".length(), header.length() - ";base64".length());
    }

    private static boolean hasMatchingSignature(String mimeType, byte[] bytes) {
        return switch (mimeType) {
            case "image/jpeg" -> startsWith(bytes, new int[]{0xFF, 0xD8, 0xFF});
            case "image/png" -> startsWith(
                    bytes,
                    new int[]{0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}
            );
            case "image/gif" -> startsWithText(bytes, "GIF87a")
                    || startsWithText(bytes, "GIF89a");
            case "image/webp" -> startsWithText(bytes, "RIFF")
                    && hasTextAt(bytes, "WEBP", 8);
            default -> false;
        };
    }

    private static boolean startsWith(byte[] bytes, int[] signature) {
        if (bytes.length < signature.length) {
            return false;
        }

        for (int index = 0; index < signature.length; index++) {
            if ((bytes[index] & 0xFF) != signature[index]) {
                return false;
            }
        }

        return true;
    }

    private static boolean startsWithText(byte[] bytes, String text) {
        return hasTextAt(bytes, text, 0);
    }

    private static boolean hasTextAt(byte[] bytes, String text, int offset) {
        byte[] expected = text.getBytes(StandardCharsets.US_ASCII);
        if (bytes.length < offset + expected.length) {
            return false;
        }

        for (int index = 0; index < expected.length; index++) {
            if (bytes[offset + index] != expected[index]) {
                return false;
            }
        }

        return true;
    }
}
