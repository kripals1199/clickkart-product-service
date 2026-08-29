// src/main/java/com/clickkart/product/repository/ProductSpecifications.java
package com.clickkart.product.repository;

import com.clickkart.product.entity.ProductEntity;
import com.clickkart.product.enums.ProductStatus;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Map;
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
    /**
     * @param properties one entry per facet the shopper has chosen, keyed by the property name,
     *                   with the values accepted for it. Entries are ANDed and values within an
     *                   entry are ORed - "RAM is 8 or 12, AND Colour is Black" is what ticking two
     *                   RAM boxes and one colour means.
     */
    public static Specification<ProductEntity> publicSearch(
            String query, String categoryPublicId, String brand, BigDecimal minPrice, BigDecimal maxPrice,
            Map<String, List<String>> properties) {
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

            // Specification facets.
            //
            // One correlated EXISTS per chosen property, never a join per property and never one
            // join reused. A product answering RAM=8 AND Colour=Black holds two rows in
            // product_properties, and a single join cannot satisfy both conditions at once - the
            // same row would have to be two things - so it silently matches nothing. That failure
            // returns an empty page rather than an error, which is the worst kind.
            if (properties != null) {
                for (Map.Entry<String, List<String>> facet : properties.entrySet()) {
                    String propertyName = facet.getKey();
                    List<String> wanted = facet.getValue();
                    if (propertyName == null || propertyName.isBlank() || wanted == null || wanted.isEmpty()) {
                        continue;
                    }

                    Subquery<Long> sub = criteriaQuery.subquery(Long.class);
                    Root<ProductEntity> subProduct = sub.from(ProductEntity.class);
                    Join<Object, Object> values = subProduct.join("properties");
                    sub.select(builder.literal(1L))
                            .where(builder.and(
                                    builder.equal(subProduct.get("id"), root.get("id")),
                                    builder.equal(values.get("propertyName"), propertyName),
                                    values.get("propertyValue").in(wanted)));
                    predicates.add(builder.exists(sub));
                }
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
