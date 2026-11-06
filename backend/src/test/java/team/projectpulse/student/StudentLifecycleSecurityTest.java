package team.projectpulse.student;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import team.projectpulse.system.UserUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class StudentLifecycleSecurityTest {
    @Mock StudentRepository studentRepository;
    @Mock UserUtils userUtils;
    @InjectMocks StudentSecurityService securityService;

    @Test
    void instructorPermissionUsesCurrentScopedQuery() {
        // T02,T04,T07 / BR-student-lifecycle: the query admits only course ownership or section assignment.
        given(this.userUtils.getUserId()).willReturn(1);
        given(this.userUtils.hasRole("ROLE_instructor")).willReturn(true);
        given(this.studentRepository.findManageableStudentById(4, 1))
                .willReturn(Optional.of(new Student()), Optional.empty());
        assertThat(this.securityService.canDeactivateStudent(4)).isTrue();
        assertThat(this.securityService.canDeactivateStudent(4)).isFalse();
    }

    @Test
    void studentRoleDoesNotUseTheLifecycleQuery() {
        // T04: the student's self-access rule is not reused for lifecycle actions.
        given(this.userUtils.getUserId()).willReturn(4);
        assertThat(this.securityService.canDeactivateStudent(4)).isFalse();
        verifyNoInteractions(this.studentRepository);
    }

    @Test
    void databaseFailureDeniesPermission() {
        // stu.md / architectural guards: lookup failures deny access rather than escape the filter chain.
        given(this.userUtils.getUserId()).willReturn(1);
        given(this.userUtils.hasRole("ROLE_instructor")).willReturn(true);
        given(this.studentRepository.findManageableStudentById(4, 1))
                .willThrow(new DataAccessResourceFailureException("Unavailable"));
        assertThat(this.securityService.canDeactivateStudent(4)).isFalse();
    }
}
