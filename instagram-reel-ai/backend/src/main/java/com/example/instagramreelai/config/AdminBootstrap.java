package com.example.instagramreelai.config;

import com.example.instagramreelai.entity.AppUser;
import com.example.instagramreelai.entity.InstagramAccount;
import com.example.instagramreelai.repository.AppUserRepository;
import com.example.instagramreelai.repository.InstagramAccountRepository;
import com.example.instagramreelai.service.EncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class AdminBootstrap {

    private final AppProperties appProperties;
    private final AppUserRepository userRepository;
    private final InstagramAccountRepository accountRepository;
    private final EncryptionService encryptionService;
    private final PasswordEncoder passwordEncoder;

    @Bean
    public ApplicationRunner bootstrapAdmin() {
        return args -> {
            String username = appProperties.getAdmin().getUsername();
            AppUser admin = userRepository.findByUsername(username).orElse(null);
            if (admin == null) {
                admin = AppUser.builder()
                        .username(username)
                        .passwordHash(passwordEncoder.encode(appProperties.getAdmin().getPassword()))
                        .role("ADMIN")
                        .active(true)
                        .build();
                admin = userRepository.save(admin);
                log.info("Bootstrap admin user '{}' created", username);
            }

            // Bootstrap configured Instagram account from environment if provided
            String accountId = appProperties.getInstagram().getAccountId();
            String token = appProperties.getInstagram().getAccessToken();
            if (accountId != null && !accountId.isBlank() && token != null && !token.isBlank()) {
                InstagramAccount account = accountRepository.findByInstagramAccountId(accountId)
                        .orElseGet(InstagramAccount::new);
                account.setInstagramAccountId(accountId);
                account.setUser(admin);
                if (account.getUsername() == null || account.getUsername().isBlank()) {
                    account.setUsername("unireelverse.in");
                }
                account.setDisplayName("unireelverse.in");
                account.setActive(true);
                account.setConnectionStatus("CONNECTED");
                account.setAccessTokenEncrypted(encryptionService.encrypt(token));
                accountRepository.save(account);
                log.info("Bootstrapped Instagram account '{}'", accountId);
            }
        };
    }
}
