package vn.codegym.smartcafe.customeractions;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import vn.codegym.smartcafe.sales.SalesService;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.datasource.url=${TEST_DB_URL}","spring.datasource.username=${TEST_DB_USERNAME}",
        "spring.datasource.password=${TEST_DB_PASSWORD:}"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = "jdbc:mysql:.*")
class CustomerActionsMySqlTest {
    private static final long ID = 93001;
    private static final String TOKEN = "Q".repeat(43);
    private static final String AUTH = "Bearer " + TOKEN;
    private static final Instant NOW = Instant.parse("2026-11-13T00:00:00Z");
    private static final String BASE = "/api/v1/customer/sessions/93001";
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired CustomerOrderService orders;
    @Autowired CustomerOrderEditService edits;
    @Autowired CustomerRequestService requests;
    @Autowired SalesService sales;
    @TestConfiguration
    static class Time {
        @Bean @Primary Clock customerClock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
    }

    @BeforeEach
    void fixture() {
        assertThat(jdbc.queryForObject("SELECT DATABASE()",String.class)).endsWith("_test");
        jdbc.update("INSERT INTO users(id,username,password_hash,full_name,email,role,status) VALUES(93001,'guest-fixture-staff','test-only','Nhân viên test','staff@example.invalid','STAFF','ACTIVE')");
        jdbc.update("INSERT INTO cafe_tables(id,code,name,qr_public_id) VALUES(93001,'GUEST-T1','Bàn khách test',UUID())");
        jdbc.update("INSERT INTO table_sessions(id,table_id,session_code,opened_by_user_id,started_at) VALUES(93001,93001,'GUEST-SESSION-1',93001,?)",Timestamp.from(NOW.minusSeconds(300)));
        jdbc.update("INSERT INTO session_clients(id,session_id,access_token_hash,joined_at,expires_at) VALUES(93001,93001,?,?,?)",CustomerAccess.hash(TOKEN),Timestamp.from(NOW.minusSeconds(60)),Timestamp.from(NOW.plusSeconds(3600)));
        jdbc.update("INSERT INTO menu_categories(id,code,name) VALUES(93001,'GUEST-CAT','Nhóm test')");
        jdbc.update("INSERT INTO menu_items(id,category_id,code,name,price) VALUES(93001,93001,'GUEST-ITEM','Cà phê snapshot',25000),(93002,93001,'GUEST-OFF','Món khác',10000)");
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM bill_items WHERE session_id=93001");
        jdbc.update("DELETE FROM bills WHERE session_id=93001");
        jdbc.update("DELETE FROM service_requests WHERE session_id=93001");
        jdbc.update("DELETE FROM order_items WHERE session_id=93001");
        jdbc.update("DELETE FROM order_batches WHERE session_id=93001");
        jdbc.update("DELETE FROM session_clients WHERE session_id=93001");
        jdbc.update("DELETE FROM table_sessions WHERE id=93001");
        jdbc.update("DELETE FROM cafe_tables WHERE id=93001");
        jdbc.update("DELETE FROM menu_items WHERE id IN (93001,93002)");
        jdbc.update("DELETE FROM menu_categories WHERE id=93001");
        jdbc.update("DELETE FROM users WHERE id=93001");
    }

    private CustomerOrderController.Command command(int quantity) {
        return new CustomerOrderController.Command(List.of(new CustomerOrderController.Line(ID,BigDecimal.valueOf(quantity))));
    }
    private CustomerOrderService.Order order() { return orders.submit(ID,AUTH,"order-test",command(2)); }

    @Test void orderUsesDatabasePricesSnapshotsAndSafeReplay() {
        var first = order();
        assertThat(first.totalAmount()).isEqualTo("50000");
        assertThat(first.items().getFirst().unitPrice()).isEqualTo("25000");
        assertThat(first.editableUntil()).isEqualTo(NOW.plusSeconds(180).toString());
        jdbc.update("UPDATE menu_items SET price=99000,name='Giá mới' WHERE id=93001");
        assertThat(orders.submit(ID,AUTH,"order-test",command(2))).isEqualTo(first);
        assertThatThrownBy(()->orders.submit(ID,AUTH,"order-test",command(3))).isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_batches WHERE session_id=93001",Integer.class)).isEqualTo(1);
    }

    @Test void invalidPayloadEmptyFractionAndForgedPrices() throws Exception {
        for (String body : List.of("{\"items\":[]}","{\"items\":[{\"menuItemId\":93001,\"quantity\":0}]}","{\"items\":[{\"menuItemId\":93001,\"quantity\":1.5}]}"))
            mvc.perform(post(BASE+"/orders").with(csrf()).header("Authorization",AUTH).header("Idempotency-Key","invalid").contentType("application/json").content(body)).andExpect(status().isBadRequest());
        mvc.perform(post(BASE+"/orders").with(csrf()).header("Authorization",AUTH).header("Idempotency-Key","forged-price").contentType("application/json")
                .content("{\"items\":[{\"menuItemId\":93001,\"quantity\":2,\"unitPrice\":1,\"name\":\"Giả\"}],\"totalAmount\":2}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalAmount").value("50000"));
    }

    @Test void missingExpiredRevokedAndCrossSessionCredentialsCannotWrite() throws Exception {
        mvc.perform(post(BASE+"/waiter-requests").with(csrf()).header("Idempotency-Key","missing")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/customer/sessions/93002/waiter-requests").with(csrf()).header("Authorization",AUTH).header("Idempotency-Key","cross")).andExpect(status().isUnauthorized());
        jdbc.update("UPDATE session_clients SET expires_at=? WHERE id=93001",Timestamp.from(NOW));
        assertThatThrownBy(()->requests.submit(ID,AUTH,"expired","WAITER")).hasMessageContaining("401");
        jdbc.update("UPDATE session_clients SET expires_at=?,revoked_at=? WHERE id=93001",Timestamp.from(NOW.plusSeconds(3600)),Timestamp.from(NOW));
        assertThatThrownBy(()->requests.submit(ID,AUTH,"revoked","WAITER")).hasMessageContaining("401");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM service_requests WHERE session_id=93001",Integer.class)).isZero();
    }

    @Test void inactiveAndCheckoutTablesRejectNewOrdersAndMissingCsrfIsForbidden() throws Exception {
        jdbc.update("UPDATE cafe_tables SET status='OUT_OF_SERVICE' WHERE id=93001");
        assertThatThrownBy(this::order).hasMessageContaining("409");
        jdbc.update("UPDATE cafe_tables SET status='ACTIVE' WHERE id=93001");
        jdbc.update("UPDATE table_sessions SET status='CHECKOUT' WHERE id=93001");
        assertThatThrownBy(this::order).hasMessageContaining("409");
        mvc.perform(post(BASE+"/waiter-requests").header("Authorization",AUTH).header("Idempotency-Key","csrf")).andExpect(status().isForbidden());
    }

    @Test void unavailableLineRollsBackWholeOrderAndDuplicateLinesAreRejected() {
        jdbc.update("UPDATE menu_items SET available=0 WHERE id=93002");
        var lines = new CustomerOrderController.Command(List.of(new CustomerOrderController.Line(ID,BigDecimal.ONE),new CustomerOrderController.Line(93002L,BigDecimal.ONE)));
        assertThatThrownBy(()->orders.submit(ID,AUTH,"mixed",lines)).hasMessageContaining("409");
        assertThatThrownBy(()->orders.submit(ID,AUTH,"duplicate",new CustomerOrderController.Command(List.of(command(1).items().getFirst(),command(1).items().getFirst())))).hasMessageContaining("400");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_batches WHERE session_id=93001",Integer.class)).isZero();
    }

    @Test void parallelSameKeyCreatesOneOrderAndParallelWaiterCreatesOneActiveRequest() throws Exception {
        try (var pool = Executors.newFixedThreadPool(2)) {
            var gate = new CountDownLatch(1);
            Callable<String> send = ()->{gate.await(); return orders.submit(ID,AUTH,"parallel",command(1)).id();};
            var a = pool.submit(send); var b = pool.submit(send); gate.countDown();
            assertThat(a.get(10,TimeUnit.SECONDS)).isEqualTo(b.get(10,TimeUnit.SECONDS));
            var waiterGate = new CountDownLatch(1);
            Callable<Integer> waiter = () -> {
                waiterGate.await();
                try {
                    requests.submit(ID, AUTH, java.util.UUID.randomUUID().toString(), "WAITER");
                    return 200;
                } catch (ResponseStatusException error) {
                    return error.getStatusCode().value();
                }
            };
            var c = pool.submit(waiter);
            var d = pool.submit(waiter);
            waiterGate.countDown();
            assertThat(List.of(c.get(10, TimeUnit.SECONDS), d.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_batches WHERE session_id=93001",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM service_requests WHERE session_id=93001",Integer.class)).isEqualTo(1);
    }

    @Test void editsUseSnapshotPriceVersionAndOwnerThenCancelPreservesHistory() {
        var first=order(); long item=Long.parseLong(first.items().getFirst().id());
        jdbc.update("UPDATE menu_items SET price=99000 WHERE id=93001");
        assertThat(edits.edit(ID,item,AUTH,0,3).totalAmount()).isEqualTo("75000");
        assertThatThrownBy(()->edits.edit(ID,item,AUTH,0,4)).hasMessageContaining("409");
        assertThat(edits.edit(ID,item,AUTH,1,null).totalAmount()).isEqualTo("0");
        assertThat(jdbc.queryForObject("SELECT status FROM order_items WHERE id=?",String.class,item)).isEqualTo("CANCELLED");
    }

    @Test void editDeadlineBoundaryAndPreparationAreEnforcedAtServer() {
        var first=order(); long item=Long.parseLong(first.items().getFirst().id());
        jdbc.update("UPDATE order_items SET status='PREPARING',preparation_started_at=? WHERE id=?",Timestamp.from(NOW),item);
        assertThatThrownBy(()->edits.edit(ID,item,AUTH,0,3)).hasMessageContaining("409");
        jdbc.update("UPDATE order_items SET status='SUBMITTED',preparation_started_at=NULL WHERE id=?",item);
        jdbc.update("UPDATE order_batches SET submitted_at=?,editable_until=? WHERE id=?",Timestamp.from(NOW.minusSeconds(180)),Timestamp.from(NOW),Long.parseLong(first.id()));
        assertThatThrownBy(()->edits.edit(ID,item,AUTH,0,null)).hasMessageContaining("409");
    }

    @Test void otherDeviceCannotEditAnOrderItDidNotSubmit() {
        var first=order();
        String other="R".repeat(43);
        jdbc.update("INSERT INTO session_clients(session_id,access_token_hash,joined_at,expires_at) VALUES(93001,?,?,?)",CustomerAccess.hash(other),Timestamp.from(NOW.minusSeconds(60)),Timestamp.from(NOW.plusSeconds(3600)));
        assertThatThrownBy(()->edits.edit(ID,Long.parseLong(first.items().getFirst().id()),"Bearer "+other,0,3)).hasMessageContaining("404");
    }

    @Test void waiterAndBillUseExistingConsumersAndDoNotIssueInvoices() throws Exception {
        assertThatThrownBy(()->requests.submit(ID,AUTH,"empty-bill","BILL")).hasMessageContaining("400");
        order();
        var waiter=requests.submit(ID,AUTH,"waiter","WAITER");
        assertThat(requests.submit(ID,AUTH,"waiter","WAITER")).isEqualTo(waiter);
        assertThatThrownBy(()->requests.submit(ID,AUTH,"waiter","BILL")).hasMessageContaining("409");
        requests.submit(ID,AUTH,"bill","BILL");
        mvc.perform(get("/api/v1/home/notifications").with(user("guest-fixture-staff").roles("STAFF"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.type == 'WAITER')]").isNotEmpty()).andExpect(jsonPath("$.items[?(@.type == 'ORDER')]").isNotEmpty());
        mvc.perform(get("/api/v1/sales/tables/93001/services").with(user("guest-fixture-staff").roles("STAFF"))).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bills WHERE session_id=93001",Integer.class)).isZero();
    }

    @Test void rejectedDuplicateKeyRemainsAvailableButAcceptedKeyIsBoundToType() throws Exception {
        order();
        requests.submit(ID, AUTH, "key-a", "WAITER");
        mvc.perform(post(BASE + "/waiter-requests").with(csrf()).header("Authorization", AUTH)
                        .header("Idempotency-Key", "key-b"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Đã có yêu cầu cùng loại đang mở. Mã gửi lần này chưa được chấp nhận hoặc lưu."));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM service_requests WHERE session_id=? AND idempotency_key=?",
                Integer.class, ID, "key-b")).isZero();

        mvc.perform(post(BASE + "/bill-requests").with(csrf()).header("Authorization", AUTH)
                        .header("Idempotency-Key", "key-a"))
                .andExpect(status().isConflict());
        mvc.perform(post(BASE + "/bill-requests").with(csrf()).header("Authorization", AUTH)
                        .header("Idempotency-Key", "key-b"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("BILL"));
        assertThatThrownBy(() -> requests.submit(ID, AUTH, "key-b", "WAITER"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
    }

    @Test void retryAfterResolutionReturnsOriginalAndDoesNotReserveRejectedKey() {
        var original = requests.submit(ID, AUTH, "original", "WAITER");
        assertThatThrownBy(() -> requests.submit(ID, AUTH, "new-key", "WAITER"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
        jdbc.update("""
                UPDATE service_requests SET status='RESOLVED',resolved_at=?,resolved_by_user_id=?
                WHERE id=? AND session_id=?
                """, Timestamp.from(NOW), ID, Long.parseLong(original.id()), ID);

        var replay = requests.submit(ID, AUTH, "original", "WAITER");
        assertThat(replay.id()).isEqualTo(original.id());
        assertThat(replay.status()).isEqualTo("RESOLVED");
        assertThatThrownBy(() -> requests.submit(ID, AUTH, "original", "BILL"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM service_requests WHERE session_id=?", Integer.class, ID))
                .isEqualTo(1);
        var next = requests.submit(ID, AUTH, "new-key", "WAITER");
        assertThat(next.id()).isNotEqualTo(original.id());
        assertThat(next.status()).isEqualTo("PENDING");
    }

    @Test void creatingBillLocksOutFurtherNewOrdersAndEdits() {
        var first=order();
        sales.createBill(ID,new SalesService.BillCommand("GUEST-SESSION-1","bill-fixture"),"guest-fixture-staff");
        assertThatThrownBy(()->orders.submit(ID,AUTH,"late-order",command(1))).hasMessageContaining("409");
        assertThatThrownBy(()->edits.edit(ID,Long.parseLong(first.items().getFirst().id()),AUTH,0,3)).hasMessageContaining("409");
    }
}
