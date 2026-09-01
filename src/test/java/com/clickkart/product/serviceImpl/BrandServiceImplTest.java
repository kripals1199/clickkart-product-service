// src/test/java/com/clickkart/product/serviceImpl/BrandServiceImplTest.java
package com.clickkart.product.serviceImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.clickkart.product.dto.request.BrandRequest;
import com.clickkart.product.dto.response.BrandResponse;
import com.clickkart.product.entity.BrandEntity;
import com.clickkart.product.repository.BrandRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * The point of this table is that two spellings of one brand cannot both exist, so most of these
 * are about collision rather than about storage.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BrandServiceImplTest {

    private static final String SELLER = "USR-seller";

    @Mock private BrandRepository brandRepository;

    private BrandServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new BrandServiceImpl(brandRepository);
        when(brandRepository.saveAndFlush(any(BrandEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(brandRepository.findByNormalisedName(anyString())).thenReturn(Optional.empty());
    }

    @Test
    void foldsEverySpellingOfOneBrandOntoOneKey() {
        // The whole reason the column exists: these are one brand, not four.
        assertThat(BrandEntity.normalise("Samsung")).isEqualTo("samsung");
        assertThat(BrandEntity.normalise("SAMSUNG")).isEqualTo("samsung");
        assertThat(BrandEntity.normalise("Sam sung")).isEqualTo("samsung");
        assertThat(BrandEntity.normalise("  samsung  ")).isEqualTo("samsung");
    }

    @Test
    void foldsAccentsSoTwoIdenticalLookingBrandsCannotBothExist() {
        // Otherwise the filter list shows two entries a customer cannot tell apart, each matching a
        // different half of the products.
        assertThat(BrandEntity.normalise("Loréal")).isEqualTo(BrandEntity.normalise("Loreal"));
    }

    @Test
    void keepsTheCapitalisationTheOwnerUsesForDisplay() {
        BrandResponse created = service.addOrGet(SELLER, new BrandRequest("  iPhone  "));

        // Normalisation is for comparison only. Displaying the folded key would rename every brand
        // in the catalogue to lowercase.
        assertThat(created.name()).isEqualTo("iPhone");
    }

    @Test
    void handsBackTheExistingBrandRatherThanRefusingAVariantSpelling() {
        BrandEntity existing = BrandEntity.createdBy("BRD-1", "Samsung", "USR-other");
        when(brandRepository.findByNormalisedName("samsung")).thenReturn(Optional.of(existing));

        BrandResponse result = service.addOrGet(SELLER, new BrandRequest("SAMSUNG"));

        // Refusing would teach the seller to type a variant that gets through, which is precisely
        // the outcome this table exists to prevent.
        assertThat(result.name()).isEqualTo("Samsung");
        verify(brandRepository, never()).saveAndFlush(any(BrandEntity.class));
    }

    @Test
    void recordsWhoInventedABrand() {
        service.addOrGet(SELLER, new BrandRequest("Menon Electronics"));

        var captor = org.mockito.ArgumentCaptor.forClass(BrandEntity.class);
        verify(brandRepository).saveAndFlush(captor.capture());

        // A brand a seller invented and one an operator curated need different treatment when
        // somebody later has to decide whether it is real.
        assertThat(captor.getValue().isSellerCreated()).isTrue();
        assertThat(captor.getValue().getCreatedBySeller()).isEqualTo(SELLER);
    }

    @Test
    void losesGracefullyWhenTwoSellersAddTheSameBrandAtOnce() {
        BrandEntity winner = BrandEntity.createdBy("BRD-1", "Samsung", "USR-other");
        when(brandRepository.saveAndFlush(any(BrandEntity.class)))
                .thenThrow(new DataIntegrityViolationException("uq_brands_normalised"));
        when(brandRepository.findByNormalisedName("samsung"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));

        BrandResponse result = service.addOrGet(SELLER, new BrandRequest("Samsung"));

        // The unique index is the authority. The loser reads the winner's row rather than failing a
        // request that asked for something which now exists.
        assertThat(result.publicId()).isEqualTo("BRD-1");
    }

    @Test
    void searchesOnTheFoldedKeySoAMisspellingStillFindsTheBrand() {
        when(brandRepository.search("samsung")).thenReturn(List.of(
                BrandEntity.createdBy("BRD-1", "Samsung", SELLER)));

        assertThat(service.search("Sam Sung")).hasSize(1);
        verify(brandRepository).search("samsung");
    }

    @Test
    void aPunctuationOnlyTermFiltersNothing() {
        service.search("!!!");

        // Its folded key is empty, which the query turns into like '%%' and matches everything.
        verify(brandRepository).search("");
    }

    @Test
    void anEmptyTermReturnsTheWholeList() {
        service.search("  ");
        verify(brandRepository).search("");
    }

    @Test
    void neverPassesNullToTheQuery() {
        // PostgreSQL cannot infer the type of a null bind here: it guesses bytea and the statement
        // fails with "operator does not exist: character varying ~~ bytea". Every path must send a
        // string. This one is worth stating outright, because a mocked repository will happily
        // accept the null that a real database rejects - which is exactly how it shipped.
        for (String term : new String[] {null, "", "   ", "!!!", "Samsung"}) {
            service.search(term);
        }
        verify(brandRepository, never()).search(null);
    }
}
