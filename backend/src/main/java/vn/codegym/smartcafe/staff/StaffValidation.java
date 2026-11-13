package vn.codegym.smartcafe.staff;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class StaffValidation {
    private static final BigDecimal MULTIPLE_OF = new BigDecimal("100000");
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]{6,63}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private StaffValidation() {}

    public static void validateCreate(StaffModels.CreateStaffRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dữ liệu yêu cầu không được rỗng.");
        }
        validateUsername(request.username());
        validateEmail(request.email());
        validateCommonFields(request.fullName(), request.address(), request.phone(), request.gender(), request.role(), request.salary());
    }

    public static void validateUpdate(StaffModels.UpdateStaffRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dữ liệu yêu cầu không được rỗng.");
        }
        validateEmail(request.email());
        validateCommonFields(request.fullName(), request.address(), request.phone(), request.gender(), request.role(), request.salary());
    }

    public static void validateUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username không được để trống.");
        }
        String trimmed = username.trim();
        if (trimmed.length() <= 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username phải dài hơn 6 ký tự.");
        }
        if (trimmed.length() > 64) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username không được vượt quá 64 ký tự.");
        }
        if (Character.isDigit(trimmed.charAt(0))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username không được bắt đầu bằng chữ số.");
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if ("admin".equals(lower) || "root".equals(lower)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username không được là admin hoặc root.");
        }
        if (!USERNAME_PATTERN.matcher(trimmed).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username chỉ gồm chữ cái, chữ số hoặc gạch dưới.");
        }
    }

    public static void validateEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email không được để trống.");
        }
        String trimmed = email.trim();
        if (trimmed.length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email không được vượt quá 255 ký tự.");
        }
        if (!EMAIL_PATTERN.matcher(trimmed).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Định dạng email không hợp lệ.");
        }
    }

    public static void validateCommonFields(String fullName, String address, String phone, String gender, String role, BigDecimal salary) {
        if (fullName == null || fullName.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Họ và tên không được để trống.");
        }
        if (fullName.trim().length() > 160) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Họ và tên không được vượt quá 160 ký tự.");
        }
        if (address == null || address.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Địa chỉ không được để trống.");
        }
        if (address.trim().length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Địa chỉ không được vượt quá 255 ký tự.");
        }
        if (phone == null || phone.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số điện thoại không được để trống.");
        }
        if (phone.trim().length() > 20) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số điện thoại không được vượt quá 20 ký tự.");
        }
        if (gender != null && !gender.trim().isEmpty()) {
            String g = gender.trim().toUpperCase(Locale.ROOT);
            if (!"MALE".equals(g) && !"FEMALE".equals(g)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Giới tính phải là MALE hoặc FEMALE.");
            }
        }
        if (role != null && !role.trim().isEmpty()) {
            String r = role.trim().toUpperCase(Locale.ROOT);
            if (!"STAFF".equals(r) && !"ADMIN".equals(r)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vai trò phải là STAFF hoặc ADMIN.");
            }
        }
        validateSalary(salary);
    }

    public static void validateSalary(BigDecimal salary) {
        if (salary == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lương không được để trống.");
        }
        if (salary.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lương phải lớn hơn 0.");
        }
        try {
            BigDecimal remainder = salary.remainder(MULTIPLE_OF);
            if (remainder.compareTo(BigDecimal.ZERO) != 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lương phải là bội số của 100.000 VNĐ.");
            }
        } catch (ArithmeticException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lương không hợp lệ.");
        }
    }
}
