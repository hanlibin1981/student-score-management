package com.score.vo;

import com.score.entity.User;

public record UserVO(Long id, String username, String realName, String role, Boolean enabled) {

    public static UserVO from(User user) {
        return new UserVO(user.getId(), user.getUsername(), user.getRealName(),
                user.getRole(), user.getEnabled());
    }
}
