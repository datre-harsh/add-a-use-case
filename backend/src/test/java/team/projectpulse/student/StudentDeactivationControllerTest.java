package team.projectpulse.student;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import team.projectpulse.section.Section;
import team.projectpulse.student.converter.StudentToStudentDtoConverter;
import team.projectpulse.system.exception.ExceptionHandlerAdvice;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class StudentDeactivationControllerTest {
    @Mock StudentService studentService;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        StudentController controller = new StudentController(this.studentService, null,
                new StudentToStudentDtoConverter(), null);
        this.mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .addPlaceholderValue("api.endpoint.base-url", "/api/v1")
                .setControllerAdvice(new ExceptionHandlerAdvice()).build();
    }

    @Test
    void returnsInactiveStudentInResultEnvelope() throws Exception {
        // T01 / step 6 API contract: path id only, no body, 200 Result with enabled false.
        Student student = new Student("john", "John", "Smith", "j.smith@abc.edu", "secret", false, "student");
        student.setId(4);
        student.setSection(new Section());
        given(this.studentService.deactivateStudent(4)).willReturn(student);

        this.mockMvc.perform(put("/api/v1/students/4/deactivation"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.flag").value(true))
                .andExpect(jsonPath("$.data.id").value(4))
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.password").doesNotExist());
        verify(this.studentService).deactivateStudent(4);
    }

    @Test
    void reportsRefusalWithoutSuccess() throws Exception {
        // T07 / extension 6b: service permission refusal uses the standard 403 envelope.
        given(this.studentService.deactivateStudent(4)).willThrow(new AccessDeniedException("No permission."));
        this.mockMvc.perform(put("/api/v1/students/4/deactivation"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.flag").value(false));
    }

    @Test
    void reportsPersistenceFailureWithoutSuccess() throws Exception {
        // T08 / extension 6c API failure shape; database rollback is checked in the integration test.
        given(this.studentService.deactivateStudent(4))
                .willThrow(new DataAccessResourceFailureException("Save failed"));
        this.mockMvc.perform(put("/api/v1/students/4/deactivation"))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.flag").value(false));
    }
}
