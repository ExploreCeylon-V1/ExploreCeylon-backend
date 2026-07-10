package com.exploreceylon.backend.service;

import com.exploreceylon.backend.model.LoginHistory;
import com.exploreceylon.backend.model.User;
import com.exploreceylon.backend.repository.LoginHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LoginHistoryService {

    private final LoginHistoryRepository loginHistoryRepository;

    public void record(User user, LoginHistory.LoginType loginType, String ipAddress, String deviceInfo) {
        loginHistoryRepository.save(LoginHistory.builder()
                .user(user)
                .loginType(loginType)
                .ipAddress(ipAddress)
                .deviceInfo(deviceInfo)
                .build());
    }
}
