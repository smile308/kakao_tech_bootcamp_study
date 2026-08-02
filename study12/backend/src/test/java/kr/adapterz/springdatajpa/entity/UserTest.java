package kr.adapterz.springdatajpa.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class UserTest {

    @Test
    void 프로필_이미지_없이_유저를_생성하면_기본값이_정상적으로_설정된다() {
        String email = "test@test.com";
        String password = "Password1!";
        String nickname = "tester";

        User user = new User(email, password, nickname, 0);

        assertAll(
                () -> assertThat(user.getEmail()).isEqualTo(email),
                () -> assertThat(user.getPassword()).isEqualTo(password),
                () -> assertThat(user.getNickname()).isEqualTo(nickname),
                () -> assertThat(user.getProfileImage()).isNull(),
                () -> assertThat(user.isDeleted()).isFalse(),
                () -> assertThat(user.getReceivedReportCount()).isZero()
        );
    }

    @Test
    void 유저_정보를_수정하면_닉네임과_프로필_이미지가_변경된다() {
        User user = new User(
                "test@test.com",
                "Password1!",
                "tester",
                "old-profile.png",
                5
        );

        user.update("newTester", "new-profile.png");

        assertAll(
                () -> assertThat(user.getNickname()).isEqualTo("newTester"),
                () -> assertThat(user.getProfileImage()).isEqualTo("new-profile.png")
        );
    }

    @Test
    void 유저를_삭제하면_deleted가_true가_되고_닉네임과_프로필_이미지가_삭제_상태로_변경된다() {
        User user = new User(
                "test@test.com",
                "Password1!",
                "tester",
                "profile.png",
                0
        );

        user.delete();

        assertAll(
                () -> assertThat(user.isDeleted()).isTrue(),
                () -> assertThat(user.getNickname()).isEqualTo("삭제된 유저"),
                () -> assertThat(user.getProfileImage()).isNull()
        );
    }

    @Test
    void 비밀번호를_변경하면_새로운_비밀번호로_바뀌고_인증_버전이_증가한다() {
        User user = new User(
                "test@test.com",
                "OldPassword1!",
                "tester",
                5
        );

        user.changePassword("NewPassword1!");

        assertThat(user.getPassword()).isEqualTo("NewPassword1!");
        assertThat(user.getAuthVersion()).isEqualTo(1L);
    }

    @Test
    void 작성한_게시글이_신고되면_누적_신고_수가_증가한다() {
        User user = new User(
                "test@test.com",
                "Password1!",
                "tester",
                "profile.png",
                0
        );

        user.receiveReport();
        user.receiveReport();

        assertThat(user.getReceivedReportCount()).isEqualTo(2);
    }

    @Test
    void 누적_신고_수가_10회_이상이면_정지_계정으로_판단된다() {
        User user = new User(
                "test@test.com",
                "Password1!",
                "tester",
                "profile.png",
                9
        );

        assertThat(user.isSuspended()).isFalse();

        user.receiveReport();

        assertThat(user.isSuspended()).isTrue();
    }
}
