package com.ecommerce.backend.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.ecommerce.backend.common.BusinessException;
import com.ecommerce.backend.common.ErrorCode;
import com.ecommerce.backend.common.domain.Money;
import com.ecommerce.backend.product.domain.Product;
import com.ecommerce.backend.product.domain.ProductStatus;
import com.ecommerce.backend.product.dto.ProductCreateRequest;
import com.ecommerce.backend.product.dto.ProductOptionRequest;
import com.ecommerce.backend.product.dto.ProductResponse;
import com.ecommerce.backend.product.dto.ProductUpdateRequest;
import com.ecommerce.backend.product.repository.ProductRepository;
import com.ecommerce.backend.seller.domain.Seller;
import com.ecommerce.backend.seller.repository.SellerRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private SellerRepository sellerRepository;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(productRepository, sellerRepository);
    }

    private Seller seller(Long id) {
        return Seller.builder()
            .id(id)
            .email("seller@test.com")
            .password("encoded")
            .shopName("샵")
            .build();
    }

    private Product product(Long id, Seller owner) {
        Product product = Product.builder()
            .id(id)
            .seller(owner)
            .name("상품")
            .thumbnailUrl("https://example.com/thumb.png")
            .basePrice(Money.of(1000))
            .status(ProductStatus.ON_SALE)
            .build();
        ReflectionTestUtils.setField(product, "salesCount", 0);
        return product;
    }

    @Test
    void 상품_등록_성공() {
        Seller seller = seller(1L);
        given(sellerRepository.findById(1L)).willReturn(Optional.of(seller));
        given(productRepository.save(any(Product.class))).willAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            ReflectionTestUtils.setField(product, "id", 10L);
            return product;
        });

        ProductCreateRequest request = new ProductCreateRequest(
            "상품명",
            1000,
            "설명",
            "https://example.com/thumb.png",
            List.of(),
            List.of(new ProductOptionRequest("옵션", 0, 5))
        );

        ProductResponse response = productService.create(1L, request);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.name()).isEqualTo("상품명");
        assertThat(response.options()).hasSize(1);
    }

    @Test
    void 상품_등록_판매자_없으면_예외() {
        given(sellerRepository.findById(1L)).willReturn(Optional.empty());

        ProductCreateRequest request = new ProductCreateRequest(
            "상품명", 1000, "설명", "https://example.com/thumb.png",
            List.of(), List.of(new ProductOptionRequest("옵션", 0, 5))
        );

        assertThatThrownBy(() -> productService.create(1L, request))
            .isInstanceOf(BusinessException.class)
            .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.SELLER_NOT_FOUND));
    }

    @Test
    void 상품_수정_성공() {
        Seller seller = seller(1L);
        Product product = product(10L, seller);
        given(productRepository.findById(10L)).willReturn(Optional.of(product));

        ProductUpdateRequest request = new ProductUpdateRequest("새이름", 2000, "새설명");

        ProductResponse response = productService.update(1L, 10L, request);

        assertThat(response.name()).isEqualTo("새이름");
        assertThat(response.basePrice()).isEqualTo(2000);
        assertThat(response.description()).isEqualTo("새설명");
    }

    @Test
    void 상품_수정_존재하지않으면_예외() {
        given(productRepository.findById(10L)).willReturn(Optional.empty());

        ProductUpdateRequest request = new ProductUpdateRequest("새이름", 2000, "새설명");

        assertThatThrownBy(() -> productService.update(1L, 10L, request))
            .isInstanceOf(BusinessException.class)
            .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.PRODUCT_NOT_FOUND));
    }

    @Test
    void 상품_수정_다른_판매자면_예외() {
        Seller owner = seller(1L);
        Product product = product(10L, owner);
        given(productRepository.findById(10L)).willReturn(Optional.of(product));

        ProductUpdateRequest request = new ProductUpdateRequest("새이름", 2000, "새설명");

        assertThatThrownBy(() -> productService.update(2L, 10L, request))
            .isInstanceOf(BusinessException.class)
            .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.PRODUCT_ACCESS_DENIED));
    }

    @Test
    void 상품_삭제_성공() {
        Seller owner = seller(1L);
        Product product = product(10L, owner);
        given(productRepository.findById(10L)).willReturn(Optional.of(product));

        productService.delete(1L, 10L);

        verify(productRepository, times(1)).delete(product);
    }

    @Test
    void 상품_삭제_다른_판매자면_예외() {
        Seller owner = seller(1L);
        Product product = product(10L, owner);
        given(productRepository.findById(10L)).willReturn(Optional.of(product));

        assertThatThrownBy(() -> productService.delete(2L, 10L))
            .isInstanceOf(BusinessException.class)
            .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.PRODUCT_ACCESS_DENIED));
        verify(productRepository, never()).delete(any(Product.class));
    }
}
