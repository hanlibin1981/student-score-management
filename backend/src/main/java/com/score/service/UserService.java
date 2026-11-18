package com.score.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.score.common.BusinessException;
import com.score.common.PageResult;
import com.score.common.Role;
import com.score.dto.UserCreateRequest;
import com.score.entity.User;
import com.score.mapper.UserMapper;
import com.score.security.SecurityUtils;
import com.score.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public PageResult<UserVO> page(String role, String keyword, long page, long size) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .eq(StringUtils.hasText(role), User::getRole, role)
                .and(StringUtils.hasText(keyword), w -> w
                        .like(User::getUsername, keyword)
                        .or()
                        .like(User::getRealName, keyword))
                .orderByAsc(User::getId);
        Page<User> result = userMapper.selectPage(new Page<>(page, size), wrapper);
        List<UserVO> list = result.getRecords().stream().map(UserVO::from).toList();
        return new PageResult<>(list, result.getTotal(), result.getCurrent(), result.getSize());
    }

    public UserVO create(UserCreateRequest request) {
        long exists = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername()));
        if (exists > 0) {
            throw BusinessException.conflict("用户名已存在");
        }
        User user = new User();
        user.setUsername(request.getUsername());
        user.setRealName(request.getRealName());
        String rawPassword = StringUtils.hasText(request.getPassword())
                ? request.getPassword() : "123456";
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRole(Role.TEACHER);
        user.setEnabled(true);
        userMapper.insert(user);
        return UserVO.from(user);
    }

    public void setStatus(Long id, boolean enabled) {
        requireUser(id);
        if (id.equals(SecurityUtils.currentUserId())) {
            throw new BusinessException("不能修改当前登录账号的状态");
        }
        User update = new User();
        update.setId(id);
        update.setEnabled(enabled);
        userMapper.updateById(update);
    }

    public void resetPassword(Long id) {
        requireUser(id);
        User update = new User();
        update.setId(id);
        update.setPassword(passwordEncoder.encode("123456"));
        userMapper.updateById(update);
    }

    private User requireUser(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }
        return user;
    }
}
