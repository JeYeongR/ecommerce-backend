package com.ecommerce.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.ecommerce.backend.auth.dto.LoginRequest;
import com.ecommerce.backend.auth.dto.SellerSignupRequest;
import com.ecommerce.backend.auth.dto.SellerSignupResponse;
import com.ecommerce.backend.auth.dto.TokenResponse;
import com.ecommerce.backend.common.BusinessException;
import com.ecommerce.backend.common.ErrorCode;
import com.ecommerce.backend.common.concurrent.KeyLockManager;
import com.ecommerce.backend.security.AccountType;
import com.ecommerce.backend.security.JwtProvider;
import com.ecommerce.backend.seller.domain.Seller;
import com.ecommerce.backend.seller.repository.SellerRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SellerAuthServiceTest {

    @Mock
    private SellerRepository sellerRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtProvider jwtProvider;

    private SellerAuthService sellerAuthService;

    @BeforeEach
    void setUp() {
        sellerAuthService = new SellerAuthService(sellerRepository, passwordEncoder, jwtProvider, new KeyLockManager());
    }

    @Test
    void 회원가입_성공() {
        SellerSignupRequest request = new SellerSignupRequest("seller@test.com", "password", "샵이름");
        given(sellerRepository.existsByEmail(request.email())).willReturn(false);
        given(passwordEncoder.encode(request.password())).willReturn("encoded");
        given(sellerRepository.save(any(Seller.class))).willAnswer(invocation -> {
            Seller seller = invocation.getArgument(0);
            ReflectionTestUtils.setField(seller, "id", 1L);
            return seller;
        });

        SellerSignupResponse response = sellerAuthService.signup(request);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.email()).isEqualTo(request.email());
        assertThat(response.shopName()).isEqualTo(request.shopName());
    }

    @Test
    void 회원가입_이메일_중복이면_예외() {
        SellerSignupRequest request = new SellerSignupRequest("seller@test.com", "password", "샵이름");
        given(sellerRepository.existsByEmail(request.email())).willReturn(true);

        assertThatThrownBy(() -> sellerAuthService.signup(request))
            .isInstanceOf(BusinessException.class)
            .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS));
    }

    @Test
    void 로그인_성공() {
        LoginRequest request = new LoginRequest("seller@test.com", "password");
        Seller seller = Seller.builder()
            .id(1L)
            .email(request.email())
            .password("encoded")
            .shopName("샵이름")
            .build();
        given(sellerRepository.findByEmail(request.email())).willReturn(Optional.of(seller));
        given(passwordEncoder.matches(request.password(), seller.getPassword())).willReturn(true);
        given(jwtProvider.generateToken(1L, AccountType.SELLER)).willReturn("token");

        TokenResponse response = sellerAuthService.login(request);

        assertThat(response.accessToken()).isEqualTo("token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
    }

    @Test
    void 로그인_존재하지않는_이메일이면_예외() {
        LoginRequest request = new LoginRequest("noone@test.com", "password");
        given(sellerRepository.findByEmail(request.email())).willReturn(Optional.empty());

        assertThatThrownBy(() -> sellerAuthService.login(request))
            .isInstanceOf(BusinessException.class)
            .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS));
    }

    @Test
    void 로그인_비밀번호가_틀리면_예외() {
        LoginRequest request = new LoginRequest("seller@test.com", "wrong");
        Seller seller = Seller.builder()
            .id(1L)
            .email(request.email())
            .password("encoded")
            .shopName("샵이름")
            .build();
        given(sellerRepository.findByEmail(request.email())).willReturn(Optional.of(seller));
        given(passwordEncoder.matches(request.password(), seller.getPassword())).willReturn(false);

        assertThatThrownBy(() -> sellerAuthService.login(request))
            .isInstanceOf(BusinessException.class)
            .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS));
    }
}
