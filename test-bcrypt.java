import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class TestBcrypt {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String rawPassword = "Admin123!";
        String storedHash = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
        
        boolean matches = encoder.matches(rawPassword, storedHash);
        System.out.println("Password matches: " + matches);
        
        // Also generate a new hash to compare
        String newHash = encoder.encode(rawPassword);
        System.out.println("New hash: " + newHash);
    }
}
