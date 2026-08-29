package com.clickkart.product.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One answer a seller gave to one master-data property.
 *
 * <p>An element rather than an entity: these have no identity of their own, no lifecycle apart from
 * the product, and are never referenced from anywhere else. They are replaced wholesale when a
 * listing is saved.
 *
 * <p>A property may appear several times. "Connectivity = Wi-Fi, Bluetooth, NFC" is three of these,
 * not one comma-joined string — which is what keeps a filter on Bluetooth an indexed equality match
 * rather than a substring scan that also matches nothing useful.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Embeddable
public class ProductPropertyValue {

    /**
     * The stable machine identifier from Category Service.
     *
     * <p>Never the display label. The label is editable in the other service, and recording it here
     * would orphan every value the moment somebody fixed a typo in it.
     */
    @Column(name = "property_name", nullable = false, length = 80)
    private String propertyName;

    /**
     * The canonical stored form.
     *
     * <p>For a controlled property this is the allowed value's {@code value}, not its label — "8",
     * not "8 GB". The unit belongs to the property definition, so carrying it here would duplicate
     * it and make the stored value non-numeric.
     */
    @Column(name = "property_value", nullable = false, length = 500)
    private String propertyValue;

    /** Position within a multi-valued property, so the seller's chosen order survives a round trip. */
    @Column(name = "value_order", nullable = false)
    private int valueOrder;

    private ProductPropertyValue(String propertyName, String propertyValue, int valueOrder) {
        this.propertyName = propertyName;
        this.propertyValue = propertyValue;
        this.valueOrder = valueOrder;
    }

    public static ProductPropertyValue of(String propertyName, String propertyValue, int valueOrder) {
        return new ProductPropertyValue(propertyName, propertyValue, valueOrder);
    }

    /**
     * Value equality, because the collection is a set of answers rather than a list of rows.
     *
     * <p>Without this, replacing a product's properties with an identical set would look like a
     * complete change to the persistence provider and rewrite every row.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ProductPropertyValue that)) {
            return false;
        }
        return valueOrder == that.valueOrder
                && java.util.Objects.equals(propertyName, that.propertyName)
                && java.util.Objects.equals(propertyValue, that.propertyValue);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(propertyName, propertyValue, valueOrder);
    }
}
