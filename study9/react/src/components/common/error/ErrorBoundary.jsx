import { Component } from "react";

class ErrorBoundary extends Component {
    constructor(props) {
        super(props);
        this.state = { hasError: false };
    }

    static getDerivedStateFromError() {
        return { hasError: true };
    }

    componentDidCatch(error, errorInfo) {
        console.error("React rendering error", error, errorInfo);
    }

    reset = () => {
        this.setState({ hasError: false });
    };

    render() {
        if (!this.state.hasError) {
            return this.props.children;
        }

        if (typeof this.props.fallback === "function") {
            return this.props.fallback({ reset: this.reset });
        }

        return this.props.fallback ?? null;
    }
}

export default ErrorBoundary;
