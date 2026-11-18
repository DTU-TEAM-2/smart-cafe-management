// Test-tool helper only; password arrives on stdin and never enters process arguments.
import java.io.BufferedReader;
import java.io.InputStreamReader;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class AuthFixturePassword {
    public static void main(String[] args) throws Exception {
        String password = new BufferedReader(new InputStreamReader(System.in)).readLine();
        System.out.print(new BCryptPasswordEncoder().encode(password));
    }
}
