package team.projectpulse.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import team.projectpulse.student.Student;
import team.projectpulse.user.UserRepository;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class JwtDecoderTest {

    @Mock
    UserRepository userRepository;

    @InjectMocks
    SecurityConfiguration configuration;

    @InjectMocks
    SecurityConfiguration otherConfiguration;

    JwtDecoder decoder;
    JwtEncoder encoder;

    @BeforeEach
    void setUp() {
        this.decoder = this.configuration.jwtDecoder(new ActiveAccountJwtValidator(this.userRepository));
        this.encoder = this.configuration.jwtEncoder();
    }

    @Test
    void configuredDecoderRechecksAccountStatus() {
        // T10 / FR-SEC-active-account: the configured decoder refuses the same token after deactivation.
        Student student = new Student();
        student.setEnabled(true);
        given(this.userRepository.findById(3)).willReturn(Optional.of(student));
        String token = token(this.encoder, Instant.now().plusSeconds(300));
        assertThat(this.decoder.decode(token).getSubject()).isEqualTo("student");
        student.setEnabled(false);
        assertThatThrownBy(() -> this.decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void configuredDecoderStillRejectsExpiredToken() {
        // T11 / FR-SEC-authentication: adding account status must preserve normal expiry checks.
        Student student = new Student();
        student.setEnabled(true);
        given(this.userRepository.findById(3)).willReturn(Optional.of(student));
        String token = token(this.encoder, Instant.now().minusSeconds(300));
        assertThatThrownBy(() -> this.decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void configuredDecoderStillRejectsAnotherSignature() {
        // T11 / FR-SEC-authentication: an account lookup must never replace signature validation.
        String token = token(this.otherConfiguration.jwtEncoder(), Instant.now().plusSeconds(300));
        assertThatThrownBy(() -> this.decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    private String token(JwtEncoder jwtEncoder, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder().subject("student").claim("userId", 3)
                .issuedAt(expiresAt.minusSeconds(600)).expiresAt(expiresAt).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }
}
