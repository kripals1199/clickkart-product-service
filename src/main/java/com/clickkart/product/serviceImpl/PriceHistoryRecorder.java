// src/main/java/com/clickkart/product/serviceImpl/PriceHistoryRecorder.java
package com.clickkart.product.serviceImpl;

import com.clickkart.product.config.ProductProperties;
import com.clickkart.product.entity.ProductEntity;
import com.clickkart.product.entity.ProductPriceHistoryEntity;
import com.clickkart.product.entity.ProductVariantEntity;
import com.clickkart.product.repository.ProductPriceHistoryRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Keeps the price history, and answers what a drop is worth.
 *
 * <p><strong>Only changes are written.</strong> Every save on a listing passes through here, and
 * most of them do not touch a price. Writing a row per save would make "the lowest this has been"
 * a question about how often somebody opened the form.
 *
 * <p><strong>A first sighting is recorded but is not a drop.</strong> The first price seen for a
 * SKU is the baseline; a product has not fallen in price simply because it now has one.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PriceHistoryRecorder {

    private final ProductPriceHistoryRepository priceHistoryRepository;
    private final ProductProperties productProperties;

    /**
     * Records every variant whose price differs from the last one seen.
     *
     * <p>Called after the product's variants are in their final state, so the SKUs it reads are the
     * ones that will be stored.
     */
    public void record(ProductEntity product, Instant at) {
        for (ProductVariantEntity variant : product.getVariants()) {
            BigDecimal current = variant.getSellingPrice();
            if (current == null) {
                continue;
            }

            Optional<ProductPriceHistoryEntity> last = product.getId() == null
                    ? Optional.empty()
                    : priceHistoryRepository.findTopByProductIdAndSkuOrderByRecordedAtDesc(
                            product.getId(), variant.getSku());

            // compareTo, not equals: BigDecimal("100") and BigDecimal("100.00") are not equal, and
            // a scale change is not a price change.
            if (last.isPresent() && last.get().getSellingPrice().compareTo(current) == 0) {
                continue;
            }

            priceHistoryRepository.save(
                    ProductPriceHistoryEntity.of(product, variant.getSku(), current, at));
        }
    }

    /**
     * How far this SKU's price has fallen from its high inside the window, as a percentage.
     *
     * <p>Null rather than zero when there is no drop: "this has not fallen" and "this has fallen by
     * nothing" would render identically otherwise, and only one of them deserves a badge.
     *
     * <p>Rounded down. A 9.6% drop shown as 10% is a claim about someone's money that is not quite
     * true, and the direction of the error should never favour the shop.
     */
    public Integer dropPercent(String productPublicId, String sku, BigDecimal currentPrice) {
        if (currentPrice == null || currentPrice.signum() <= 0) {
            return null;
        }

        Instant since = Instant.now().minus(productProperties.getPriceDropWindowDays(), ChronoUnit.DAYS);
        BigDecimal highest = priceHistoryRepository.highestSince(productPublicId, sku, since);
        if (highest == null || highest.compareTo(currentPrice) <= 0) {
            return null;
        }

        int percent = highest
                .subtract(currentPrice)
                .multiply(BigDecimal.valueOf(100))
                .divide(highest, 0, java.math.RoundingMode.DOWN)
                .intValue();

        // A drop the shopper cannot see the point of is noise on a listing tile.
        return percent < productProperties.getMinPriceDropPercent() ? null : percent;
    }
}
