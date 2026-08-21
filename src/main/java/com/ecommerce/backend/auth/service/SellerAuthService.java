package com.ecommerce.backend.auth.service;

import com.ecommerce.backend.auth.dto.LoginRequest;
import com.ecommerce.backend.auth.dto.SellerSignupRequest;
import com.ecommerce.backend.auth.dto.SellerSignupResponse;
import com.ecommerce.backend.auth.dto.TokenResponse;
import com.ecommerce.backend.common.BusinessException;
import com.ecommerce.backend.common.ErrorCode;
import com.ecommerce.backend.common.concurrent.KeyLockManager;
import com.ecommerce.backend.seller.domain.Seller;
import com.ecommerce.backend.seller.repository.SellerRepository;
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
public class SellerAuthService {

    private final SellerRepository sellerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final KeyLockManager keyLockManager;

    @Transactional
    public SellerSignupResponse signup(SellerSignupRequest request) {
        return keyLockManager.withLock(request.email(), () -> createSeller(request));
    }

    private SellerSignupResponse createSeller(SellerSignupRequest request) {
        if (sellerRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        Seller seller = Seller.builder()
            .email(request.email())
            .password(passwordEncoder.encode(request.password()))
            .shopName(request.shopName())
            .build();

        try {
            sellerRepository.save(seller);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        return new SellerSignupResponse(seller.getId(), seller.getEmail(), seller.getShopName());
    }

    public TokenResponse login(LoginRequest request) {
        Seller seller = sellerRepository.findByEmail(request.email())
            .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), seller.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        String token = jwtProvider.generateToken(seller.getId(), AccountType.SELLER);

        return TokenResponse.bearer(token);
    }
}
