package team.projectpulse.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.oauth2.jwt.Jwt;
import team.projectpulse.instructor.Instructor;
import team.projectpulse.student.Student;
import team.projectpulse.user.PeerEvaluationUser;
import team.projectpulse.user.UserRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ActiveAccountJwtValidatorTest {

    @Mock
    UserRepository userRepository;

    @InjectMocks
    ActiveAccountJwtValidator validator;

    @Test
    void acceptsActiveStudentAndInstructor() {
        // T11 / FR-SEC-active-account: the status check applies to both user types.
        Student student = new Student();
        Instructor instructor = new Instructor();
        for (PeerEvaluationUser user : List.of(student, instructor)) {
            user.setEnabled(true);
            given(this.userRepository.findById(3)).willReturn(Optional.of(user));
            assertThat(this.validator.validate(token(3)).hasErrors()).isFalse();
        }
    }

    @Test
    void rejectsStudentAndInstructorAfterDeactivation() {
        // T11 / FR-SEC-active-account: otherwise-valid credentials cannot bypass inactive status.
        for (PeerEvaluationUser user : List.of(new Student(), new Instructor())) {
            user.setEnabled(false);
            given(this.userRepository.findById(3)).willReturn(Optional.of(user));
            assertThat(this.validator.validate(token(3)).hasErrors()).isTrue();
        }
    }

    @Test
    void checksCurrentStatusAgainForTheSameToken() {
        // T10 / UC-STU-deactivate-student Associated Information: next request loses access.
        Student student = new Student();
        student.setEnabled(true);
        given(this.userRepository.findById(3)).willReturn(Optional.of(student));
        Jwt jwt = token(3);
        assertThat(this.validator.validate(jwt).hasErrors()).isFalse();

        student.setEnabled(false);
        assertThat(this.validator.validate(jwt).hasErrors()).isTrue();
    }

    @Test
    void rejectsMissingOrMalformedUserIdWithoutQueryingAnAccount() {
        // T11: missing and invalid ids fail validation, including an overflowing numeric id.
        assertThat(this.validator.validate(token(null)).hasErrors()).isTrue();
        for (Object id : List.of("invalid", "", 0, -1, 3.5, Long.MAX_VALUE, List.of(3))) {
            assertThat(this.validator.validate(token(id)).hasErrors()).as("userId %s", id).isTrue();
        }
        verifyNoInteractions(this.userRepository);
    }

    @Test
    void acceptsRepresentationsUsedByTheExistingIdentityReader() {
        // T11: valid userId values still identify the active account after JWT decoding.
        Student student = new Student();
        student.setEnabled(true);
        given(this.userRepository.findById(3)).willReturn(Optional.of(student));
        for (Object id : List.of(3, 3L, "3")) {
            assertThat(this.validator.validate(token(id)).hasErrors()).isFalse();
        }
    }

    @Test
    void rejectsDeletedAccount() {
        // T11: a token for an account that no longer exists must be refused.
        given(this.userRepository.findById(3)).willReturn(Optional.empty());
        assertThat(this.validator.validate(token(3)).hasErrors()).isTrue();
    }

    @Test
    void rejectsWhenAccountLookupFails() {
        // stu.md Limits: a failed current-account lookup must deny access.
        given(this.userRepository.findById(3)).willThrow(new DataAccessResourceFailureException("Database unavailable"));
        assertThat(this.validator.validate(token(3)).hasErrors()).isTrue();
    }

    private Jwt token(Object userId) {
        Jwt.Builder builder = Jwt.withTokenValue("token").header("alg", "RS256").subject("student");
        if (userId != null) {
            builder.claim("userId", userId);
        }
        return builder.build();
    }
}
