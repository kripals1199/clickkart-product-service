// src/test/java/com/clickkart/product/controller/ProductCatalogControllerFacetTest.java
package com.clickkart.product.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Guards the query-parameter binding on the catalogue search.
 *
 * <p>This exists because of a bug that every other test in this service was blind to. The search
 * method took {@code @RequestParam Map<String, List<String>>}, which compiles, binds, and then
 * throws {@code ClassCastException} on first read — Spring only ever puts one String per key into a
 * plain Map, and the {@code List} is erased. Every filtered request returned 500: text query,
 * brand, price and specification facets alike, because all of them pass through the same reader.
 * Only a search with no parameters at all worked.
 *
 * <p>The service-level tests missed it because they call {@code productService.search(...)}
 * directly, which is downstream of the binding that was wrong.
 */
class ProductCatalogControllerFacetTest {

    @Test
    void theSearchBindsParametersAsAMultiValueMap() throws Exception {
        Method search = findSearch();
        Parameter bound = null;
        for (Parameter parameter : search.getParameters()) {
            if (parameter.isAnnotationPresent(RequestParam.class)
                    && Map.class.isAssignableFrom(parameter.getType())) {
                bound = parameter;
            }
        }

        assertThat(bound).as("a Map-typed @RequestParam on search").isNotNull();
        // A plain Map here is the bug. Spring fills it with String values whatever the declared
        // generics say, and the failure only appears at runtime on the first request that has any
        // parameter at all.
        assertThat(MultiValueMap.class)
                .as("query parameters must bind as MultiValueMap, not a plain Map")
                .isAssignableFrom(bound.getType());
    }

    @Test
    void readsEveryValueOfARepeatedParameter() throws Exception {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("prop.RAM", "8");
        params.add("prop.RAM", "12");
        params.add("prop.Colour", "Black");
        params.add("query", "phone");
        params.add("prop.", "ignored");

        Map<String, List<String>> facets = invokeFacets(params);

        // Repeated values are one facet with two options - "8 or 12" - not the last one winning.
        assertThat(facets).containsOnlyKeys("RAM", "Colour");
        assertThat(facets.get("RAM")).containsExactly("8", "12");
        assertThat(facets.get("Colour")).containsExactly("Black");
    }

    @Test
    void ignoresParametersThatAreNotFacets() throws Exception {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("query", "phone");
        params.add("page", "0");
        params.add("size", "20");

        // The prefix is what stops a property called "page" from breaking paging.
        assertThat(invokeFacets(params)).isEmpty();
    }

    @Test
    void aSearchWithNoParametersIsNotAnError() throws Exception {
        assertThat(invokeFacets(new LinkedMultiValueMap<>())).isEmpty();
        assertThat(invokeFacets(null)).isEmpty();
    }

    private static Method findSearch() {
        for (Method method : ProductCatalogController.class.getDeclaredMethods()) {
            if (method.getName().equals("search")) {
                return method;
            }
        }
        throw new AssertionError("no search method on ProductCatalogController");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, List<String>> invokeFacets(MultiValueMap<String, String> params)
            throws Exception {
        Method facets = ProductCatalogController.class
                .getDeclaredMethod("propertyFacets", MultiValueMap.class);
        facets.setAccessible(true);
        return (Map<String, List<String>>) facets.invoke(null, params);
    }
}
