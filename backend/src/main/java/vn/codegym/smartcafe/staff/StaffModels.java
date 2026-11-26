package vn.codegym.smartcafe.staff;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

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
            Long avatarMediaId
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
            Long avatarMediaId,
            String version
    ) {}

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
