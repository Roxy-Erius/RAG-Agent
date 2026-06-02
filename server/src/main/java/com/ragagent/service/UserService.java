package com.ragagent.service;

import com.ragagent.model.User;
import com.ragagent.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public UserService(UserRepository userRepository) { this.userRepository = userRepository; }

    public User register(String username, String password) {
        if (userRepository.findByUsername(username) != null) {
            throw new IllegalArgumentException("用户名已存在");
        }
        String hash = encoder.encode(password);
        log.info("用户注册 | username={}", username);
        return userRepository.create(username, hash);
    }

    public User login(String username, String password) {
        User user = userRepository.findByUsername(username);
        if (user == null || !encoder.matches(password, user.getPasswordHash())) {
            return null;
        }
        log.info("用户登录 | username={}", username);
        return user;
    }
}
