package kr.adapterz.springdatajpa.auth;

public record AccessTokenClaims(
        Long userId,
        long authVersion
) {
}
