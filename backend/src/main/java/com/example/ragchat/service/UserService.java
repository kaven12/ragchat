package com.example.ragchat.service;

import com.example.ragchat.dto.response.LoginResponse;
import com.example.ragchat.entity.User;
import com.example.ragchat.exception.BusinessException;
import com.example.ragchat.repository.UserRepository;
import com.example.ragchat.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public LoginResponse login(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException("用户名或密码错误", HttpStatus.UNAUTHORIZED));

        // 检查账户是否被锁定
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            throw new BusinessException("账户已被锁定，请稍后重试", HttpStatus.UNAUTHORIZED);
        }

        // 验证密码
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            handleFailedLogin(user);
            throw new BusinessException("用户名或密码错误", HttpStatus.UNAUTHORIZED);
        }

        // 重置失败次数
        resetFailedLoginAttempts(user);

        // 更新最后登录时间
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        // 生成Token
        String accessToken = jwtTokenProvider.generateToken(user.getId(), user.getUsername(), user.getRole());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        return LoginResponse.of(accessToken, refreshToken, 
                jwtTokenProvider.getExpirationInMillis(), 
                LoginResponse.UserInfo.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .email(user.getEmail())
                        .role(user.getRole())
                        .build());
    }

    public String refreshToken(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new BusinessException("无效的Refresh Token", HttpStatus.UNAUTHORIZED);
        }

        String userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在", HttpStatus.UNAUTHORIZED));

        return jwtTokenProvider.generateToken(user.getId(), user.getUsername(), user.getRole());
    }

    @Transactional
    public void logout(String userId) {
        // 清除用户相关缓存（如果有）
        log.info("User {} logged out", userId);
    }

    private void handleFailedLogin(User user) {
        int failedAttempts = user.getFailedLoginAttempts() != null ? user.getFailedLoginAttempts() : 0;
        failedAttempts++;
        
        if (failedAttempts >= 5) {
            user.setLockedUntil(LocalDateTime.now().plusMinutes(15));
            user.setFailedLoginAttempts(0);
        } else {
            user.setFailedLoginAttempts(failedAttempts);
        }
        
        userRepository.save(user);
    }

    private void resetFailedLoginAttempts(User user) {
        if (user.getFailedLoginAttempts() != null && user.getFailedLoginAttempts() > 0) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
        }
    }
}
