# Greengrocer Hardening Checklist

## Build & Run
- **Build/compile check (smoke test):**
  - `mvn -q -DskipTests package`
- **Run (JavaFX):**
  - `mvn -q javafx:run`

> If Maven dependency downloads are blocked by the environment, run the same commands in a local environment with network access.

## Database Configuration
Defaults (local MySQL):
- **URL:** `jdbc:mysql://localhost:3306/greengrocer?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC`
- **User:** `myuser`
- **Password:** `1234`

Override via **environment variables** (preferred) or JVM properties:
- `GREENGROCER_DB_URL` or `-Dgreengrocer.db.url=...`
- `GREENGROCER_DB_USER` or `-Dgreengrocer.db.user=...`
- `GREENGROCER_DB_PASSWORD` or `-Dgreengrocer.db.password=...`

Ensure MySQL is running and the `greengrocer` database exists before starting the app.

## Known Limitations
- JavaFX requires a graphical environment; headless environments will not display the UI.
- Maven builds require access to Maven Central unless dependencies are cached locally.

## Core QA Flows (Manual)
### Customer (`cust` / `cust`)
1. Log in as **cust**.
2. Add items to the cart from Fruits/Vegetables lists.
3. Open cart → apply coupon `SAVE10` when subtotal ≥ `200.00`.
4. Pick a delivery date/time within 48 hours.
5. Checkout and verify order ID.
6. Open **Orders** and try canceling within 30 minutes of placing the order.

### Carrier (`carr` / `carr`)
1. Log in as **carr**.
2. Claim one or more available orders.
3. Mark a claimed order as delivered.

### Owner (`own` / `own`)
1. Log in as **own**.
2. Review order status (Pending / In Delivery / Delivered / Canceled).
3. Refresh to ensure consistent status updates.

## Notes for Examiners
- Order cancellation is restricted to 30 minutes after placement and only when not assigned to a carrier.
- Delivery time is validated to be within 48 hours.
- Stock is enforced during checkout and when adding to cart.
