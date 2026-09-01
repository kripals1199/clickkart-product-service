// src/test/java/com/clickkart/product/serviceImpl/PriceHistoryRecorderTest.java
package com.clickkart.product.serviceImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.clickkart.product.config.ProductProperties;
import com.clickkart.product.entity.ProductEntity;
import com.clickkart.product.entity.ProductPriceHistoryEntity;
import com.clickkart.product.entity.ProductVariantEntity;
import com.clickkart.product.repository.ProductPriceHistoryRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PriceHistoryRecorderTest {

    private static final String PRODUCT = "PRD-1";
    private static final String SKU = "SKU-1";

    @Mock private ProductPriceHistoryRepository priceHistoryRepository;

    private ProductEntity product;
    private PriceHistoryRecorder recorder;

    @BeforeEach
    void setUp() {
        ProductProperties properties = new ProductProperties();
        properties.setPriceDropWindowDays(30);
        properties.setMinPriceDropPercent(5);
        recorder = new PriceHistoryRecorder(priceHistoryRepository, properties);

        product = ProductEntity.createFor(PRODUCT, "USR-seller");
        // A saved product: the recorder skips the lookup entirely for one with no id yet.
        ReflectionTestUtils.setField(product, "id", 1L);
        product.addVariant(variant(SKU, "100.00"));
    }

    private static ProductVariantEntity variant(String sku, String price) {
        ProductVariantEntity variant = ProductVariantEntity.createWithSku(sku);
        variant.update("Default", new BigDecimal("200.00"), new BigDecimal(price), null, null);
        return variant;
    }

    /* ---- what gets written ---- */

    @Test
    void aFirstSightingIsRecorded() {
        when(priceHistoryRepository.findTopByProductIdAndSkuOrderByRecordedAtDesc(1L, SKU))
                .thenReturn(Optional.empty());

        recorder.record(product, Instant.now());

        verify(priceHistoryRepository).save(any(ProductPriceHistoryEntity.class));
    }

    @Test
    void anUnchangedPriceWritesNothing() {
        when(priceHistoryRepository.findTopByProductIdAndSkuOrderByRecordedAtDesc(1L, SKU))
                .thenReturn(Optional.of(ProductPriceHistoryEntity.of(
                        product, SKU, new BigDecimal("100.00"), Instant.now())));

        recorder.record(product, Instant.now());

        // Every save on a listing passes through here, and most do not touch a price. A row per
        // save would make "the lowest this has been" a question about how often someone opened
        // the form.
        verify(priceHistoryRepository, never()).save(any());
    }

    @Test
    void aScaleChangeIsNotAPriceChange() {
        when(priceHistoryRepository.findTopByProductIdAndSkuOrderByRecordedAtDesc(1L, SKU))
                .thenReturn(Optional.of(ProductPriceHistoryEntity.of(
                        product, SKU, new BigDecimal("100"), Instant.now())));

        recorder.record(product, Instant.now());

        // BigDecimal("100") does not equal BigDecimal("100.00"), but it is the same money.
        verify(priceHistoryRepository, never()).save(any());
    }

    @Test
    void arealPriceChangeIsRecorded() {
        when(priceHistoryRepository.findTopByProductIdAndSkuOrderByRecordedAtDesc(1L, SKU))
                .thenReturn(Optional.of(ProductPriceHistoryEntity.of(
                        product, SKU, new BigDecimal("120.00"), Instant.now())));

        recorder.record(product, Instant.now());

        verify(priceHistoryRepository).save(any(ProductPriceHistoryEntity.class));
    }

    /* ---- what the drop is worth ---- */

    @Test
    void aDropIsMeasuredAgainstTheHighInTheWindow() {
        when(priceHistoryRepository.highestSince(eq(PRODUCT), eq(SKU), any()))
                .thenReturn(new BigDecimal("200.00"));

        assertThat(recorder.dropPercent(PRODUCT, SKU, new BigDecimal("150.00"))).isEqualTo(25);
    }

    @Test
    void aDropIsRoundedDownSoTheClaimIsNeverGenerous() {
        when(priceHistoryRepository.highestSince(eq(PRODUCT), eq(SKU), any()))
                .thenReturn(new BigDecimal("100.00"));

        // 9.6% shown as 10% is a claim about someone's money that is not quite true, and the
        // direction of the error should never favour the shop.
        assertThat(recorder.dropPercent(PRODUCT, SKU, new BigDecimal("90.40"))).isEqualTo(9);
    }

    @Test
    void noHistoryMeansNoDropRatherThanADropOfZero() {
        when(priceHistoryRepository.highestSince(anyString(), anyString(), any())).thenReturn(null);

        // "Has not fallen" and "fell by nothing" would render identically as 0, and only one of
        // them deserves a badge.
        assertThat(recorder.dropPercent(PRODUCT, SKU, new BigDecimal("100.00"))).isNull();
    }

    @Test
    void aPriceThatWentUpIsNotADrop() {
        when(priceHistoryRepository.highestSince(eq(PRODUCT), eq(SKU), any()))
                .thenReturn(new BigDecimal("80.00"));

        assertThat(recorder.dropPercent(PRODUCT, SKU, new BigDecimal("100.00"))).isNull();
    }

    @Test
    void aTrivialDropIsNotWorthABadge() {
        when(priceHistoryRepository.highestSince(eq(PRODUCT), eq(SKU), any()))
                .thenReturn(new BigDecimal("100.00"));

        // 2% is noise on a listing tile rather than a reason to look.
        assertThat(recorder.dropPercent(PRODUCT, SKU, new BigDecimal("98.00"))).isNull();
        // 5% is the configured floor, and is shown.
        assertThat(recorder.dropPercent(PRODUCT, SKU, new BigDecimal("95.00"))).isEqualTo(5);
    }

    @Test
    void aFreeOrNegativePriceIsNotDividedBy() {
        assertThat(recorder.dropPercent(PRODUCT, SKU, BigDecimal.ZERO)).isNull();
        assertThat(recorder.dropPercent(PRODUCT, SKU, null)).isNull();
    }
}
