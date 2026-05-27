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

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public LoginResponse login(String username, String password) {
        long startTime = System.currentTimeMillis();
        log.info("[UserService.login] 方法开始 | 请求参数: username={}", username);

        try {
            // 查询用户
            log.debug("[UserService.login] 查询用户信息 | username={}", username);
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> {
                        log.warn("[UserService.login] 用户不存在 | username={}", username);
                        return new BusinessException("用户名或密码错误", HttpStatus.UNAUTHORIZED);
                    });
            log.debug("[UserService.login] 用户查询成功 | userId={}, username={}", user.getId(), username);

            // 检查账户是否被锁定
            if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
                log.warn("[UserService.login] 账户已被锁定 | userId={}, lockedUntil={}", 
                        user.getId(), user.getLockedUntil());
                throw new BusinessException("账户已被锁定，请稍后重试", HttpStatus.UNAUTHORIZED);
            }

            // 验证密码
            log.debug("[UserService.login] 验证密码 | userId={}", user.getId());
            if (!passwordEncoder.matches(password, user.getPasswordHash())) {
                log.warn("[UserService.login] 密码验证失败 | userId={}, username={}", user.getId(), username);
                handleFailedLogin(user);
                throw new BusinessException("用户名或密码错误", HttpStatus.UNAUTHORIZED);
            }
            log.debug("[UserService.login] 密码验证成功 | userId={}", user.getId());

            // 重置失败次数
            resetFailedLoginAttempts(user);

            // 更新最后登录时间
            user.setLastLoginAt(LocalDateTime.now());
            userRepository.save(user);
            log.debug("[UserService.login] 更新最后登录时间 | userId={}, lastLoginAt={}", 
                    user.getId(), user.getLastLoginAt());

            // 生成Token
            log.debug("[UserService.login] 生成JWT令牌 | userId={}, username={}", user.getId(), username);
            String accessToken = jwtTokenProvider.generateToken(user.getId(), user.getUsername(), user.getRole());
            String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());
            log.info("[UserService.login] 登录成功 | userId={}, username={}, role={}", 
                    user.getId(), username, user.getRole());

            long costTime = System.currentTimeMillis() - startTime;
            log.debug("[UserService.login] 方法执行完成 | 耗时={}ms", costTime);

            return LoginResponse.of(accessToken, refreshToken,
                    jwtTokenProvider.getExpirationInMillis(),
                    LoginResponse.UserInfo.builder()
                            .id(user.getId())
                            .username(user.getUsername())
                            .email(user.getEmail())
                            .role(user.getRole())
                            .build());

        } catch (BusinessException e) {
            log.error("[UserService.login] 业务异常 | username={}, error={}", username, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("[UserService.login] 系统异常 | username={}, error={}, stackTrace={}", 
                    username, e.getMessage(), e.getStackTrace());
            throw new BusinessException("登录失败", e, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public String refreshToken(String refreshToken) {
        long startTime = System.currentTimeMillis();
        log.info("[UserService.refreshToken] 方法开始 | refreshToken前8位={}", 
                refreshToken != null && refreshToken.length() > 8 ? refreshToken.substring(0, 8) + "..." : "null");

        try {
            if (!jwtTokenProvider.validateToken(refreshToken)) {
                log.warn("[UserService.refreshToken] Refresh Token无效");
                throw new BusinessException("无效的Refresh Token", HttpStatus.UNAUTHORIZED);
            }

            String userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
            log.debug("[UserService.refreshToken] 从Token中解析用户ID | userId={}", userId);

            User user = userRepository.findById(userId)
                    .orElseThrow(() -> {
                        log.error("[UserService.refreshToken] 用户不存在 | userId={}", userId);
                        return new BusinessException("用户不存在", HttpStatus.UNAUTHORIZED);
                    });

            String newAccessToken = jwtTokenProvider.generateToken(user.getId(), user.getUsername(), user.getRole());
            log.info("[UserService.refreshToken] Token刷新成功 | userId={}", userId);

            long costTime = System.currentTimeMillis() - startTime;
            log.debug("[UserService.refreshToken] 方法执行完成 | 耗时={}ms", costTime);

            return newAccessToken;

        } catch (BusinessException e) {
            log.error("[UserService.refreshToken] 业务异常 | error={}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("[UserService.refreshToken] 系统异常 | error={}, stackTrace={}", 
                    e.getMessage(), e.getStackTrace());
            throw new BusinessException("Token刷新失败", e, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Transactional
    public void logout(String userId) {
        log.info("[UserService.logout] 用户登出 | userId={}", userId);
        try {
            log.info("[UserService.logout] 登出成功 | userId={}", userId);
        } catch (Exception e) {
            log.error("[UserService.logout] 登出异常 | userId={}, error={}", userId, e.getMessage());
        }
    }

    private void handleFailedLogin(User user) {
        log.debug("[UserService.handleFailedLogin] 处理登录失败 | userId={}", user.getId());
        int failedAttempts = user.getFailedLoginAttempts() != null ? user.getFailedLoginAttempts() : 0;
        failedAttempts++;

        if (failedAttempts >= 5) {
            user.setLockedUntil(LocalDateTime.now().plusMinutes(15));
            user.setFailedLoginAttempts(0);
            log.warn("[UserService.handleFailedLogin] 账户被锁定 | userId={}, failedAttempts={}, lockedUntil={}",
                    user.getId(), failedAttempts, user.getLockedUntil());
        } else {
            user.setFailedLoginAttempts(failedAttempts);
            log.warn("[UserService.handleFailedLogin] 登录失败次数增加 | userId={}, failedAttempts={}",
                    user.getId(), failedAttempts);
        }

        userRepository.save(user);
    }

    private void resetFailedLoginAttempts(User user) {
        if (user.getFailedLoginAttempts() != null && user.getFailedLoginAttempts() > 0) {
            log.debug("[UserService.resetFailedLoginAttempts] 重置失败次数 | userId={}, previousAttempts={}",
                    user.getId(), user.getFailedLoginAttempts());
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
        }
    }
}
