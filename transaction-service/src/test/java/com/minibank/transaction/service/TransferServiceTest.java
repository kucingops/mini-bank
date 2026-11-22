package com.minibank.transaction.service;

import com.minibank.transaction.dto.TransferRequest;
import com.minibank.transaction.dto.TransferResponse;
import com.minibank.transaction.entity.Account;
import com.minibank.transaction.entity.Transaction;
import com.minibank.transaction.repository.AccountRepository;
import com.minibank.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock TransactionRepository transactionRepository;
    @Mock AccountRepository accountRepository;
    @Mock RedissonClient redissonClient;
    @Mock RedisTemplate<String, Object> redisTemplate;
    @Mock RLock lock;

    TransferService service;

    final UUID alice = UUID.randomUUID();
    final UUID bob = UUID.randomUUID();
    final UUID mallory = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new TransferService(transactionRepository, accountRepository, redissonClient, redisTemplate);
        ReflectionTestUtils.setField(service, "transferRequestedStream", "transfer-requested");
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    // ==================== Ownership ====================

    @Test
    void cannotTransferFromSomeoneElsesAccount() {
        loginAs(mallory, "USER");

        assertThatThrownBy(() -> service.initiateTransfer(request(alice, bob, "100000")))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(redissonClient, transactionRepository, accountRepository);
    }

    @Test
    void adminWithoutAccountCannotTransferOnBehalfOfUsers() {
        loginAs(null, "ADMIN");

        assertThatThrownBy(() -> service.initiateTransfer(request(alice, bob, "100000")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void cannotReadOtherPeoplesTransfer() {
        loginAs(mallory, "USER");
        when(transactionRepository.findById(any())).thenReturn(Optional.of(txn(alice, bob, "PENDING")));

        assertThatThrownBy(() -> service.getTransfer(UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void recipientAndAdminCanReadTransfer() {
        when(transactionRepository.findById(any())).thenReturn(Optional.of(txn(alice, bob, "PENDING")));

        loginAs(bob, "USER");
        assertThat(service.getTransfer(UUID.randomUUID()).getFromAccountId()).isEqualTo(alice);

        loginAs(null, "ADMIN");
        assertThat(service.getTransfer(UUID.randomUUID()).getFromAccountId()).isEqualTo(alice);
    }

    @Test
    void cannotReadOtherPeoplesHistory() {
        loginAs(mallory, "USER");

        assertThatThrownBy(() -> service.getTransactionHistory(alice))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(transactionRepository);
    }

    // ==================== Balance & daily limit ====================

    @Test
    void pendingTransfersReduceAvailableBalance() throws Exception {
        loginAs(alice, "USER");
        givenLockAcquired();
        givenAccounts("1000000", "50000000");
        // 900k already reserved by PENDING transfers -> only 100k available
        when(transactionRepository.sumPendingOutgoing(alice)).thenReturn(new BigDecimal("900000"));

        assertThatThrownBy(() -> service.initiateTransfer(request(alice, bob, "200000")))
                .hasMessageContaining("Insufficient balance");
        verify(transactionRepository, never()).save(any());
        verify(lock).unlock();
    }

    @Test
    void dailyLimitCountsPendingTransfers() throws Exception {
        loginAs(alice, "USER");
        givenLockAcquired();
        givenAccounts("100000000", "5000000");
        when(transactionRepository.sumPendingOutgoing(alice)).thenReturn(BigDecimal.ZERO);
        // The repository query sums PENDING + COMPLETED; 4.9M used so far today
        when(transactionRepository.sumDailyTransfers(eq(alice), any())).thenReturn(new BigDecimal("4900000"));

        assertThatThrownBy(() -> service.initiateTransfer(request(alice, bob, "200000")))
                .hasMessageContaining("Daily transfer limit exceeded");
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void validTransferIsSavedAsPendingAndPublished() throws Exception {
        loginAs(alice, "USER");
        givenLockAcquired();
        givenAccounts("10000000", "50000000");
        when(transactionRepository.sumPendingOutgoing(alice)).thenReturn(BigDecimal.ZERO);
        when(transactionRepository.sumDailyTransfers(eq(alice), any())).thenReturn(BigDecimal.ZERO);
        when(transactionRepository.save(any())).thenAnswer(inv -> {
            Transaction t = inv.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });
        @SuppressWarnings("unchecked")
        StreamOperations<String, Object, Object> streamOps = mock(StreamOperations.class);
        when(redisTemplate.opsForStream()).thenReturn(streamOps);
        when(streamOps.add(any())).thenReturn(RecordId.autoGenerate());

        TransferResponse response = service.initiateTransfer(request(alice, bob, "1000000"));

        assertThat(response.getStatus()).isEqualTo("PENDING");
        verify(redissonClient).getLock("lock:account:" + alice);
        verify(streamOps).add(any());
        verify(lock).unlock();
    }

    // ==================== Idempotent completion ====================

    @Test
    void duplicateCompletionEventDoesNotDebitTwice() {
        UUID id = UUID.randomUUID();
        when(transactionRepository.findById(id)).thenReturn(Optional.of(txn(alice, bob, "COMPLETED")));
        when(transactionRepository.transitionStatus(id, "PENDING", "COMPLETED", "PASSED")).thenReturn(0);

        service.completeTransfer(id);

        verifyNoInteractions(accountRepository);
    }

    @Test
    void completionDebitsAndCreditsOnce() {
        UUID id = UUID.randomUUID();
        Transaction txn = txn(alice, bob, "PENDING");
        when(transactionRepository.findById(id)).thenReturn(Optional.of(txn));
        when(transactionRepository.transitionStatus(id, "PENDING", "COMPLETED", "PASSED")).thenReturn(1);
        when(accountRepository.debitBalance(alice, txn.getAmount())).thenReturn(1);

        service.completeTransfer(id);

        verify(accountRepository).debitBalance(alice, txn.getAmount());
        verify(accountRepository).creditBalance(bob, txn.getAmount());
        verify(transactionRepository, never()).updateStatus(any(), eq("FAILED"), any());
    }

    @Test
    void completionFailsWhenBalanceIsGone() {
        UUID id = UUID.randomUUID();
        Transaction txn = txn(alice, bob, "PENDING");
        when(transactionRepository.findById(id)).thenReturn(Optional.of(txn));
        when(transactionRepository.transitionStatus(id, "PENDING", "COMPLETED", "PASSED")).thenReturn(1);
        when(accountRepository.debitBalance(alice, txn.getAmount())).thenReturn(0);

        service.completeTransfer(id);

        verify(transactionRepository).updateStatus(id, "FAILED", "PASSED");
        verify(accountRepository, never()).creditBalance(any(), any());
    }

    @Test
    void rejectionOnlyAppliesToPendingTransfers() {
        UUID id = UUID.randomUUID();
        when(transactionRepository.transitionStatus(id, "PENDING", "REJECTED", "FLAGGED")).thenReturn(0);

        service.rejectTransfer(id);

        verify(transactionRepository, never()).updateStatus(any(), any(), any());
    }

    // ==================== Helpers ====================

    private void loginAs(UUID accountId, String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "user@test", accountId != null ? accountId.toString() : null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    private void givenLockAcquired() throws InterruptedException {
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any())).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
    }

    private void givenAccounts(String aliceBalance, String aliceDailyLimit) {
        when(accountRepository.findById(alice)).thenReturn(Optional.of(account(alice, aliceBalance, aliceDailyLimit)));
        when(accountRepository.findById(bob)).thenReturn(Optional.of(account(bob, "0", "50000000")));
    }

    private static Account account(UUID id, String balance, String dailyLimit) {
        Account a = new Account();
        a.setId(id);
        a.setBalance(new BigDecimal(balance));
        a.setDailyTransferLimit(new BigDecimal(dailyLimit));
        a.setStatus("ACTIVE");
        return a;
    }

    private static Transaction txn(UUID from, UUID to, String status) {
        return Transaction.builder()
                .id(UUID.randomUUID())
                .referenceNo("TXN-TEST")
                .fromAccountId(from)
                .toAccountId(to)
                .amount(new BigDecimal("1000000"))
                .status(status)
                .build();
    }

    private static TransferRequest request(UUID from, UUID to, String amount) {
        return TransferRequest.builder()
                .fromAccountId(from)
                .toAccountId(to)
                .amount(new BigDecimal(amount))
                .build();
    }
}
