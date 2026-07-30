import { Link } from "react-router-dom";

function AppLink({ to, className = "", children, ...props }) {
    return (
        <Link to={to} className={className} {...props}>
            {children}
        </Link>
    );
}

export default AppLink;
