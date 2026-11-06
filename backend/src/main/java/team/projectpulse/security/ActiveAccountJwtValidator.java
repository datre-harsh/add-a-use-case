package team.projectpulse.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import team.projectpulse.user.UserRepository;

@Component
public class ActiveAccountJwtValidator implements OAuth2TokenValidator<Jwt> {

    private final UserRepository userRepository;

    public ActiveAccountJwtValidator(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        Object claim = jwt.getClaim("userId");
        try {
            Integer userId;
            if (claim instanceof Integer id) {
                userId = id;
            } else if (claim instanceof Long id) {
                userId = Math.toIntExact(id);
            } else if (claim instanceof String id) {
                userId = Integer.valueOf(id);
            } else {
                return invalidAccount();
            }

            if (userId > 0 && this.userRepository.findById(userId)
                    .map(user -> user.isEnabled()).orElse(false)) {
                return OAuth2TokenValidatorResult.success();
            }
        } catch (RuntimeException exception) {
            // A malformed id or failed account lookup must never grant access.
            return invalidAccount();
        }
        return invalidAccount();
    }

    private OAuth2TokenValidatorResult invalidAccount() {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                "invalid_token", "The account is unavailable or inactive.", null));
    }
}
