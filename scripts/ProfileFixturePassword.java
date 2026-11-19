import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Isolated fixture helper: secrets travel over stdin, never process arguments. */
class ProfileFixturePassword {
    public static void main(String[] args) throws Exception {
        var input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String password = input.readLine();
        var encoder = new BCryptPasswordEncoder();
        if (args.length == 0) System.out.print(encoder.encode(password));
        else if (args.length == 1 && "matches".equals(args[0])) {
            System.out.print(encoder.matches(password, input.readLine()) ? "MATCH" : "NO_MATCH");
        } else throw new IllegalArgumentException("Unsupported fixture operation");
    }
}
