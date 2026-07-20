export function formatCount(count) {
    const number = Number(count) || 0;
    return number >= 1000 ? `${Math.floor(number / 1000)}k` : String(number);
}

export function formatDateTime(value) {
    if (!value) {
        return "";
    }

    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
        return "";
    }

    const parts = [
        date.getFullYear(),
        String(date.getMonth() + 1).padStart(2, "0"),
        String(date.getDate()).padStart(2, "0"),
    ];
    const time = [
        String(date.getHours()).padStart(2, "0"),
        String(date.getMinutes()).padStart(2, "0"),
        String(date.getSeconds()).padStart(2, "0"),
    ];

    return `${parts.join("-")} ${time.join(":")}`;
}
