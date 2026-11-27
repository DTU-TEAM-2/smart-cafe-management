package vn.codegym.smartcafe.staff;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

public final class StaffModels {
    private StaffModels() {}

    public record StaffSummary(
            @JsonFormat(shape = JsonFormat.Shape.STRING) Long id,
            String username,
            String fullName,
            String email,
            String phone,
            String address,
            String gender,
            LocalDate birthday,
            String role,
            @JsonFormat(shape = JsonFormat.Shape.STRING)
            BigDecimal salary,
            String status,
            String avatarUrl
    ) {}

    public record StaffDetail(
            @JsonFormat(shape = JsonFormat.Shape.STRING) Long id,
            String username,
            String fullName,
            String email,
            String phone,
            String address,
            String gender,
            LocalDate birthday,
            String role,
            @JsonFormat(shape = JsonFormat.Shape.STRING)
            BigDecimal salary,
            String status,
            @JsonFormat(shape = JsonFormat.Shape.STRING) Long avatarMediaId,
            String avatarUrl,
            String version
    ) {}

    public record CreateStaffRequest(
            String username,
            String fullName,
            String email,
            String phone,
            String address,
            String gender,
            LocalDate birthday,
            String role,
            BigDecimal salary,
            @JsonDeserialize(using = AvatarMediaIdDeserializer.class) Long avatarMediaId
    ) {}

    public record UpdateStaffRequest(
            String fullName,
            String email,
            String phone,
            String address,
            String gender,
            LocalDate birthday,
            String role,
            BigDecimal salary,
            @JsonDeserialize(using = AvatarMediaIdDeserializer.class) Long avatarMediaId,
            String version
    ) {}

    static final class AvatarMediaIdDeserializer extends ValueDeserializer<Long> {
        @Override
        public Long deserialize(JsonParser parser, DeserializationContext context) {
            if (parser.hasToken(JsonToken.VALUE_NUMBER_INT) || parser.hasToken(JsonToken.VALUE_STRING)) {
                String text = parser.getString();
                if (text.matches("[1-9][0-9]{0,18}")) {
                    try {
                        return Long.parseLong(text);
                    } catch (NumberFormatException ignored) {
                        // A 19-digit value can exceed the signed BIGINT range.
                    }
                }
            }
            return context.reportInputMismatch(this, "avatarMediaId must be a positive signed BIGINT integer");
        }
    }

    public record PageResponse<T>(
            List<T> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {}

    public record ImageUploadResponse(
            @JsonFormat(shape = JsonFormat.Shape.STRING) Long mediaId,
            String secureUrl
    ) {}

    public record CsrfResponse(
            String headerName,
            String token
    ) {}
}
