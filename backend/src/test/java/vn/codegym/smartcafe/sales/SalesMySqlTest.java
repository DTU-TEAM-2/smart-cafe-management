package vn.codegym.smartcafe.sales;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import vn.codegym.smartcafe.billing.BillReader;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=${TEST_DB_URL}", "spring.datasource.username=${TEST_DB_USERNAME}",
        "spring.datasource.password=${TEST_DB_PASSWORD:}", "smartcafe.auth.token-secret=dt268-test-only-signing-key-at-least-32-characters"
})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="TEST_DB_URL", matches="jdbc:mysql:.*")
@Transactional
class SalesMySqlTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired SalesService sales;
    @Autowired BillReader reader;
    @Autowired CheckoutService checkout;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;

    @TestConfiguration
    static class FixedClock {
        @Bean @Primary Clock testClock() { return Clock.fixed(Instant.parse("2026-11-11T12:00:00Z"), ZoneOffset.UTC); }
    }

    @BeforeEach
    void fixture() {
        assertThat(jdbc.queryForObject("SELECT DATABASE()",String.class)).endsWith("_test");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users",Integer.class)).isZero();
        jdbc.update("""
                INSERT INTO users(id,username,password_hash,full_name,email,role,status)
                VALUES(1,'staff','test-only','Nhân viên kiểm thử','staff@example.invalid','STAFF','ACTIVE'),
                (2,'admin','test-only','Quản trị kiểm thử','admin@example.invalid','ADMIN','ACTIVE')
                """);
        jdbc.update("INSERT INTO cafe_tables(id,code,table_number,name,qr_public_id) VALUES(1,'T01',1,'Bàn gốc',UUID()),(2,'T02',2,'Bàn trống',UUID())");
        jdbc.update("INSERT INTO cafe_tables(id,code,name,status,qr_public_id) VALUES(3,'T03','Bàn bảo trì','OUT_OF_SERVICE',UUID())");
        jdbc.update("INSERT INTO table_sessions(id,table_id,session_code,opened_by_user_id,started_at) VALUES(1,1,'SESSION-OLD',1,'2026-11-10 00:00:00')");
        jdbc.update("INSERT INTO menu_categories(id,code,name) VALUES(1,'CAFE','Cà phê')");
        jdbc.update("INSERT INTO menu_items(id,category_id,code,name,price) VALUES(1,1,'COFFEE','Tên menu hiện tại',99000)");
        jdbc.update("""
                INSERT INTO order_batches(id,order_code,session_id,submitted_by_user_id,submitted_at,editable_until,edit_policy,idempotency_key,request_hash)
                VALUES(1,'ORDER-1',1,1,'2026-11-10 00:01:00','2026-11-10 00:01:00','DEADLINE_ONLY','order-key',REPEAT('a',64))
                """);
        jdbc.update("""
                INSERT INTO order_items(id,session_id,order_batch_id,menu_item_id,item_code,item_name,unit_price,quantity)
                VALUES(1,1,1,1,'COFFEE','Cà phê sữa snapshot',25000,2)
                """);
    }

    @Test void occupancyDoesNotConfuseActiveWithOccupiedAndKeepsMaintenance() throws Exception {
        mvc.perform(get("/api/v1/sales/tables").with(user("staff").roles("STAFF")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].occupancy").value("OCCUPIED"))
                .andExpect(jsonPath("$[1].occupancy").value("EMPTY"))
                .andExpect(jsonPath("$[1].operationalStatus").value("ACTIVE"))
                .andExpect(jsonPath("$[2].operationalStatus").value("OUT_OF_SERVICE"));
    }

    @Test void servicesUseOrderSnapshotsExcludeCancelledAndRefreshNewItems() throws Exception {
        jdbc.update("INSERT INTO menu_items(id,category_id,code,name,price) VALUES(2,1,'OTHER','Món khác',80000)");
        jdbc.update("""
                INSERT INTO order_items(session_id,order_batch_id,menu_item_id,item_code,item_name,unit_price,quantity,status,cancelled_at,cancellation_reason)
                VALUES(1,1,2,'CANCEL','Món hủy',80000,9,'CANCELLED',CURRENT_TIMESTAMP,'fixture')
                """);
        assertThat(sales.services(1).totalAmount()).isEqualTo("50000");
        jdbc.update("UPDATE order_items SET quantity=3 WHERE id=1");
        mvc.perform(get("/api/v1/sales/tables/1/services").with(user("staff").roles("STAFF")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalAmount").value("75000"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].itemName").value("Cà phê sữa snapshot"));
        assertThat(sales.services(2).totalAmount()).isEqualTo("0");
    }

    @Test void billIsAtomicPendingAndImmutableAcrossRetryAndMenuTableRename() {
        var first=sales.createBill(1,new SalesService.BillCommand("SESSION-OLD","bill-key"),"staff");
        assertThat(first.totalAmount()).isEqualTo("50000");
        assertThat(first.status()).isEqualTo("PENDING");
        assertThat(first.paidAt()).isNull();
        assertThat(first.items()).hasSize(1);
        jdbc.update("UPDATE cafe_tables SET name='Đổi tên',code='RENAMED' WHERE id=1");
        jdbc.update("UPDATE menu_items SET name='Đổi món',price=123000 WHERE id=1");
        jdbc.update("UPDATE users SET full_name='Đổi tên nhân viên' WHERE id=1");
        assertThat(sales.createBill(1,new SalesService.BillCommand("SESSION-OLD","bill-key"),"staff")).isEqualTo(first);
        assertThat(reader.get(Long.parseLong(first.id()))).isEqualTo(first);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bills",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payments",Integer.class)).isZero();
    }

    @Test void rejectsSecondCurrentBillAndStaleOrMissingSession() {
        sales.createBill(1,new SalesService.BillCommand("SESSION-OLD","one"),"staff");
        assertThatThrownBy(()->sales.createBill(1,new SalesService.BillCommand("SESSION-OLD","two"),"staff")).hasMessageContaining("409");
        assertThatThrownBy(()->sales.createBill(1,new SalesService.BillCommand("NEW-SESSION","three"),"staff")).hasMessageContaining("409");
        assertThatThrownBy(()->sales.createBill(1,new SalesService.BillCommand(null,"four"),"staff")).hasMessageContaining("400");
    }

    @Test void supportsFullLengthSessionCodeWithOpaqueBoundedKey() {
        String session="S".repeat(64);
        jdbc.update("UPDATE table_sessions SET session_code=? WHERE id=1",session);
        assertThat(sales.createBill(1,new SalesService.BillCommand(session,"K".repeat(64)),"staff").sessionCode()).isEqualTo(session);
    }

    @Test void rejectsOversizeKeyBeforeSqlAndEmptyBill() {
        assertThatThrownBy(()->sales.createBill(1,new SalesService.BillCommand("SESSION-OLD","K".repeat(65)),"staff")).hasMessageContaining("400");
        jdbc.update("DELETE FROM order_items");
        assertThatThrownBy(()->sales.createBill(1,new SalesService.BillCommand("SESSION-OLD","empty"),"staff")).hasMessageContaining("No Service to Pay");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bills",Integer.class)).isZero();
    }

    @Test void billResolvesOnlyItsSessionBillRequest() {
        jdbc.update("INSERT INTO session_clients(id,session_id,access_token_hash,expires_at) VALUES(1,1,REPEAT('f',64),DATE_ADD(CURRENT_TIMESTAMP,INTERVAL 1 DAY))");
        jdbc.update("""
                INSERT INTO service_requests(session_id,requested_by_client_id,request_type,idempotency_key)
                VALUES(1,1,'BILL','bill-request'),(1,1,'WAITER','waiter-request')
                """);
        sales.createBill(1,new SalesService.BillCommand("SESSION-OLD","key"),"staff");
        assertThat(jdbc.queryForObject("SELECT status FROM service_requests WHERE request_type='BILL'",String.class)).isEqualTo("RESOLVED");
        assertThat(jdbc.queryForObject("SELECT status FROM service_requests WHERE request_type='WAITER'",String.class)).isEqualTo("PENDING");
    }

    @Test void permissionAndCsrfAreRequiredForMutation() throws Exception {
        String body="{\"expectedSessionCode\":\"SESSION-OLD\",\"idempotencyKey\":\"http-key\"}";
        mvc.perform(get("/api/v1/sales/tables")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/income").with(user("guest").roles("CUSTOMER"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/sales/tables/1/bill").with(user("staff").roles("STAFF")).contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/sales/tables/1/bill").with(user("guest").roles("CUSTOMER")).with(csrf()).contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/sales/tables/1/bill").with(user("staff").roles("STAFF")).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.creatorName").value("Nhân viên kiểm thử"));
        mvc.perform(get("/api/v1/sales/csrf").with(user("staff").roles("STAFF")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test void realBearerConsumerWorksAndRevocationIsRespected() throws Exception {
        String payload=Base64.getUrlEncoder().withoutPadding().encodeToString(("1:staff:STAFF:"+Instant.now().plusSeconds(600).toEpochMilli()).getBytes(StandardCharsets.UTF_8));
        Mac mac=Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("dt268-test-only-signing-key-at-least-32-characters".getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
        String token="Bearer "+payload+"."+HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        mvc.perform(get("/api/v1/sales/tables").header("Authorization",token)).andExpect(status().isOk());
        jdbc.update("UPDATE users SET auth_version=1 WHERE id=1");
        mvc.perform(get("/api/v1/sales/tables").header("Authorization",token)).andExpect(status().isForbidden());
    }

    @Test void incomeUsesPaidUtcBoundaryCountsAverageAndZeroFilledDays() throws Exception {
        var bill=sales.createBill(1,new SalesService.BillCommand("SESSION-OLD","income"),"staff");
        mvc.perform(get("/api/v1/income").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalAmount").value("0"));
        jdbc.update("UPDATE bills SET status='PAID',created_at='2026-11-10 00:00:00',paid_at='2026-11-10 17:00:00' WHERE id=?",bill.id());
        mvc.perform(get("/api/v1/income").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalAmount").value("50000"))
                .andExpect(jsonPath("$.billCount").value(1)).andExpect(jsonPath("$.averageBillAmount").value("50000"));
        jdbc.update("UPDATE bills SET paid_at='2026-11-11 17:00:00' WHERE id=?",bill.id());
        mvc.perform(get("/api/v1/income").with(user("admin").roles("ADMIN")))
                .andExpect(jsonPath("$.billCount").value(0));
        mvc.perform(get("/api/v1/income").param("period","in_week").with(user("admin").roles("ADMIN")))
                .andExpect(jsonPath("$.dailyRevenue.length()").value(7)).andExpect(jsonPath("$.totalAmount").value("50000"));
    }

    @Test void incomeRejectsInvalidFiltersAndDoesNotExposeSql() throws Exception {
        mvc.perform(get("/api/v1/income").param("from","2026-11-12").param("to","2026-11-11").with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        mvc.perform(get("/api/v1/income").param("period","bad").with(user("admin").roles("ADMIN"))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/income").param("from","2020-01-01").param("to","2026-11-11").with(user("admin").roles("ADMIN"))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/sales/tables/999/services").with(user("staff").roles("STAFF"))).andExpect(status().isNotFound());
    }

    @Test void managerCanRetrieveTheReceiptSnapshotForPrinting() throws Exception {
        var bill = sales.createBill(1, new SalesService.BillCommand("SESSION-OLD", "print"), "staff");
        jdbc.update("UPDATE cafe_tables SET code='CHANGED',name='Đổi tên' WHERE id=1");
        mvc.perform(get("/api/v1/bills/" + bill.id()).with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tableName").value("Bàn gốc"))
                .andExpect(jsonPath("$.items[0].lineTotal").value("50000"));
        mvc.perform(get("/api/v1/bills/9999").with(user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test void acknowledgeCannotTargetAnotherOrClosedSessionAndRetryIsSafe() {
        jdbc.update("INSERT INTO session_clients(id,session_id,access_token_hash,expires_at) VALUES(1,1,REPEAT('f',64),DATE_ADD(CURRENT_TIMESTAMP,INTERVAL 1 DAY))");
        jdbc.update("INSERT INTO service_requests(id,session_id,requested_by_client_id,request_type,idempotency_key) VALUES(1,1,1,'WAITER','request')");
        assertThatThrownBy(()->sales.acknowledge(2,1,"SESSION-OLD","staff")).hasMessageContaining("409");
        assertThatThrownBy(()->sales.acknowledge(1,1,null,"staff")).hasMessageContaining("400");
        sales.acknowledge(1,1,"SESSION-OLD","staff");
        sales.acknowledge(1,1,"SESSION-OLD","staff");
        assertThat(jdbc.queryForObject("SELECT status FROM service_requests WHERE id=1",String.class)).isEqualTo("ACKNOWLEDGED");
        jdbc.update("UPDATE table_sessions SET status='CLOSED',closed_by_user_id=1,ended_at=CURRENT_TIMESTAMP WHERE id=1");
        jdbc.update("INSERT INTO table_sessions(id,table_id,session_code,opened_by_user_id) VALUES(2,1,'SESSION-NEW',1)");
        assertThatThrownBy(()->sales.acknowledge(1,1,"SESSION-OLD","staff")).hasMessageContaining("409");
        assertThatThrownBy(()->sales.acknowledge(1,1,"SESSION-NEW","staff")).hasMessageContaining("409");
    }

    @Test void qaFourRestrictsReportsPaymentsAndResetToManagers() throws Exception {
        var bill=sales.createBill(1,new SalesService.BillCommand("SESSION-OLD","auth"),"staff");
        for (String path : java.util.List.of("/api/v1/income", "/api/v1/bills/" + bill.id()))
            mvc.perform(get(path).with(user("staff").roles("STAFF"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/bills/"+bill.id()+"/payments").with(user("staff").roles("STAFF")).with(csrf())
                .contentType("application/json").content("{\"method\":\"CASH\",\"idempotencyKey\":\"pay\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/sales/tables/1/reset").with(user("staff").roles("STAFF")).with(csrf())
                .contentType("application/json").content("{\"expectedSessionCode\":\"SESSION-OLD\"}"))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payments",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM table_sessions WHERE id=1",String.class)).isEqualTo("OPEN");
    }

    @Test void managerConfirmationIsIdempotentAndPaidResetRevokesOnlyCurrentClients() throws Exception {
        var bill=sales.createBill(1,new SalesService.BillCommand("SESSION-OLD","paid"),"staff");
        long billId=Long.parseLong(bill.id());
        assertThatThrownBy(()->checkout.resetPaidSession(1,"SESSION-OLD","admin")).hasMessageContaining("409");
        jdbc.update("INSERT INTO session_clients(id,session_id,access_token_hash,expires_at) VALUES(1,1,REPEAT('f',64),DATE_ADD(CURRENT_TIMESTAMP,INTERVAL 1 DAY))");
        String body="{\"method\":\"BANK_TRANSFER\",\"referenceCode\":\"fixture-transfer\",\"idempotencyKey\":\"paid-key\",\"amount\":1}";
        for(int retry=0;retry<2;retry++)
            mvc.perform(post("/api/v1/bills/"+bill.id()+"/payments").with(user("admin").roles("ADMIN")).with(csrf())
                    .contentType("application/json").content(body)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.payment.amount").value("50000")).andExpect(jsonPath("$.bill.status").value("PAID"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payments",Integer.class)).isEqualTo(1);
        assertThatThrownBy(()->checkout.confirmPayment(billId,new CheckoutService.ConfirmPayment("CASH",null,"paid-key"),"admin")).hasMessageContaining("409");
        assertThatThrownBy(()->checkout.confirmPayment(billId,new CheckoutService.ConfirmPayment("CASH",null,"another-key"),"admin")).hasMessageContaining("409");
        mvc.perform(get("/api/v1/income").with(user("admin").roles("ADMIN"))).andExpect(jsonPath("$.totalAmount").value("50000"));
        mvc.perform(post("/api/v1/sales/tables/1/reset").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType("application/json").content("{\"expectedSessionCode\":\"SESSION-OLD\"}")).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM session_clients WHERE session_id=1 AND revoked_at IS NOT NULL",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM cafe_tables WHERE id=1",String.class)).isEqualTo("ACTIVE");
        jdbc.update("INSERT INTO table_sessions(id,table_id,session_code,opened_by_user_id) VALUES(2,1,'SESSION-NEW',1)");
        assertThatThrownBy(()->checkout.resetPaidSession(1,"SESSION-OLD","admin")).hasMessageContaining("409");
        assertThat(jdbc.queryForObject("SELECT status FROM table_sessions WHERE id=2",String.class)).isEqualTo("OPEN");
    }

    @Test void orderChangesAfterSnapshotCannotBeSilentlyPaid() {
        var bill=sales.createBill(1,new SalesService.BillCommand("SESSION-OLD","changed"),"staff");
        jdbc.update("UPDATE order_items SET quantity=3 WHERE id=1");
        assertThatThrownBy(()->checkout.confirmPayment(Long.parseLong(bill.id()),new CheckoutService.ConfirmPayment("CASH",null,"changed-pay"),"admin"))
                .hasMessageContaining("Orders changed after bill creation");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payments",Integer.class)).isZero();
        assertThat(reader.get(Long.parseLong(bill.id())).status()).isEqualTo("PENDING");
    }

    @Test void concurrentBillAndPaymentRetriesCommitOnlyOneOfEach() throws Exception {
        org.springframework.test.context.transaction.TestTransaction.flagForCommit();
        org.springframework.test.context.transaction.TestTransaction.end();
        try {
            var bill = race(() -> sales.createBill(1,new SalesService.BillCommand("SESSION-OLD","race-bill"),"staff")).getFirst();
            var receipts = race(() -> checkout.confirmPayment(Long.parseLong(bill.id()),
                    new CheckoutService.ConfirmPayment("CASH",null,"race-payment"),"admin"));
            assertThat(receipts.getFirst()).isEqualTo(receipts.getLast());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bills",Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payments",Integer.class)).isEqualTo(1);
            assertThatThrownBy(()->sales.createBill(1,new SalesService.BillCommand("SESSION-OLD","race-bill"),"staff"))
                    .hasMessageContaining("403");
        } finally {
            new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                assertThat(jdbc.queryForObject("SELECT DATABASE()",String.class)).endsWith("_test");
                for (String table : java.util.List.of("payments","bill_items","bills","order_items","order_batches",
                        "table_sessions","cafe_tables","menu_items","menu_categories","users")) jdbc.update("DELETE FROM " + table);
            });
        }
    }

    private static <T> java.util.List<T> race(java.util.concurrent.Callable<T> command) throws Exception {
        var workers = java.util.concurrent.Executors.newFixedThreadPool(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try {
            var first = workers.submit(() -> { start.await(); return command.call(); });
            var second = workers.submit(() -> { start.await(); return command.call(); });
            start.countDown();
            var a = first.get(10,java.util.concurrent.TimeUnit.SECONDS);
            var b = second.get(10,java.util.concurrent.TimeUnit.SECONDS);
            assertThat(a).isEqualTo(b);
            return java.util.List.of(a,b);
        } finally { workers.shutdownNow(); }
    }
}
