package com.score.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.score.common.BusinessException;
import com.score.dto.LoginRequest;
import com.score.dto.PasswordChangeRequest;
import com.score.entity.User;
import com.score.mapper.UserMapper;
import com.score.security.JwtUtil;
import com.score.security.SecurityUtils;
import com.score.vo.LoginResponse;
import com.score.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public LoginResponse login(LoginRequest request) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername()));
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            throw BusinessException.forbidden("账号已被禁用");
        }
        return new LoginResponse(jwtUtil.generate(user), UserVO.from(user));
    }

    public UserVO me() {
        User user = userMapper.selectById(SecurityUtils.currentUserId());
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }
        return UserVO.from(user);
    }

    public void changePassword(PasswordChangeRequest request) {
        User user = userMapper.selectById(SecurityUtils.currentUserId());
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BusinessException("原密码不正确");
        }
        User update = new User();
        update.setId(user.getId());
        update.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userMapper.updateById(update);
    }
}
