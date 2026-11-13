package vn.codegym.smartcafe.customeractions;

import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(basePackageClasses = CustomerOrderController.class)
class CustomerActionErrors {
    @ExceptionHandler(ResponseStatusException.class)
    ProblemDetail business(ResponseStatusException error) { return problem(error.getStatusCode(), error.getReason()); }
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class, ArithmeticException.class})
    ProblemDetail invalid(Exception error) { return problem(HttpStatus.BAD_REQUEST, "Dữ liệu yêu cầu không hợp lệ. Vui lòng kiểm tra món, số lượng và phiên."); }
    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    ProblemDetail unavailable(RuntimeException error) { return problem(HttpStatus.SERVICE_UNAVAILABLE, "Dịch vụ tạm thời gián đoạn. Vui lòng thử lại với cùng mã gửi yêu cầu."); }
    private ProblemDetail problem(HttpStatusCode status, String detail) {
        var result = ProblemDetail.forStatusAndDetail(status, detail);
        result.setTitle("Không thể thực hiện yêu cầu");
        return result;
    }
}
