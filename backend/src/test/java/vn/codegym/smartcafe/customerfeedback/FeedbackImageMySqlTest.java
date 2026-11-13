package vn.codegym.smartcafe.customerfeedback;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=${TEST_DB_URL}","spring.datasource.username=${TEST_DB_USERNAME}","spring.datasource.password=${TEST_DB_PASSWORD:}"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches="jdbc:mysql:.*")
class FeedbackImageMySqlTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired CustomerFeedbackService service;
    @Autowired FeedbackSubmission submission;
    @MockitoBean FirebaseFeedbackStorage storage;
    private static final String JSON="{\"name\":\"Khách ảnh test\",\"email\":\"images@example.invalid\",\"content\":\"Ảnh tốt\",\"rating\":5}";
    private FirebaseFeedbackStorage.Uploaded uploaded() {
        String name="feedback/mock-"+UUID.randomUUID()+".png";
        return new FirebaseFeedbackStorage.Uploaded(name,"https://example.invalid/"+name,"image/png",68,2,2);
    }
    @BeforeEach void isolated() {
        assertThat(jdbc.queryForObject("SELECT DATABASE()",String.class)).endsWith("_test");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM feedback",Integer.class)).isZero();
    }
    @AfterEach void cleanup() {
        jdbc.update("DELETE i FROM feedback_images i JOIN feedback f ON f.id=i.feedback_id WHERE f.author_email='images@example.invalid'");
        jdbc.update("DELETE FROM feedback WHERE author_email='images@example.invalid'");
        jdbc.update("DELETE FROM media_assets WHERE provider='FIREBASE' AND provider_asset_id LIKE 'feedback/mock-%'");
    }
    @Test void multipartSavesFeedbackAndFirebaseMetadataAtomicallyWithoutPublicAttachById() throws Exception {
        var image=uploaded();given(storage.upload(any())).willReturn(image);
        mvc.perform(multipart("/api/v1/customer/feedback")
                .file(new MockMultipartFile("feedback","","application/json",JSON.getBytes(StandardCharsets.UTF_8)))
                .file(new MockMultipartFile("images","test.png","image/png",FeedbackImagesTest.png())).with(csrf()))
                .andExpect(status().isCreated());
        var row=jdbc.queryForMap("SELECT f.author_email,m.provider,m.provider_asset_id,i.sort_order FROM feedback f JOIN feedback_images i ON i.feedback_id=f.id JOIN media_assets m ON m.id=i.media_id");
        assertThat(row.get("provider")).isEqualTo("FIREBASE");assertThat(row.get("provider_asset_id")).isEqualTo(image.name());
        assertThat(((Number)row.get("sort_order")).intValue()).isZero();
    }
    @Test void constraintFailureRollsBackAllFeedbackAndMetadataAndCompensatesProvider() throws Exception {
        var image=uploaded();given(storage.upload(any())).willReturn(image);
        var file=new MockMultipartFile("images","test.png","image/png",FeedbackImagesTest.png());
        var command=new CustomerFeedbackController.Command("Khách ảnh test","images@example.invalid","Ảnh tốt",java.math.BigDecimal.valueOf(5));
        assertThatThrownBy(()->submission.submit(command,List.of(file,file))).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM feedback",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM feedback_images",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM media_assets WHERE provider='FIREBASE'",Integer.class)).isZero();
        verify(storage,org.mockito.Mockito.times(2)).delete(image);
    }
}
