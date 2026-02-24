package com.minibank.account.config;

import com.minibank.account.entity.Account;
import com.minibank.account.entity.UserCredential;
import com.minibank.account.repository.AccountRepository;
import com.minibank.account.repository.UserCredentialRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final AccountRepository accountRepository;
    private final UserCredentialRepository userCredentialRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (accountRepository.count() > 0) {
            log.info("Data already seeded, skipping...");
            return;
        }

        log.info("Seeding initial data...");

        // Seed accounts — let JPA generate UUIDs
        Account acc1 = createAccount("1234567890", "Ahmad Syarif", "syarif@minibank.com", "100000000.00", "50000000.00");
        Account acc2 = createAccount("0987654321", "Budi Santoso", "budi@minibank.com", "75000000.00", "50000000.00");
        Account acc3 = createAccount("1122334455", "Citra Dewi", "citra@minibank.com", "25000000.00", "25000000.00");
        Account acc4 = createAccount("5566778899", "Dian Pratama", "dian@minibank.com", "150000000.00", "100000000.00");
        Account acc5 = createAccount("6677889900", "Eka Wijaya", "eka@minibank.com", "500000.00", "10000000.00");

        // Seed admin credential (no linked account — admin is a system user)
        if (!userCredentialRepository.existsByEmail("admin@minibank.com")) {
            UserCredential admin = UserCredential.builder()
                    .email("admin@minibank.com")
                    .password(passwordEncoder.encode("admin123"))
                    .role("ADMIN")
                    .accountId(null)
                    .build();
            userCredentialRepository.save(admin);
            log.info("Admin user created: admin@minibank.com / admin123");
        }

        // Seed user credentials for sample accounts
        createUserCredential("syarif@minibank.com", "password123", "USER", acc1.getId());
        createUserCredential("budi@minibank.com", "password123", "USER", acc2.getId());
        createUserCredential("citra@minibank.com", "password123", "USER", acc3.getId());
        createUserCredential("dian@minibank.com", "password123", "USER", acc4.getId());
        createUserCredential("eka@minibank.com", "password123", "USER", acc5.getId());

        log.info("Data seeding completed! 5 accounts + 1 admin + 5 user credentials created.");
    }

    private Account createAccount(String accountNumber, String holderName,
                                   String email, String balance, String dailyLimit) {
        Account account = Account.builder()
                .accountNumber(accountNumber)
                .holderName(holderName)
                .email(email)
                .balance(new BigDecimal(balance))
                .dailyTransferLimit(new BigDecimal(dailyLimit))
                .status("ACTIVE")
                .build();
        log.info("Creating account: {} ({})", holderName, accountNumber);
        return accountRepository.save(account);
    }

    private void createUserCredential(String email, String password, String role, java.util.UUID accountId) {
        if (!userCredentialRepository.existsByEmail(email)) {
            UserCredential credential = UserCredential.builder()
                    .email(email)
                    .password(passwordEncoder.encode(password))
                    .role(role)
                    .accountId(accountId)
                    .build();
            userCredentialRepository.save(credential);
            log.info("User credential created for: {}", email);
        }
    }
}
