# ClickKart Product Service

Seller listings, variants, and the moderation workflow between a seller submitting and a customer
seeing. Service #6 of the platform's 14, port **8087**.

Built after Category Service despite being numbered before it — a listing cannot be published
without confirming its category is real, active and a leaf, and that check is Category Service's to
answer.

---

## The moderation workflow

```
DRAFT ──submit──▶ PENDING_REVIEW ──approve──▶ ACTIVE ──archive──▶ ARCHIVED
  ▲                     │                        │                    │
  └───────reject────────┘                        └────────archive─────┘
```

Only `ACTIVE` is publicly visible. The states exist because a marketplace lets third parties
publish, and letting a seller put a listing straight in front of customers is how counterfeit and
mispriced goods reach a storefront.

Three rules the workflow depends on:

- **A listing under review is frozen against seller edits.** Otherwise a seller passes moderation
  with acceptable content and swaps it afterwards — the exact failure review exists to prevent, and
  the operator would have approved something that no longer matches what they read.
- **A rejection returns the listing to `DRAFT`, and requires a reason.** A terminal rejected state
  leaves the seller with nothing to do; a rejection with no reason leaves them guessing and turns
  one decision into a support ticket.
- **Operators cannot edit content.** They approve or reject. An operator who could quietly fix a
  description before approving would make the audit trail claim the seller published something they
  never wrote.

---

## Two cross-service checks, both at submit

This is the first service that calls two others to validate a write, and *when* it calls them is a
deliberate choice.

| Check | Service | Question |
|---|---|---|
| Seller is verified | User Service | Has an operator confirmed this business? |
| Category is assignable | Category Service | Real, active, and a leaf? |

Neither runs at draft time. Drafting against a category being reorganised, or before verification
completes, is reasonable — a seller should be able to prepare while an operator reviews their GSTIN.
Publishing before either is settled is not. Neither runs on reads either: two network calls in the
path of every catalog page, to re-answer a question that was decisive at submit.

**The `ROLE_SELLER` claim is not sufficient on its own.** It says the platform granted someone the
seller role, not that anyone checked their business. Only User Service knows the verification
status.

Both are **required dependencies** for submission — degrading to "assume it is fine" would put a
listing on sale against a category nobody confirmed exists. The blast radius is bounded, though: a
seller can still create and edit drafts while either service is down.

A 404 from User Service is treated as a definitive "no seller profile", not an outage. Translating
it into a 503 would tell the seller to retry something that cannot start working.

---

## API

| Method | Path | Access |
|---|---|---|
| `GET` | `/api/v1/products/search` | public — query, category, brand, price filters |
| `GET` | `/api/v1/products/slug/{slug}` | public |
| `GET` | `/api/v1/products/{publicId}` | public |
| `GET` | `/api/v1/products/seller` | **SELLER** — own listings, any state |
| `POST` | `/api/v1/products/seller` | **SELLER** — create a draft |
| `PUT` | `/api/v1/products/seller/{publicId}` | **SELLER** — edit a draft |
| `PUT` | `/api/v1/products/seller/{publicId}/submission` | **SELLER** — submit for review |
| `PUT` | `/api/v1/products/seller/{publicId}/archive` | **SELLER** — withdraw |
| `GET` | `/api/v1/products/admin/review-queue` | **ADMIN** |
| `PUT` | `/api/v1/products/admin/{publicId}/review` | **ADMIN** — approve or reject |

### Internal API

| Method | Path | Caller |
|---|---|---|
| `GET` | `/internal/v1/products/variants/{sku}` | Cart — is this purchasable, at what price? |
| `GET` | `/internal/v1/products/{publicId}` | Order — resolve regardless of status |

Shared-secret authenticated, no Gateway route, excluded from the published spec. It exists
separately from the public catalog because these callers must see listings the public API hides: an
order placed last month has to still render what was bought after the seller archived it, and a
public endpoint returning only `ACTIVE` would make order history decay as sellers tidy their
catalogs.

`variants/{sku}` returns a **verdict** rather than 404-ing, so Cart can distinguish "no such SKU"
from "not on sale". It carries the price so the caller can **snapshot** it — a cart re-reading the
live price would silently change what the customer agreed to between adding and paying. The reason
string is deliberately vague about *why* a listing is not live, so a competitor cannot learn from
this endpoint that a rival's product is sitting in review.

---

## Design decisions

**Every product has at least one variant**, even with no real options. The alternative is for Cart,
Order and Inventory each to handle "sometimes the product is the purchasable unit and sometimes a
variant is". One always-present level costs a row and removes that branch from three services.

**Money is `BigDecimal` from the wire down**, never `double`. A JSON number bound to a double has
already lost precision before validation runs, so switching at the entity alone would be too late.
Stored as `numeric(12,2)`, normalised on the way in so the price a seller sees back is the one they
submitted.

**Selling price may not exceed MRP.** MRP is the figure a discount is advertised *from*; selling
above it renders a negative discount and, in India, misstates a legally meaningful number.

**SKUs are globally unique and uppercased.** Inventory keys stock on the SKU alone and a warehouse
operator scans it off a label with no idea which seller it belongs to — two sellers sharing one, or
differing only in case, makes both of those ambiguous.

**Cross-service references are ids, not foreign keys.** Separate databases, reachable only by their
own roles. A category deactivated later does *not* retroactively pull live products off the shelf —
that would be a surprising blast radius for hiding a section.

**Nothing here tracks stock.** That is Inventory Service (#8), keyed by SKU. A quantity here as well
would create two answers to "can I buy this", and the wrong one would be the one the catalog renders.

**Another seller's listing 404s rather than 403s**, so this API cannot be used to enumerate a
competitor's unpublished catalog. `ProductResponse` has two factories for the same reason —
the customer view omits moderation fields entirely, rather than relying on each call site to null
them out.

---

## Running it

Needs Config Server, Eureka, Audit Log, **Category Service**, **User Service**, Postgres and the
shared revocation Redis.

```bash
docker compose -f docker-compose.dev-infra.yml -f docker-compose.app-tier.yml up -d product-service
```

This service holds **three** distinct internal-API secrets: one guarding its own `/internal/**`, and
one each for calling Category's and User's. That is what per-service keying buys — a compromise here
yields only what this service was entitled to reach, rather than every internal surface.

### Tests

```bash
mvn verify
```

`verify`, not `test` — that enforces the coverage gate (floor 0.50 against measured 0.54).

> **Building on a JDK newer than 21:** the pom declares Lombok as an explicit annotation processor
> path. JDK 23 turned off the implicit classpath discovery that JDK 21 only warns about.

---

## Configuration

| Property | Purpose |
|---|---|
| `product.jwt-secret` | Shared HMAC secret; must match Auth Service and the Gateway |
| `product.internal-api-key` | Guards this service's own `/internal/**` |
| `product.category-service-api-key` | Presented when calling Category Service |
| `product.user-service-api-key` | Presented when calling User Service |
| `product.allowed-origins` | CORS allow-list |

Readiness covers `readinessState,db,redis`.
