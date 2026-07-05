package com.urbansidequest.backend.service.impl;

import cn.hutool.core.util.StrUtil;
import com.urbansidequest.backend.config.AuthProperties;
import com.urbansidequest.backend.config.AuthenticatedUser;
import com.urbansidequest.backend.config.JwtTokenProvider;
import com.urbansidequest.backend.domain.param.LoginParam;
import com.urbansidequest.backend.domain.po.UserPO;
import com.urbansidequest.backend.domain.vo.CurrentUserVO;
import com.urbansidequest.backend.domain.vo.LoginVO;
import com.urbansidequest.backend.manage.UserManage;
import com.urbansidequest.backend.provider.user.UserAvatarObjectStore;
import com.urbansidequest.backend.provider.user.UserAvatarObjectStore.StoredUserAvatar;
import com.urbansidequest.backend.service.AuthService;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AuthServiceImpl implements AuthService {

    private static final String TOKEN_TYPE = "Bearer";

    private static final String ACTIVE_STATUS = "ACTIVE";

    private final AuthProperties authProperties;

    private final JwtTokenProvider jwtTokenProvider;

    private final UserManage userManage;

    private final UserAvatarObjectStore userAvatarObjectStore;

    public AuthServiceImpl(
            AuthProperties authProperties,
            JwtTokenProvider jwtTokenProvider,
            UserManage userManage,
            UserAvatarObjectStore userAvatarObjectStore
    ) {
        this.authProperties = authProperties;
        this.jwtTokenProvider = jwtTokenProvider;
        this.userManage = userManage;
        this.userAvatarObjectStore = userAvatarObjectStore;
    }

    @Override
    public LoginVO login(LoginParam loginParam) {
        String phone = StrUtil.trim(loginParam.getPhone());
        String code = StrUtil.trim(loginParam.getCode());
        if (!this.authProperties.getDevVerificationCode().equals(code)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "验证码不正确");
        }

        UserPO user = this.userManage.findByPhone(phone).orElseGet(() -> this.userManage.createByPhone(phone));
        if (!ACTIVE_STATUS.equals(user.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "账号不可用");
        }

        String accessToken = this.jwtTokenProvider.createAccessToken(user);
        return new LoginVO(
                TOKEN_TYPE,
                accessToken,
                this.jwtTokenProvider.getAccessTokenValiditySeconds(),
                this.toCurrentUserVO(user)
        );
    }

    @Override
    public CurrentUserVO getCurrentUser(AuthenticatedUser authenticatedUser) {
        UserPO user = this.userManage.findById(authenticatedUser.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录状态无效"));
        return this.toCurrentUserVO(user);
    }

    @Override
    public CurrentUserVO updateAvatar(AuthenticatedUser authenticatedUser, MultipartFile avatar) {
        UserPO user = this.userManage.findById(authenticatedUser.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录状态无效"));
        String contentType = this.validateAvatarContentType(avatar);
        StoredUserAvatar storedAvatar = this.userAvatarObjectStore.putAvatar(
                user.getId(),
                this.readAvatarBytes(avatar),
                contentType
        );
        UserPO updatedUser = this.userManage.updateAvatarUrl(user.getId(), storedAvatar.imageUrl());
        return this.toCurrentUserVO(updatedUser);
    }

    private CurrentUserVO toCurrentUserVO(UserPO user) {
        return new CurrentUserVO(
                user.getId(),
                user.getPhone(),
                user.getNickname(),
                user.getAvatarUrl(),
                user.getCompletedRouteCount() == null ? 0 : user.getCompletedRouteCount(),
                user.getTravelDistanceMeters() == null ? 0L : user.getTravelDistanceMeters()
        );
    }

    private byte[] readAvatarBytes(MultipartFile avatar) {
        if (avatar == null || avatar.isEmpty()) {
            throw new IllegalArgumentException("请先选择头像");
        }
        try {
            return avatar.getBytes();
        } catch (IOException exception) {
            throw new IllegalStateException("头像图片读取失败", exception);
        }
    }

    private String validateAvatarContentType(MultipartFile avatar) {
        if (avatar == null || avatar.isEmpty()) {
            throw new IllegalArgumentException("请先选择头像");
        }
        String contentType = avatar.getContentType();
        if (!"image/jpeg".equals(contentType) && !"image/png".equals(contentType) && !"image/webp".equals(contentType)) {
            throw new IllegalArgumentException("头像仅支持 JPEG、PNG 或 WebP");
        }
        return contentType;
    }
}
