package com.urbansidequest.backend.service;

import com.urbansidequest.backend.config.AuthenticatedUser;
import com.urbansidequest.backend.domain.param.LoginParam;
import com.urbansidequest.backend.domain.vo.CurrentUserVO;
import com.urbansidequest.backend.domain.vo.LoginVO;
import org.springframework.web.multipart.MultipartFile;

public interface AuthService {

    LoginVO login(LoginParam loginParam);

    CurrentUserVO getCurrentUser(AuthenticatedUser authenticatedUser);

    CurrentUserVO updateAvatar(AuthenticatedUser authenticatedUser, MultipartFile avatar);
}
