// src/main/java/com/clickkart/product/repository/ProductReviewRepository.java
package com.clickkart.product.repository;

import com.clickkart.product.entity.ProductReviewEntity;
import com.clickkart.product.enums.ReviewStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductReviewRepository extends JpaRepository<ProductReviewEntity, Long> {

    Optional<ProductReviewEntity> findByPublicId(String publicId);

    Page<ProductReviewEntity> findByProductPublicIdAndStatusOrderByCreatedDateDesc(
            String productPublicId, ReviewStatus status, Pageable pageable);

    Optional<ProductReviewEntity> findByProductPublicIdAndAuthorPublicId(
            String productPublicId, String authorPublicId);

    /**
     * The aggregate, read straight from the rows rather than accumulated.
     *
     * <p>Returns the average and the count in one pass so the two can never disagree, and counts
     * only PUBLISHED rows - a hidden review must stop affecting the score the moment it is hidden.
     *
     * <p>{@code avg} comes back null when there are no published reviews, which is exactly the
     * value the product should hold: no rating, rather than a rating of zero.
     */
    @Query("""
            select avg(r.rating) as average, count(r) as total
              from ProductReviewEntity r
             where r.product.publicId = :productPublicId
               and r.status = com.clickkart.product.enums.ReviewStatus.PUBLISHED
            """)
    RatingSummary summarise(@Param("productPublicId") String productPublicId);

    /** Aliased projection rather than Object[]: a two-column row is ambiguous to unpack by index. */
    interface RatingSummary {
        Double getAverage();

        long getTotal();
    }

    /** The distribution behind the stars: how many gave 5, how many 4, and so on. */
    @Query("""
            select r.rating, count(r)
              from ProductReviewEntity r
             where r.product.publicId = :productPublicId
               and r.status = com.clickkart.product.enums.ReviewStatus.PUBLISHED
             group by r.rating
            """)
    java.util.List<Object[]> ratingBreakdown(@Param("productPublicId") String productPublicId);

    Page<ProductReviewEntity> findByAuthorPublicIdOrderByCreatedDateDesc(
            String authorPublicId, Pageable pageable);
}
