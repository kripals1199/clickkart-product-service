// src/test/java/com/clickkart/product/serviceImpl/ProductServiceImplPostgresIT.java
package com.clickkart.product.serviceImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.clickkart.product.config.ProductProperties;
import com.clickkart.product.dto.request.AftersalesRequest;
import com.clickkart.product.dto.request.ProductRequest;
import com.clickkart.product.dto.request.SeoRequest;
import com.clickkart.product.dto.request.ShippingRequest;
import com.clickkart.product.dto.request.VariantRequest;
import com.clickkart.product.dto.response.ProductResponse;
import com.clickkart.product.entity.AuditLogEntryEntity;
import com.clickkart.product.entity.BrandEntity;
import com.clickkart.product.entity.ProductEntity;
import com.clickkart.product.entity.ProductMediaEntity;
import com.clickkart.product.entity.ProductOfferEntity;
import com.clickkart.product.entity.ProductVariantEntity;
import com.clickkart.product.enums.AuditOutcome;
import com.clickkart.product.enums.DeliveryOption;
import com.clickkart.product.enums.ProductAuditAction;
import com.clickkart.product.enums.ProductType;
import com.clickkart.product.enums.WarrantyType;
import com.clickkart.product.feign.CategoryServiceClient;
import com.clickkart.product.feign.CategoryValidationApiResponse;
import com.clickkart.product.feign.SellerProfileApiResponse;
import com.clickkart.product.feign.UserServiceClient;
import com.clickkart.product.repository.ProductPriceHistoryRepository;
import com.clickkart.product.repository.ProductRepository;
import com.clickkart.product.repository.ProductVariantRepository;
import com.clickkart.product.service.AuditTrailService;
import com.clickkart.product.service.ChainIntegrityReport;
import com.clickkart.product.web.RequestMetadata;
import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.Metamodel;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.Map;
import org.hibernate.cfg.ManagedBeanSettings;
import org.hibernate.resource.beans.container.spi.BeanContainer;
import org.hibernate.resource.beans.container.spi.ContainedBean;
import org.hibernate.resource.beans.spi.BeanInstanceProducer;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.auditing.AuditingHandler;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.data.mapping.context.PersistentEntities;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;

/**
 * Drives {@link ProductServiceImpl} against the real PostgreSQL schema.
 *
 * <p><strong>Why this exists.</strong> Every other test in this service mocks the repository, so
 * they assert what the service *asked* for, never what a database actually stored. That gap has
 * already produced two defects nothing else caught: three tables missing the audit columns
 * {@code BaseEntity} maps, and a {@code :term is null} clause PostgreSQL cannot plan. Both compiled,
 * both passed every unit test, and both failed on first contact with Postgres.
 *
 * <p>This deliberately stops below HTTP. There is no controller, no security filter and no token -
 * so it proves the service layer persists what it claims, and proves nothing at all about
 * authentication. The controller and the upload endpoint still need an authenticated browser.
 *
 * <p><strong>Everything rolls back.</strong> Each test runs in a transaction that is abandoned, and
 * the assertions read through that same transaction, so the database is left exactly as found.
 *
 * <p>Skipped rather than failed when Postgres is unreachable, so a machine without the dev database
 * still gets a green build. That is a real trade: a skipped test protects nothing, which is why the
 * skip reason is printed rather than swallowed.
 */
class ProductServiceImplPostgresIT {

    private static final String URL = System.getProperty(
            "it.db.url", "jdbc:postgresql://localhost:5432/clickkart_product");
    private static final String USER = System.getProperty("it.db.username", "postgres");
    private static final String PASSWORD = System.getProperty("it.db.password", "");

    private static final String SELLER = "USR-it-seller";
    private static final String CATEGORY = "CAT-it";
    private static final String CORRELATION = "it-corr";
    private static final RequestMetadata METADATA = new RequestMetadata("127.0.0.1", "junit");

    /**
     * Spring Boot hands Hibernate a bean container so {@code @EntityListeners} resolve as beans;
     * without one the listener is built by its no-arg constructor, its handler stays null, and
     * every audit column silently arrives as NULL. That is not a hypothetical: the first run of
     * this test failed on {@code created_by} violating NOT NULL, which is precisely the shape of
     * defect this class exists to catch. Wiring the real listener keeps the write path honest.
     */
    private static final AuditingEntityListener AUDIT_LISTENER = new AuditingEntityListener();

    private static boolean reachable;
    private static EntityManagerFactory factory;

    private EntityManager em;
    private ProductServiceImpl service;

    @BeforeAll
    static void bootstrap() {
        reachable = probe();
        if (!reachable) {
            System.out.println("[ProductServiceImplPostgresIT] skipped: " + URL + " not reachable");
            return;
        }
        Configuration configuration = new Configuration()
                .addAnnotatedClass(ProductEntity.class)
                .addAnnotatedClass(ProductVariantEntity.class)
                .addAnnotatedClass(ProductMediaEntity.class)
                .addAnnotatedClass(ProductOfferEntity.class)
                .addAnnotatedClass(BrandEntity.class)
                .addAnnotatedClass(com.clickkart.product.entity.ProductPriceHistoryEntity.class)
                .setProperty("hibernate.connection.driver_class", "org.postgresql.Driver")
                .setProperty("hibernate.connection.url", URL)
                .setProperty("hibernate.connection.username", USER)
                .setProperty("hibernate.connection.password", PASSWORD)
                // validate, never create: the schema under test is the one Flyway built. Letting
                // Hibernate generate it would test the entities against themselves.
                .setProperty("hibernate.hbm2ddl.auto", "validate")
                .setProperty("hibernate.show_sql", System.getProperty("it.show.sql", "false"));
        configuration.getProperties().put(ManagedBeanSettings.BEAN_CONTAINER, listenerContainer());
        factory = configuration.buildSessionFactory();
        armAuditListener();
    }

    /** Returns our one listener for the listener type, and defers everything else to Hibernate. */
    private static BeanContainer listenerContainer() {
        return new BeanContainer() {
            @Override
            public <B> ContainedBean<B> getBean(Class<B> type, LifecycleOptions options,
                    BeanInstanceProducer fallback) {
                return contained(type, AuditingEntityListener.class.equals(type)
                        ? type.cast(AUDIT_LISTENER)
                        : fallback.produceBeanInstance(type));
            }

            @Override
            public <B> ContainedBean<B> getBean(String name, Class<B> type,
                    LifecycleOptions options, BeanInstanceProducer fallback) {
                return contained(type, AuditingEntityListener.class.equals(type)
                        ? type.cast(AUDIT_LISTENER)
                        : fallback.produceBeanInstance(name, type));
            }

            @Override
            public void stop() { }
        };
    }

    private static <B> ContainedBean<B> contained(Class<B> type, B instance) {
        return new ContainedBean<>() {
            @Override
            public Class<B> getBeanClass() {
                return type;
            }

            @Override
            public B getBeanInstance() {
                return instance;
            }
        };
    }

    /**
     * Gives the listener the same handler Spring builds, reading the metamodel back out of the
     * factory we just built. The auditor is fixed rather than read from a SecurityContext: this
     * test asserts the columns are populated, not who populated them.
     */
    private static void armAuditListener() {
        Metamodel metamodel = factory.getMetamodel();
        AuditingHandler handler = new AuditingHandler(
                PersistentEntities.of(new JpaMetamodelMappingContext(Set.of(metamodel))));
        handler.setAuditorAware(() -> Optional.of(SELLER));
        handler.afterPropertiesSet();
        AUDIT_LISTENER.setAuditingHandler(() -> handler);
    }

    private static boolean probe() {
        try (Connection c = DriverManager.getConnection(URL, USER, PASSWORD)) {
            return c.isValid(3);
        } catch (Exception e) {
            return false;
        }
    }

    @AfterAll
    static void shutdown() {
        if (factory != null) {
            factory.close();
        }
    }

    @BeforeEach
    void setUp() {
        assumeTrue(reachable, "PostgreSQL not reachable at " + URL);
        em = factory.createEntityManager();
        em.getTransaction().begin();

        JpaRepositoryFactory repositories = new JpaRepositoryFactory(em);
        ProductRepository products = repositories.getRepository(ProductRepository.class);
        ProductVariantRepository variants = repositories.getRepository(ProductVariantRepository.class);

        ProductProperties properties = new ProductProperties();
        properties.setCategoryServiceApiKey("it");
        properties.setUserServiceApiKey("it");

        // The real recorder, not a stub: it writes to a table this test can read back, which is
        // the only way to see that a price change is recorded and an unchanged price is not.
        ProductPriceHistoryRepository priceHistory =
                repositories.getRepository(ProductPriceHistoryRepository.class);

        service = new ProductServiceImpl(
                products, variants, categoryAlwaysAssignable(), sellerAlwaysVerified(),
                auditThatRecordsNothing(), new PriceHistoryRecorder(priceHistory, properties),
                properties);
    }

    @AfterEach
    void tearDown() {
        if (em != null) {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            em.close();
        }
    }

    /* ---- what a full save actually puts in the database ---------------------------------- */

    @Test
    void everyWorkspaceSectionReachesItsColumn() {
        ProductResponse created = service.createDraft(SELLER, full(), CORRELATION, METADATA);
        em.flush();

        Map<String, Object> row = readProductRow(created.publicId());

        // Read back through SQL, not through the entity: an entity round trip would return the
        // object still in the persistence context and prove nothing about what was written.
        assertThat(row.get("short_description")).isEqualTo("A short line");
        assertThat(row.get("product_type")).isEqualTo("PHYSICAL");
        assertThat(((BigDecimal) row.get("tax_rate_percent"))).isEqualByComparingTo("18.00");
        assertThat(row.get("price_includes_tax")).isEqualTo(true);
        assertThat(row.get("weight_grams")).isEqualTo(450);
        assertThat(row.get("package_type")).isEqualTo("Box");
        assertThat(row.get("shipping_class")).isEqualTo("Fragile");
        assertThat(row.get("free_shipping")).isEqualTo(true);
        assertThat(row.get("return_window_days")).isEqualTo(7);
        assertThat(row.get("warranty_type")).isEqualTo("MANUFACTURER");
        assertThat(row.get("warranty_months")).isEqualTo(12);
        assertThat(row.get("seo_title")).isEqualTo("Buy the IT Widget");
        assertThat(row.get("meta_description")).isEqualTo("A widget for integration testing.");
        assertThat(row.get("last_edited_at")).isNotNull();
    }

    @Test
    void keywordsLandInTheirOwnTableWithoutDuplicates() {
        ProductResponse created = service.createDraft(
                SELLER,
                request(ProductType.PHYSICAL, null, null,
                        new SeoRequest("t", "d", List.of("Phone", " phone ", "PHONE", "5G"))),
                CORRELATION, METADATA);
        em.flush();

        List<String> stored = readStrings(
                "select keyword from product_keywords where product_id ="
                        + " (select id from products where public_id = ?)", created.publicId());

        // The unique constraint would reject the second spelling outright, so this is the service's
        // de-duplication working rather than the database rescuing it.
        assertThat(stored).containsExactlyInAnyOrder("Phone", "5G");
    }

    @Test
    void deliveryOptionsLandInTheirOwnTable() {
        ProductResponse created = service.createDraft(
                SELLER,
                request(ProductType.PHYSICAL,
                        new ShippingRequest(450, 160, 78, 8, "Box", "Fragile", false,
                                List.of(DeliveryOption.STANDARD, DeliveryOption.EXPRESS)),
                        null, null),
                CORRELATION, METADATA);
        em.flush();

        assertThat(readStrings(
                "select delivery_option from product_delivery_options where product_id ="
                        + " (select id from products where public_id = ?)", created.publicId()))
                .containsExactlyInAnyOrder("STANDARD", "EXPRESS");
    }

    @Test
    void aPhysicalProductWithNoStatedSpeedStillShipsSomehow() {
        ProductResponse created = service.createDraft(
                SELLER, request(ProductType.PHYSICAL, null, null, null), CORRELATION, METADATA);
        em.flush();

        // An empty set would read as "cannot be delivered at all", which is not a state a physical
        // product can be in - and the check constraint would not catch it.
        assertThat(readStrings(
                "select delivery_option from product_delivery_options where product_id ="
                        + " (select id from products where public_id = ?)", created.publicId()))
                .containsExactly("STANDARD");
    }

    @Test
    void specificationValuesLandOneRowPerValue() {
        ProductRequest request = new ProductRequest(
                "IT Widget", null, "desc", "Acme", CATEGORY,
                List.of(variant("IT-SKU-1")),
                Map.of("Connectivity", List.of("Wi-Fi", "Bluetooth", "NFC")),
                null, null, null, null, null, null, null);

        ProductResponse created = service.createDraft(SELLER, request, CORRELATION, METADATA);
        em.flush();

        // A row each, not one comma-joined string - which is what makes a facet an indexed equality
        // match rather than a substring scan.
        assertThat(readStrings(
                "select property_value from product_properties where product_id ="
                        + " (select id from products where public_id = ?)", created.publicId()))
                .containsExactlyInAnyOrder("Wi-Fi", "Bluetooth", "NFC");
    }

    @Test
    void costPriceIsStoredAgainstTheVariant() {
        ProductRequest request = new ProductRequest(
                "IT Widget", null, "desc", "Acme", CATEGORY,
                List.of(new VariantRequest("IT-SKU-COST", "Blue", new BigDecimal("100.00"),
                        new BigDecimal("90.00"), Map.of(), new BigDecimal("55.00"))),
                Map.of(), null, null, null, null, null, null, null);

        ProductResponse created = service.createDraft(SELLER, request, CORRELATION, METADATA);
        em.flush();

        List<String> cost = readStrings(
                "select cost_price::text from product_variants where product_id ="
                        + " (select id from products where public_id = ?)", created.publicId());
        assertThat(cost).hasSize(1);
        assertThat(new BigDecimal(cost.get(0))).isEqualByComparingTo("55.00");
    }

    @Test
    void switchingToDigitalClearsTheShippingColumns() {
        ProductResponse created = service.createDraft(
                SELLER,
                request(ProductType.DIGITAL,
                        new ShippingRequest(450, 160, 78, 8, "Box", "Fragile", true,
                                List.of(DeliveryOption.EXPRESS)),
                        null, null),
                CORRELATION, METADATA);
        em.flush();

        Map<String, Object> row = readProductRow(created.publicId());

        // Stale dimensions on a digital product would quote a delivery date for something that is
        // never posted.
        assertThat(row.get("weight_grams")).isNull();
        assertThat(row.get("length_mm")).isNull();
        assertThat(row.get("package_type")).isNull();
        assertThat(row.get("free_shipping")).isEqualTo(false);
        assertThat(readStrings(
                "select delivery_option from product_delivery_options where product_id ="
                        + " (select id from products where public_id = ?)", created.publicId()))
                .isEmpty();
    }

    @Test
    void aClearedSectionIsClearedInTheDatabaseRatherThanLeftBehind() {
        ProductResponse created = service.createDraft(SELLER, full(), CORRELATION, METADATA);
        em.flush();
        assertThat(readProductRow(created.publicId()).get("seo_title")).isNotNull();

        // Save again with the SEO section absent, exactly as the form sends it once cleared.
        service.updateOwnProduct(
                SELLER, created.publicId(),
                request(ProductType.PHYSICAL,
                        new ShippingRequest(450, 160, 78, 8, "Box", "Fragile", true, null),
                        new AftersalesRequest(7, WarrantyType.MANUFACTURER, 12), null),
                CORRELATION, METADATA);
        em.flush();
        Map<String, Object> row = readProductRow(created.publicId());
        // Wholesale, not merged: a merge would silently keep a title the seller deleted.
        assertThat(row.get("seo_title")).isNull();
        assertThat(row.get("meta_description")).isNull();
        assertThat(readStrings(
                "select keyword from product_keywords where product_id ="
                        + " (select id from products where public_id = ?)", created.publicId()))
                .isEmpty();
    }

    @Test
    void editingASpecificationReplacesTheStoredValuesRatherThanAddingToThem() {
        ProductResponse created = service.createDraft(SELLER, specs(Map.of(
                "Connectivity", List.of("Wi-Fi", "Bluetooth"),
                "Colour", List.of("Blue"))), CORRELATION, METADATA);
        em.flush();

        // The seller drops Bluetooth, keeps Wi-Fi, adds NFC, and clears Colour entirely.
        service.updateOwnProduct(SELLER, created.publicId(),
                specs(Map.of("Connectivity", List.of("Wi-Fi", "NFC"))), CORRELATION, METADATA);
        em.flush();

        // Every one of these fails if the write path merges instead of flushing: merge
        // re-snapshots the element collection and the removed rows quietly survive the edit.
        assertThat(readStrings(
                "select property_value from product_properties where product_id ="
                        + " (select id from products where public_id = ?)", created.publicId()))
                .containsExactlyInAnyOrder("Wi-Fi", "NFC");

        // Now the seller moves the product to a category that asks for none of this, and the form
        // stops sending the section at all.
        //
        // This one holds even on the old write path: measured, clearing this collection survived
        // the redundant merge while clearing the keyword list did not, and the difference is
        // Hibernate internals rather than anything either caller does. So read this as coverage of
        // the edit path, not as the guard on that bug - the guard is the keyword test below.
        service.updateOwnProduct(SELLER, created.publicId(), specs(Map.of()), CORRELATION, METADATA);
        em.flush();

        assertThat(readStrings(
                "select property_value from product_properties where product_id ="
                        + " (select id from products where public_id = ?)", created.publicId()))
                .isEmpty();
    }

    @Test
    void unpickingADeliveryOptionRemovesItsRow() {
        ProductResponse created = service.createDraft(
                SELLER,
                request(ProductType.PHYSICAL,
                        new ShippingRequest(450, 160, 78, 8, "Box", "Fragile", false,
                                List.of(DeliveryOption.STANDARD, DeliveryOption.EXPRESS)),
                        null, null),
                CORRELATION, METADATA);
        em.flush();

        service.updateOwnProduct(SELLER, created.publicId(),
                request(ProductType.PHYSICAL,
                        new ShippingRequest(450, 160, 78, 8, "Box", "Fragile", false,
                                List.of(DeliveryOption.STANDARD)),
                        null, null),
                CORRELATION, METADATA);
        em.flush();

        assertThat(readStrings(
                "select delivery_option from product_delivery_options where product_id ="
                        + " (select id from products where public_id = ?)", created.publicId()))
                .containsExactly("STANDARD");
    }

    /* ---- helpers ------------------------------------------------------------------------- */

    private ProductRequest full() {
        return request(ProductType.PHYSICAL,
                new ShippingRequest(450, 160, 78, 8, "Box", "Fragile", true, null),
                new AftersalesRequest(7, WarrantyType.MANUFACTURER, 12),
                new SeoRequest("Buy the IT Widget", "A widget for integration testing.", List.of("widget")));
    }

    private ProductRequest specs(Map<String, List<String>> properties) {
        return new ProductRequest(
                "IT Widget", null, "desc", "Acme", CATEGORY,
                List.of(variant("IT-SKU-" + System.nanoTime())), properties,
                null, null, null, null, null, null, null);
    }

    private ProductRequest request(
            ProductType type, ShippingRequest shipping, AftersalesRequest aftersales, SeoRequest seo) {
        return new ProductRequest(
                "IT Widget", null, "desc", "Acme", CATEGORY,
                List.of(variant("IT-SKU-" + System.nanoTime())), Map.of(),
                "A short line", type, new BigDecimal("18.00"), true, shipping, aftersales, seo);
    }

    private VariantRequest variant(String sku) {
        return new VariantRequest(sku, "Blue / M", new BigDecimal("100.00"),
                new BigDecimal("90.00"), Map.of("colour", "Blue"), null);
    }

    /** Reads the row through the transaction's own connection, so uncommitted work is visible. */
    private Map<String, Object> readProductRow(String publicId) {
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        em.unwrap(org.hibernate.Session.class).doWork(connection -> {
            try (PreparedStatement p = connection.prepareStatement(
                    "select * from products where public_id = ?")) {
                p.setString(1, publicId);
                try (ResultSet rs = p.executeQuery()) {
                    assertThat(rs.next()).as("product row for " + publicId).isTrue();
                    var meta = rs.getMetaData();
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        out.put(meta.getColumnName(i), rs.getObject(i));
                    }
                }
            }
        });
        return out;
    }

    private List<String> readStrings(String sql, String publicId) {
        List<String> out = new java.util.ArrayList<>();
        em.unwrap(org.hibernate.Session.class).doWork(connection -> {
            try (PreparedStatement p = connection.prepareStatement(sql)) {
                p.setString(1, publicId);
                try (ResultSet rs = p.executeQuery()) {
                    while (rs.next()) {
                        out.add(rs.getString(1));
                    }
                }
            }
        });
        return out;
    }

    /* Hand-written stubs rather than Mockito: these have one behaviour each and no verification. */

    private static UserServiceClient sellerAlwaysVerified() {
        // Two methods now that reviews resolve a byline, so this cannot be a lambda.
        return new UserServiceClient() {
            @Override
            public SellerProfileApiResponse getSellerProfile(
                    String sellerPublicId, String correlationId, String apiKey) {
                return new SellerProfileApiResponse(
                        true, new SellerProfileApiResponse.Data(SELLER, "IT Traders", "VERIFIED"));
            }

            @Override
            public com.clickkart.product.feign.UserProfileApiResponse getProfile(
                    String userPublicId, String correlationId, String apiKey) {
                throw new UnsupportedOperationException("not exercised by this test");
            }
        };
    }

    private static CategoryServiceClient categoryAlwaysAssignable() {
        return (publicId, correlationId, apiKey) -> new CategoryValidationApiResponse(
                true, new CategoryValidationApiResponse.Data(CATEGORY, true, true, true, true, null));
    }

    /**
     * AuditTrailService has several methods, so it cannot be a lambda. Only record() is reached
     * from the write paths under test; the rest throw so a future caller cannot pass silently.
     */
    private static AuditTrailService auditThatRecordsNothing() {
        return new AuditTrailService() {
            @Override
            public void record(String correlationId, String actor, ProductAuditAction action,
                    RequestMetadata metadata, String detail) { }

            @Override
            public void record(String correlationId, String actor, ProductAuditAction action,
                    AuditOutcome outcome, RequestMetadata metadata, String detail) { }

            @Override
            public ChainIntegrityReport verifyChainIntegrity() {
                throw new UnsupportedOperationException("not exercised by this test");
            }

            @Override
            public Page<AuditLogEntryEntity> browse(Pageable pageable) {
                throw new UnsupportedOperationException("not exercised by this test");
            }
        };
    }
}
