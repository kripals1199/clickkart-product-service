// src/main/java/com/clickkart/product/serviceImpl/BrandServiceImpl.java
package com.clickkart.product.serviceImpl;

import com.clickkart.product.dto.request.BrandRequest;
import com.clickkart.product.dto.response.BrandResponse;
import com.clickkart.product.entity.BrandEntity;
import com.clickkart.product.repository.BrandRepository;
import com.clickkart.product.service.BrandService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** See {@link BrandService} for why adding is idempotent. */
@Slf4j
@Service
@RequiredArgsConstructor
public class BrandServiceImpl implements BrandService {

    private static final String PUBLIC_ID_PREFIX = "BRD-";

    private final BrandRepository brandRepository;

    @Override
    @Transactional(readOnly = true)
    public List<BrandResponse> search(String term) {
        // Empty means "no filter", and the query relies on that: an empty term becomes like '%%',
        // which matches everything. Null would leave PostgreSQL unable to type the bind.
        String normalised = term == null || term.isBlank() ? "" : BrandEntity.normalise(term);
        return brandRepository.search(normalised)
                .stream()
                .map(BrandResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public BrandResponse addOrGet(String sellerPublicId, BrandRequest request) {
        String name = request.name().trim();
        String key = BrandEntity.normalise(name);

        var existing = brandRepository.findByNormalisedName(key);
        if (existing.isPresent()) {
            return BrandResponse.from(existing.get());
        }

        BrandEntity brand = BrandEntity.createdBy(PUBLIC_ID_PREFIX + UUID.randomUUID(), name, sellerPublicId);
        try {
            brandRepository.saveAndFlush(brand);
        } catch (DataIntegrityViolationException e) {
            // Two sellers adding the same new brand at the same moment. The unique index is the
            // authority; the loser reads the winner's row rather than failing a request that asked
            // for something that now exists.
            log.debug("Brand {} was created concurrently; returning the existing row", key);
            return brandRepository.findByNormalisedName(key)
                    .map(BrandResponse::from)
                    .orElseThrow(() -> e);
        }
        log.info("BRAND_CREATED name={} by={}", name, sellerPublicId);
        return BrandResponse.from(brand);
    }
}
