package vn.codegym.smartcafe.database;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.codegym.smartcafe.auth.*;
import vn.codegym.smartcafe.home.AccountReader;
import vn.codegym.smartcafe.profile.*;
import vn.codegym.smartcafe.staff.*;
import static org.assertj.core.api.Assertions.*;

/** Real module services/transactions/MySQL, with database lock queues controlling genuine overlap.
 * Explicit service wiring isolates concurrency from the independently reported controller-name collision.
 */
@org.springframework.test.context.junit.jupiter.SpringJUnitConfig(StaffProfileConcurrencyTest.Probe.class)
@TestPropertySource(properties={"smartcafe.database.policy=verified-20261113", "spring.flyway.enabled=true", "spring.flyway.locations=classpath:db/smart-cafe"})
@DirtiesContext
class StaffProfileConcurrencyTest {
    static final TestSchema SCHEMA = provision();
    static TestSchema provision() {
        var schema = new TestSchema(null);
        try {
            var flyway=Flyway.configure().dataSource(schema.source).locations("classpath:db/smart-cafe")
                    .initSql("SET SESSION time_zone='+00:00'").baselineOnMigrate(false).validateOnMigrate(true).load();
            assertThat(DatabaseRehearsal.execute(flyway,"provision")).isEqualTo(1);
            return schema;
        } catch(Exception e) {schema.close();throw new IllegalStateException(e);}
    }
    @org.springframework.boot.test.context.TestConfiguration @EnableAutoConfiguration
    @ComponentScan("vn.codegym.smartcafe.profile")
    @Import({StaffService.class, AccountReader.class, AuthRepository.class, TokenService.class, DatabaseMigrationConfiguration.class})
    static class Probe {
        @Bean Clock clock(){return Clock.systemUTC();}
        @Bean PasswordEncoder passwords(){return new BCryptPasswordEncoder();}
    }
    @DynamicPropertySource static void datasource(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",SCHEMA.source::getUrl);
        r.add("spring.datasource.username",SCHEMA.source::getUsername);
        r.add("spring.datasource.password",SCHEMA.source::getPassword);
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired StaffService staff;
    @Autowired ProfileService profiles;
    @Autowired AuthRepository auth;
    @Autowired TokenService tokens;
    @Autowired PasswordEncoder encoder;
    @Autowired PlatformTransactionManager manager;
    UserRecord principal;
    String currentPassword, nextPassword;
    @BeforeEach void fixture() {
        SCHEMA.assertIsolated();SCHEMA.update("DELETE FROM staff_profiles");SCHEMA.update("DELETE FROM users");
        currentPassword="Aa1!"+UUID.randomUUID();nextPassword="Bb2!"+UUID.randomUUID();
        String hash=encoder.encode(currentPassword);
        SCHEMA.update("INSERT INTO users(id,username,password_hash,full_name,email,phone,role,status) VALUES(1,'rehearsal_admin',?,'Original','rehearsal_admin@example.test','0900000001','ADMIN','ACTIVE'),(2,'rehearsal_staff',?,'Original','rehearsal_staff@example.test','0900000002','STAFF','ACTIVE')",hash,hash);
        SCHEMA.update("INSERT INTO staff_profiles(user_id,address,salary) VALUES(1,'Original address',10000000),(2,'Original address',10000000)");
        principal=auth.findUserById(2L).orElseThrow();
    }
    @AfterAll static void cleanup(){SCHEMA.close();}
    StaffModels.UpdateStaffRequest request(String name,String address) {
        return new StaffModels.UpdateStaffRequest(name,"rehearsal_staff@example.test","0900000002",address,null,null,"STAFF",new BigDecimal("10000000"),null);
    }
    ProfileModels.Patch patch(String field,String value){return new ProfileModels.Patch(Map.of("version","0",field,value));}
    ProfileModels.PasswordChange password(){return new ProfileModels.PasswordChange(Map.of("currentPassword",currentPassword,"newPassword",nextPassword,"confirmNewPassword",nextPassword));}
    String outcome(Callable<?> work) {
        try {work.call();return "200";} catch(Exception e) {
            if(e instanceof org.springframework.web.server.ResponseStatusException r)return Integer.toString(r.getStatusCode().value());
            try {var f=e.getClass().getDeclaredField("status");f.setAccessible(true);return Integer.toString(((org.springframework.http.HttpStatus)f.get(e)).value());}
            catch(ReflectiveOperationException ignored){return e.getClass().getSimpleName();}
        }
    }
    void awaitWaiters(int count) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while(System.nanoTime()<deadline) {
            int waiting=SCHEMA.jdbc.queryForObject("SELECT COUNT(*) FROM performance_schema.data_lock_waits w JOIN performance_schema.data_locks l ON w.REQUESTING_ENGINE_LOCK_ID=l.ENGINE_LOCK_ID WHERE l.OBJECT_SCHEMA=? AND l.OBJECT_NAME='users'",Integer.class,SCHEMA.name);
            if(waiting>=count)return;Thread.sleep(20);
        }
        throw new AssertionError("Both real database transactions must overlap at users lock queue");
    }
    List<String> race(Callable<?> first,Callable<?> second) throws Exception {
        try(Connection gate=SCHEMA.source.getConnection();var pool=Executors.newFixedThreadPool(2)) {
            gate.setAutoCommit(false);
            try(var q=gate.prepareStatement("SELECT id FROM users WHERE id=2 FOR UPDATE")){q.executeQuery().close();}
            var a=pool.submit(()->outcome(first));awaitWaiters(1);
            var b=pool.submit(()->outcome(second));awaitWaiters(2);
            gate.commit();
            var result=List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS));
            System.out.println("REHEARSAL_SQL statuses="+result+" state="+jdbc.queryForMap("SELECT full_name,phone,status,auth_version,version,(SELECT address FROM staff_profiles WHERE user_id=2) AS address FROM users WHERE id=2"));
            return result;
        }
    }
    @Test void staffSelfEditAndAdminEditCannotSilentlyOverwriteSameName() throws Exception {
        assertThat(race(()->staff.updateStaff(2L,request("Admin name","Original address"),"rehearsal_admin"),
                ()->profiles.update(principal,patch("name","Self name")))).containsExactly("200","409");
        assertThat(jdbc.queryForObject("SELECT full_name FROM users WHERE id=2",String.class)).isEqualTo("Admin name");
    }
    @Test void adminNameEditMustNotRestoreStaleAddressFromFullPayload() throws Exception {
        assertThat(race(()->profiles.update(principal,patch("address","Self address")),
                ()->staff.updateStaff(2L,request("Admin name","Original address"),"rehearsal_admin"))).containsExactly("200","409");
        assertThat(jdbc.queryForObject("SELECT address FROM staff_profiles WHERE user_id=2",String.class)).isEqualTo("Self address");
    }
    @Test void sameFieldCompetitionRequiresConflictAndOneVersionIncrement() throws Exception {
        assertThat(race(()->profiles.update(principal,patch("name","Self name")),
                ()->staff.updateStaff(2L,request("Admin name","Original address"),"rehearsal_admin"))).containsExactly("200","409");
        assertThat(jdbc.queryForObject("SELECT version FROM users WHERE id=2",Long.class)).isEqualTo(1L);
    }
    @Test void deactivationWinningBeforePasswordMustRejectPasswordAndInvalidateOldBearer() throws Exception {
        var before=jdbc.queryForObject("SELECT password_hash FROM users WHERE id=2",String.class);
        assertThat(race(()->{staff.deleteStaff(2L,"rehearsal_admin");return null;},
                ()->profiles.changePassword(principal,password()))).containsExactly("200","401");
        assertThat(jdbc.queryForObject("SELECT password_hash FROM users WHERE id=2",String.class)).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id=2 AND status='ACTIVE' AND auth_version=0",Integer.class)).isZero();
    }
    @Test void passwordWinningBeforeDeactivationCannotLeaveReplacementBearerCurrent() throws Exception {
        assertThat(race(()->profiles.changePassword(principal,password()),()->{staff.deleteStaff(2L,"rehearsal_admin");return null;})).containsExactly("200","200");
        assertThat(encoder.matches(nextPassword,jdbc.queryForObject("SELECT password_hash FROM users WHERE id=2",String.class))).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id=2 AND status='ACTIVE' AND auth_version=1",Integer.class)).isZero();
    }
    @Test void personnelUpdateDuringPasswordCannotRestoreHashOrAuthVersion() throws Exception {
        var results=race(()->profiles.changePassword(principal,password()),()->staff.updateStaff(2L,request("Admin name","Original address"),"rehearsal_admin"));
        assertThat(results.getFirst()).isEqualTo("200");assertThat(results.getLast()).isIn("200","409");
        assertThat(encoder.matches(nextPassword,jdbc.queryForObject("SELECT password_hash FROM users WHERE id=2",String.class))).isTrue();
        assertThat(jdbc.queryForObject("SELECT auth_version FROM users WHERE id=2",Long.class)).isEqualTo(1L);
    }
    @Test void profileStaleVersionAfterStaffUpdateMustBeRejected() {
        staff.updateStaff(2L,request("Admin name","Original address"),"rehearsal_admin");
        assertThat(outcome(()->profiles.update(principal,patch("phone","123")))).isEqualTo("409");
    }
    @Test void bothModulesFollowUsersThenPersonnelLockOrderWithoutDeadlock() throws Exception {
        for(int i=0;i<5;i++) {
            SCHEMA.update("UPDATE users SET version=0 WHERE id=2");
            assertThat(race(()->staff.updateStaff(2L,request("Admin name","Admin address"),"rehearsal_admin"),
                    ()->profiles.update(principal,patch("address","Self address")))).containsExactly("200","409");
        }
    }
    @Test void rolledBackAdminUpdateReleasesBothRowsAndSelfEditSeesNoPartialWrite() throws Exception {
        var written=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var admin=pool.submit(()->new TransactionTemplate(manager).execute(status->{
                staff.updateStaff(2L,request("Rollback name","Rollback address"),"rehearsal_admin");
                written.countDown();try{if(!release.await(10,TimeUnit.SECONDS))throw new AssertionError("rollback release timeout");}catch(InterruptedException e){throw new IllegalStateException(e);}
                status.setRollbackOnly();return "rolled back";
            }));
            assertThat(written.await(10,TimeUnit.SECONDS)).isTrue();
            var self=pool.submit(()->outcome(()->profiles.update(principal,patch("address","Self address"))));awaitWaiters(1);release.countDown();
            assertThat(admin.get(20,TimeUnit.SECONDS)).isEqualTo("rolled back");assertThat(self.get(20,TimeUnit.SECONDS)).isEqualTo("200");
        }
        assertThat(jdbc.queryForObject("SELECT full_name FROM users WHERE id=2",String.class)).isEqualTo("Original");
        assertThat(jdbc.queryForObject("SELECT address FROM staff_profiles WHERE user_id=2",String.class)).isEqualTo("Self address");
        assertThat(jdbc.queryForObject("SELECT version FROM users WHERE id=2",Long.class)).isEqualTo(1L);
    }
}
