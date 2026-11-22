package com.minibank.account.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minibank.account.dto.RegisterRequest;
import com.minibank.account.entity.Account;
import com.minibank.account.repository.AccountRepository;
import com.minibank.account.repository.RefreshTokenRepository;
import com.minibank.account.repository.UserCredentialRepository;
import com.minibank.account.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserCredentialRepository userCredentialRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock AccountRepository accountRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtUtil jwtUtil;

    @InjectMocks AuthService authService;

    @Test
    void selfRegistrationCannotChooseBalanceOrLimit() throws Exception {
        // A client still sending the old fields must have them ignored
        // (Spring Boot's ObjectMapper does not fail on unknown properties)
        ObjectMapper mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        RegisterRequest request = mapper.readValue("""
                {"holderName":"Mallory","email":"m@test.com","password":"secret123",
                 "initialBalance":1000000000,"dailyTransferLimit":999999999999}
                """, RegisterRequest.class);

        when(accountRepository.save(any())).thenAnswer(inv -> {
            Account a = inv.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });
        when(userCredentialRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        authService.register(request);

        ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(saved.capture());
        assertThat(saved.getValue().getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saved.getValue().getDailyTransferLimit()).isEqualByComparingTo("50000000");
    }
}
