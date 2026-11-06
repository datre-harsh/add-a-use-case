package team.projectpulse.security.authorizationmanagers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import team.projectpulse.student.StudentSecurityService;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class StudentLifecycleAuthorizationManagerTest {
    @Mock StudentSecurityService securityService;
    @InjectMocks StudentLifecycleAuthorizationManager manager;

    @Test
    void usesBoundStudentId() {
        // T04 / route authorization: use the variable the matched rule supplied.
        given(this.securityService.canDeactivateStudent(4)).willReturn(true);
        assertThat(this.manager.authorize(() -> null, context(Map.of("studentId", "4"))).isGranted()).isTrue();
    }

    @Test
    void deniesMissingOrMalformedId() {
        // T04 / extension 2a: an unresolved target fails closed.
        assertThat(this.manager.authorize(() -> null, context(Map.of())).isGranted()).isFalse();
        assertThat(this.manager.authorize(() -> null, context(Map.of("studentId", "invalid"))).isGranted()).isFalse();
        verifyNoInteractions(this.securityService);
    }

    private RequestAuthorizationContext context(Map<String, String> variables) {
        return new RequestAuthorizationContext(new MockHttpServletRequest(), variables);
    }
}
