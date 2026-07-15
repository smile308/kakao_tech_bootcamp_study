import AppHeader from "./AppHeader";
import AuthIntroPanel from "./AuthIntroPanel";

function AuthLayout({
    pageClassName,
    sectionClassName,
    showBack = false,
    intro,
    children,
}) {
    return (
        <div className={pageClassName}>
            <AppHeader
                showBack={showBack}
                backTo="/login"
                />

                <main className={sectionClassName}>
                    <AuthIntroPanel {...intro} />
                    {children}
                </main>
        </div>
    );
}

export default AuthLayout;