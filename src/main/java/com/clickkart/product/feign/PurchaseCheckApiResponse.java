// src/main/java/com/clickkart/product/feign/PurchaseCheckApiResponse.java
package com.clickkart.product.feign;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** One boolean, unwrapped from the standard envelope. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PurchaseCheckApiResponse(boolean success, Boolean data) {

    public boolean purchased() {
        return Boolean.TRUE.equals(data);
    }
}
