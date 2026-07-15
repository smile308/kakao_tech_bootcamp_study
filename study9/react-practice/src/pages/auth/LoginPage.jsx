import AuthIntroPanel from "../../components/layout/AuthIntroPanel.jsx";

const LOGIN_INTRO_ITEMS = [
    {
        title: "이름 없는 걸음",
        description: "누구인지 몰라도 편하게 남기는 하루",
    },
    {
        title: "반딧불 댓글",
        description: "서로의 이야기에 살짝 불을 비춰주는 공감",
    },
];

function LoginPage() {
    return (
        <main>
            <AuthIntroPanel
                eyebrow="Open After Dark"
                title="다들 잠든 시간, 여기선 마음을 꺼내도 괜찮아요"
                items={LOGIN_INTRO_ITEMS}
            />
        </main>
    );
}

export default LoginPage;