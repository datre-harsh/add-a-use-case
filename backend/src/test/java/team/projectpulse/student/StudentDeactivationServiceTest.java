package team.projectpulse.student;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import team.projectpulse.section.Section;
import team.projectpulse.system.UserUtils;
import team.projectpulse.team.Team;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentDeactivationServiceTest {

    @Mock StudentRepository studentRepository;
    @Mock UserUtils userUtils;
    @InjectMocks StudentService studentService;

    @Test
    void deactivationKeepsEnrollmentAndOptionalTeam() {
        // T01 / BR-student-deactivation-membership: only enabled changes, including a student with no team.
        given(this.userUtils.getUserId()).willReturn(1);
        given(this.userUtils.hasRole("ROLE_instructor")).willReturn(true);
        for (Team team : new Team[]{new Team(), null}) {
            Student student = new Student("john", "John", "Smith", "j.smith@abc.edu", "secret", true, "student");
            Section section = new Section();
            student.setId(4);
            student.setTeam(team);
            student.setSection(section);
            given(this.studentRepository.findManageableStudentById(4, 1)).willReturn(Optional.of(student));
            given(this.studentRepository.saveAndFlush(student)).willReturn(student);

            Student result = this.studentService.deactivateStudent(4);

            assertThat(result.isEnabled()).isFalse();
            assertThat(result.getId()).isEqualTo(4);
            assertThat(result.getSection()).isSameAs(section);
            assertThat(result.getTeam()).isSameAs(team);
            assertThat(result.getEmail()).isEqualTo("j.smith@abc.edu");
            assertThat(result.getRoles()).isEqualTo("student");
            verify(this.studentRepository).saveAndFlush(student);
        }
        verify(this.studentRepository, never()).deleteById(any());
    }

    @Test
    void repeatRequestChecksPermissionAndDoesNotSaveAgain() {
        // T06 / extension 6a: permission still applies to an already inactive student.
        Student student = new Student();
        student.setEnabled(false);
        given(this.userUtils.getUserId()).willReturn(1);
        given(this.userUtils.hasRole("ROLE_instructor")).willReturn(true);
        given(this.studentRepository.findManageableStudentById(4, 1)).willReturn(Optional.of(student));

        assertThat(this.studentService.deactivateStudent(4)).isSameAs(student);
        verify(this.studentRepository).findManageableStudentById(4, 1);
        verify(this.studentRepository, never()).saveAndFlush(any());
    }

    @Test
    void studentCannotDeactivateHerself() {
        // T04 / BR-student-lifecycle: self ownership is insufficient for this action.
        given(this.userUtils.getUserId()).willReturn(4);
        assertThatThrownBy(() -> this.studentService.deactivateStudent(4)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(this.studentRepository);
    }

    @Test
    void lostPermissionOrUnknownTargetIsDeniedByScopedLookup() {
        // T04,T07 / extensions 2a and 6b: recheck current assignment when confirmation arrives.
        given(this.userUtils.getUserId()).willReturn(1);
        given(this.userUtils.hasRole("ROLE_instructor")).willReturn(true);
        given(this.studentRepository.findManageableStudentById(4, 1)).willReturn(Optional.empty());
        assertThatThrownBy(() -> this.studentService.deactivateStudent(4)).isInstanceOf(AccessDeniedException.class);
        verify(this.studentRepository, never()).saveAndFlush(any());
        verify(this.studentRepository, never()).findById(any());
    }

    @Test
    void detailsLookupAlsoFailsClosedWithinCallerScope() {
        // T02,T07 / details GET contract: the service repeats route approval with a scoped query.
        given(this.userUtils.getUserId()).willReturn(1);
        given(this.userUtils.hasRole("ROLE_instructor")).willReturn(true);
        given(this.studentRepository.findReadableStudentById(4, 1, true)).willReturn(Optional.empty());
        assertThatThrownBy(() -> this.studentService.findStudentById(4)).isInstanceOf(AccessDeniedException.class);
        verify(this.studentRepository, never()).findById(any());
    }
}
