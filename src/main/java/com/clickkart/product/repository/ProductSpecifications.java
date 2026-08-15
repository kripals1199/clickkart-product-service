// src/main/java/com/clickkart/product/repository/ProductSpecifications.java
package com.clickkart.product.repository;

import com.clickkart.product.entity.ProductEntity;
import com.clickkart.product.enums.ProductStatus;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/** Criteria for the public catalog search. */
public final class ProductSpecifications {

    private ProductSpecifications() {}

    /**
     * Every filter is optional; the ACTIVE constraint is not.
     *
     * <p>It is baked in here rather than left to the caller precisely because it is the one that
     * matters: forgetting a brand filter returns too many results, forgetting the status filter
     * publishes other people's unreviewed and rejected listings. Putting it in the specification
     * means no future call site can compose a search without it.
     */
    public static Specification<ProductEntity> publicSearch(
            String query, String categoryPublicId, String brand, BigDecimal minPrice, BigDecimal maxPrice) {
        return (root, criteriaQuery, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("status"), ProductStatus.ACTIVE));

            if (hasText(query)) {
                String pattern = "%" + escapeLikeWildcards(query.trim().toLowerCase()) + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("name")), pattern, '\\'),
                        builder.like(builder.lower(root.get("brand")), pattern, '\\'),
                        builder.like(builder.lower(root.get("description")), pattern, '\\')));
            }
            if (hasText(categoryPublicId)) {
                predicates.add(builder.equal(root.get("categoryPublicId"), categoryPublicId.trim()));
            }
            if (hasText(brand)) {
                predicates.add(builder.equal(builder.lower(root.get("brand")), brand.trim().toLowerCase()));
            }

            if (minPrice != null || maxPrice != null) {
                // Price lives on the variant, so a price filter has to reach through the join and
                // ask whether ANY variant qualifies - a product matches if one of its variants is in
                // range, which is what a shopper filtering by price means.
                var variants = root.join("variants");
                predicates.add(builder.isTrue(variants.get("active")));
                if (minPrice != null) {
                    predicates.add(builder.greaterThanOrEqualTo(variants.get("sellingPrice"), minPrice));
                }
                if (maxPrice != null) {
                    predicates.add(builder.lessThanOrEqualTo(variants.get("sellingPrice"), maxPrice));
                }
                // The join multiplies rows when several variants match, so without this a product
                // would appear once per matching variant in the result page.
                criteriaQuery.distinct(true);
            }

            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * Without escaping, a search for {@code %} matches everything and {@code _} matches any single
     * character - letting a shopper turn the search box into a full table scan with a
     * user-controlled pattern.
     */
    private static String escapeLikeWildcards(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
