package com.ecommerce.backend.auth.service;

import com.ecommerce.backend.auth.dto.CustomerSignupResponse;
import com.ecommerce.backend.auth.dto.LoginRequest;
import com.ecommerce.backend.auth.dto.SignupRequest;
import com.ecommerce.backend.auth.dto.TokenResponse;
import com.ecommerce.backend.common.BusinessException;
import com.ecommerce.backend.common.ErrorCode;
import com.ecommerce.backend.common.concurrent.KeyLockManager;
import com.ecommerce.backend.customer.domain.Customer;
import com.ecommerce.backend.customer.repository.CustomerRepository;
import com.ecommerce.backend.security.AccountType;
import com.ecommerce.backend.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerAuthService {

    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final KeyLockManager keyLockManager;

    @Transactional
    public CustomerSignupResponse signup(SignupRequest request) {
        return keyLockManager.withLock(request.email(), () -> createCustomer(request));
    }

    private CustomerSignupResponse createCustomer(SignupRequest request) {
        if (customerRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        Customer customer = Customer.builder()
            .email(request.email())
            .password(passwordEncoder.encode(request.password()))
            .name(request.name())
            .nickname(request.nickname())
            .build();

        try {
            customerRepository.save(customer);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        return new CustomerSignupResponse(customer.getId(), customer.getEmail(), customer.getNickname());
    }

    public TokenResponse login(LoginRequest request) {
        Customer customer = customerRepository.findByEmail(request.email())
            .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), customer.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        String token = jwtProvider.generateToken(customer.getId(), AccountType.CUSTOMER);

        return TokenResponse.bearer(token);
    }
}
