package com.ecommerce.backend.product.service;

import com.ecommerce.backend.common.BusinessException;
import com.ecommerce.backend.common.ErrorCode;
import com.ecommerce.backend.common.PageResponse;
import com.ecommerce.backend.common.domain.Money;
import com.ecommerce.backend.product.domain.Product;
import com.ecommerce.backend.product.domain.ProductImage;
import com.ecommerce.backend.product.domain.ProductOption;
import com.ecommerce.backend.product.domain.ProductStatus;
import com.ecommerce.backend.product.dto.ProductCreateRequest;
import com.ecommerce.backend.product.dto.ProductOptionResponse;
import com.ecommerce.backend.product.dto.ProductResponse;
import com.ecommerce.backend.product.dto.ProductSummaryResponse;
import com.ecommerce.backend.product.dto.ProductUpdateRequest;
import com.ecommerce.backend.product.repository.ProductRepository;
import com.ecommerce.backend.seller.domain.Seller;
import com.ecommerce.backend.seller.repository.SellerRepository;
import java.util.List;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final SellerRepository sellerRepository;

    @Transactional
    public ProductResponse create(Long sellerId, ProductCreateRequest request) {
        Seller seller = sellerRepository.findById(sellerId)
            .orElseThrow(() -> new BusinessException(ErrorCode.SELLER_NOT_FOUND));

        Product product = Product.builder()
            .seller(seller)
            .name(request.name())
            .description(request.description())
            .thumbnailUrl(request.thumbnailUrl())
            .basePrice(Money.of(request.basePrice()))
            .status(ProductStatus.ON_SALE)
            .build();

        request.options().forEach(o -> product.addOption(
            ProductOption.builder()
                .optionName(o.optionName())
                .additionalPrice(Money.of(o.additionalPrice()))
                .stock(o.stock())
                .build()
        ));

        List<String> imageUrls = request.images() != null ? request.images() : List.of();
        IntStream.range(0, imageUrls.size()).forEach(i -> product.addImage(
            ProductImage.builder()
                .imageUrl(imageUrls.get(i))
                .sortOrder(i)
                .build()
        ));

        productRepository.save(product);

        return ProductResponse.of(
            product,
            imageUrls,
            product.getOptions().stream()
                    .map(ProductOptionResponse::from)
                    .toList()
        );
    }

    public PageResponse<ProductSummaryResponse> list(Pageable pageable) {
        Page<ProductSummaryResponse> responses = productRepository.findAll(pageable)
                .map(ProductSummaryResponse::from);

        return PageResponse.from(responses);
    }

    public ProductResponse get(Long productId) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        return ProductResponse.of(product, imageUrls(product), options(product));
    }

    @Transactional
    public ProductResponse update(Long sellerId, Long productId, ProductUpdateRequest request) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        if (!product.getSeller().getId().equals(sellerId)) {
            throw new BusinessException(ErrorCode.PRODUCT_ACCESS_DENIED);
        }

        product.update(request.name(), request.basePrice(), request.description());

        return ProductResponse.of(product, imageUrls(product), options(product));
    }

    @Transactional
    public void delete(Long sellerId, Long productId) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        if (!product.getSeller().getId().equals(sellerId)) {
            throw new BusinessException(ErrorCode.PRODUCT_ACCESS_DENIED);
        }

        productRepository.delete(product);
    }

    private List<String> imageUrls(Product product) {
        return product.getImages().stream()
                .map(ProductImage::getImageUrl)
                .toList();
    }

    private List<ProductOptionResponse> options(Product product) {
        return product.getOptions().stream()
                .map(ProductOptionResponse::from)
                .toList();
    }
}
