package vn.codegym.smartcafe.staff;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class StaffModels {
    private StaffModels() {}

    public record StaffSummary(
            Long id,
            String username,
            String fullName,
            String email,
            String phone,
            String address,
            String gender,
            LocalDate birthday,
            String role,
            BigDecimal salary,
            String status,
            String avatarUrl
    ) {}

    public record StaffDetail(
            Long id,
            String username,
            String fullName,
            String email,
            String phone,
            String address,
            String gender,
            LocalDate birthday,
            String role,
            BigDecimal salary,
            String status,
            Long avatarMediaId,
            String avatarUrl
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
            Long avatarMediaId
    ) {}

    public record PageResponse<T>(
            List<T> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {}

    public record ImageUploadResponse(
            Long mediaId,
            String secureUrl
    ) {}

    public record CsrfResponse(
            String headerName,
            String token
    ) {}
}
