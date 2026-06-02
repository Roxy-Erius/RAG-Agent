# Virtual Payment System — Implementation Plan

> **Goal:** Add checkbox-based virtual payment to cart (select items → calculate total → fake pay → clear selected)

**Architecture:** Cart items get checkboxes, bottom bar shows total for checked items only. Backend accepts list of itemIds, sums their prices, deletes only those items.

**Tech Stack:** Spring Boot (Java) + Android Kotlin (ViewBinding, OkHttp)

---

### Task 1: Backend OrderController

**Files:**
- Create: `server/src/main/java/com/ragagent/controller/OrderController.java`
- Modify: `server/src/main/java/com/ragagent/repository/CartRepository.java` (add batch delete method)

- [ ] **Step 1: Add batch delete to CartRepository**

Add method to delete specific cart items by their IDs:

```java
public int deleteByIds(List<Long> ids, String sessionId) {
    if (ids.isEmpty()) return 0;
    String placeholders = ids.stream().map(id -> "?").collect(Collectors.joining(","));
    return jdbc.update("DELETE FROM cart_items WHERE id IN (" + placeholders + ") AND session_id = ?",
            Stream.concat(ids.stream(), Stream.of(sessionId)).toArray());
}

public int deleteByIdsForUser(List<Long> ids, Long userId) {
    if (ids.isEmpty()) return 0;
    String placeholders = ids.stream().map(id -> "?").collect(Collectors.joining(","));
    return jdbc.update("DELETE FROM cart_items WHERE id IN (" + placeholders + ") AND user_id = ?",
            Stream.concat(ids.stream(), Stream.of(userId)).toArray());
}
```

- [ ] **Step 2: Create OrderController**

```java
@RestController
@RequestMapping("/api/orders")
public class OrderController {
    // POST /api/orders/checkout
    // Body: {"sessionId":"xxx","itemIds":[1,3]}
    // Response: {"orderId":"uuid","total":156.0,"itemCount":2,"status":"paid","paidAt":"..."}
    // Logic: query cart items by ids → calculate total → sleep 1s → delete items → return result
}
```

- [ ] **Step 3: Commit** `git commit -m "feat: virtual payment endpoint with selective checkout"`

---

### Task 2: Frontend Checkbox + Payment UI

**Files:**
- Modify: `client/app/src/main/res/layout/item_cart_product.xml` (add CheckBox)
- Modify: `client/app/src/main/res/layout/activity_cart.xml` (add bottom bar with select-all + total + checkout)
- Modify: `client/app/src/main/java/com/ragagent/ui/CartAdapter.kt` (checkbox state, select-all callback)
- Modify: `client/app/src/main/java/com/ragagent/ui/CartActivity.kt` (checkout logic, total for checked items)
- Modify: `client/app/src/main/java/com/ragagent/network/ApiService.kt` (checkout method)

- [ ] **Step 1: Add CheckBox to item_cart_product.xml**

Add a CheckBox at the left of each cart item row (before the placeholder View). Use a simple android.widget.CheckBox with `android:id="@+id/cbSelect"`.

- [ ] **Step 2: Update activity_cart.xml bottom bar**

Add below the RecyclerView:
- CheckBox (select all) with text "全选"
- TextView for total of checked items
- "去结算" button

- [ ] **Step 3: Update CartAdapter**

Add:
- `checkedIds: MutableSet<Long>` to track checked state
- `onCheckedChange: () -> Unit` callback to notify activity of total changes
- CheckBox listener in bind()
- `getCheckedIds(): List<Long>`, `setAllChecked(checked: Boolean)`, `isAllChecked(): Boolean`

- [ ] **Step 4: Update CartActivity**

- Replace `updateTotal()` to only sum checked items
- Full-select CheckBox logic
- Checkout button: collect checked item IDs → call `apiService.checkout(sessionId, checkedIds)` → on success clear checked items from list
- Add confirmation dialog before checkout

- [ ] **Step 5: Add checkout to ApiService**

```kotlin
data class CheckoutResult(val orderId: String, val total: Double, val itemCount: Int, val status: String)

suspend fun checkout(sessionId: String, itemIds: List<Long>): CheckoutResult? = withContext(Dispatchers.IO) {
    // POST /api/orders/checkout with JSON body
}
```

- [ ] **Step 6: Commit and build verify** `git commit -m "feat: virtual payment with checkbox selection in cart"`

---
