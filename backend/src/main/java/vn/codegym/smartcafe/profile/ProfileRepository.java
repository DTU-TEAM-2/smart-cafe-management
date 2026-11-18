package vn.codegym.smartcafe.profile;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class ProfileRepository {
    private final JdbcClient jdbc;

    ProfileRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    void lock(long id) {
        jdbc.sql("SELECT id FROM users WHERE id=? FOR UPDATE").param(id).query(Long.class).optional();
    }

    int updateUser(long id, long authVersion, ProfileModels.Patch patch) {
        return jdbc.sql("""
                UPDATE users SET full_name=COALESCE(?,full_name), phone=COALESCE(?,phone),
                       version=version+1, updated_at=CURRENT_TIMESTAMP(6)
                WHERE id=? AND status='ACTIVE' AND auth_version=? AND version=?
                """).param(patch.value("name")).param(patch.value("phone"))
                .param(id).param(authVersion).param(patch.version).update();
    }

    int updateAddress(long id, String address) {
        return jdbc.sql("UPDATE staff_profiles SET address=? WHERE user_id=?").param(address).param(id).update();
    }
}
