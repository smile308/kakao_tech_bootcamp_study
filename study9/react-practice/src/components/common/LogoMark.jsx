import bambooLogo from "../../assets/images/bamboo-logo.svg";

function LogoMark({
    size = "large",
    className = "",
}) {
    return (
        <img
            src={bambooLogo}
            alt=""
            className={`logo-mark logo-mark--${size} ${className}`.trim()}
        />
    );
}

export default LogoMark;
