package com.ecommerce.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

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
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CustomerAuthServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtProvider jwtProvider;

    private CustomerAuthService customerAuthService;

    @BeforeEach
    void setUp() {
        customerAuthService = new CustomerAuthService(customerRepository, passwordEncoder, jwtProvider, new KeyLockManager());
    }

    @Test
    void 회원가입_성공() {
        SignupRequest request = new SignupRequest("test@test.com", "password", "이름", "닉네임");
        given(customerRepository.existsByEmail(request.email())).willReturn(false);
        given(passwordEncoder.encode(request.password())).willReturn("encoded");
        given(customerRepository.save(any(Customer.class))).willAnswer(invocation -> {
            Customer customer = invocation.getArgument(0);
            ReflectionTestUtils.setField(customer, "id", 1L);
            return customer;
        });

        CustomerSignupResponse response = customerAuthService.signup(request);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.email()).isEqualTo(request.email());
        assertThat(response.nickname()).isEqualTo(request.nickname());
    }

    @Test
    void 회원가입_이메일_중복이면_예외() {
        SignupRequest request = new SignupRequest("test@test.com", "password", "이름", "닉네임");
        given(customerRepository.existsByEmail(request.email())).willReturn(true);

        assertThatThrownBy(() -> customerAuthService.signup(request))
            .isInstanceOf(BusinessException.class)
            .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS));
    }

    @Test
    void 로그인_성공() {
        LoginRequest request = new LoginRequest("test@test.com", "password");
        Customer customer = Customer.builder()
            .id(1L)
            .email(request.email())
            .password("encoded")
            .name("이름")
            .nickname("닉네임")
            .build();
        given(customerRepository.findByEmail(request.email())).willReturn(Optional.of(customer));
        given(passwordEncoder.matches(request.password(), customer.getPassword())).willReturn(true);
        given(jwtProvider.generateToken(1L, AccountType.CUSTOMER)).willReturn("token");

        TokenResponse response = customerAuthService.login(request);

        assertThat(response.accessToken()).isEqualTo("token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
    }

    @Test
    void 로그인_존재하지않는_이메일이면_예외() {
        LoginRequest request = new LoginRequest("noone@test.com", "password");
        given(customerRepository.findByEmail(request.email())).willReturn(Optional.empty());

        assertThatThrownBy(() -> customerAuthService.login(request))
            .isInstanceOf(BusinessException.class)
            .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS));
    }

    @Test
    void 로그인_비밀번호가_틀리면_예외() {
        LoginRequest request = new LoginRequest("test@test.com", "wrong");
        Customer customer = Customer.builder()
            .id(1L)
            .email(request.email())
            .password("encoded")
            .name("이름")
            .nickname("닉네임")
            .build();
        given(customerRepository.findByEmail(request.email())).willReturn(Optional.of(customer));
        given(passwordEncoder.matches(request.password(), customer.getPassword())).willReturn(false);

        assertThatThrownBy(() -> customerAuthService.login(request))
            .isInstanceOf(BusinessException.class)
            .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS));
    }
}
