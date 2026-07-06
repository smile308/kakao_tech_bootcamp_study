package kr.adapterz.springdatajpa.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class UserTest {

    @Test
    @DisplayName("프로필 이미지 없이 유저를 생성하면 기본값이 정상적으로 설정된다")
    void createUserWithoutProfileImage() {
        // given
        String email = "test@test.com";
        String password = "Password1!";
        String nickname = "tester";

        // when
        User user = new User(email, password, nickname);

        // then
        assertAll(
                () -> assertThat(user.getEmail()).isEqualTo(email),
                () -> assertThat(user.getPassword()).isEqualTo(password),
                () -> assertThat(user.getNickname()).isEqualTo(nickname),
                () -> assertThat(user.getProfileImage()).isNull(),
                () -> assertThat(user.isDeleted()).isFalse()
        );
    }

    @Test
    @DisplayName("유저 정보를 수정하면 닉네임과 프로필 이미지가 변경된다")
    void updateUser() {
        // given
        User user = new User(
                "test@test.com",
                "Password1!",
                "tester",
                "old-profile.png"
        );

        // when
        user.update("newTester", "new-profile.png");

        // then
        assertAll(
                () -> assertThat(user.getNickname()).isEqualTo("newTester"),
                () -> assertThat(user.getProfileImage()).isEqualTo("new-profile.png")
        );
    }

    @Test
    @DisplayName("유저를 삭제하면 deleted가 true가 되고 닉네임과 프로필 이미지가 삭제 상태로 변경된다")
    void deleteUser() {
        // given
        User user = new User(
                "test@test.com",
                "Password1!",
                "tester",
                "profile.png"
        );

        // when
        user.delete();

        // then
        assertAll(
                () -> assertThat(user.isDeleted()).isTrue(),
                () -> assertThat(user.getNickname()).isEqualTo("삭제된 유저"),
                () -> assertThat(user.getProfileImage()).isNull()
        );
    }

    @Test
    @DisplayName("비밀번호를 변경하면 새로운 비밀번호로 바뀐다")
    void setPassword() {
        // given
        User user = new User(
                "test@test.com",
                "OldPassword1!",
                "tester"
        );

        // when
        user.setPassword("NewPassword1!");

        // then
        assertThat(user.getPassword()).isEqualTo("NewPassword1!");
    }
}