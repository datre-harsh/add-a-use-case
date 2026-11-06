package team.projectpulse.student;

import jakarta.persistence.EntityManager;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import team.projectpulse.AbstractIntegrationTest;
import team.projectpulse.evaluation.PeerEvaluation;
import team.projectpulse.evaluation.PeerEvaluationRepository;
import team.projectpulse.instructor.Instructor;
import team.projectpulse.instructor.InstructorRepository;
import team.projectpulse.section.Section;
import team.projectpulse.section.SectionRepository;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class StudentDeactivationIntegrationTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired EntityManager entityManager;
    @Autowired InstructorRepository instructorRepository;
    @Autowired SectionRepository sectionRepository;
    @Autowired PeerEvaluationRepository evaluationRepository;
    @Autowired JsonMapper jsonMapper;
    @Autowired JwtEncoder jwtEncoder;
    @MockitoSpyBean StudentRepository studentRepository;
    @Value("${api.endpoint.base-url}") String baseUrl;

    @Test
    void assignedInstructorDeactivatesAndPreservesTheDatabaseRecord() throws Exception {
        // T01 / main scenario and BR-student-deactivation-membership: compare retained rows, not just the response.
        String token = login("b.gates@abc.edu");
        Student before = this.studentRepository.findById(4).orElseThrow();
        Integer sectionId = before.getSection().getSectionId();
        Integer teamId = before.getTeam().getTeamId();
        List<?> activities = activityRows(4);
        List<?> evaluations = evaluationRows(4);
        List<?> ratings = ratingRows(4);
        assertThat(activities).isNotEmpty();
        assertThat(evaluations).isNotEmpty();
        assertThat(ratings).isNotEmpty();

        deactivate(4, token).andExpect(status().isOk()).andExpect(jsonPath("$.data.enabled").value(false));
        this.entityManager.clear();
        Student after = this.studentRepository.findById(4).orElseThrow();
        assertThat(after.isEnabled()).isFalse();
        assertThat(after.getSection().getSectionId()).isEqualTo(sectionId);
        assertThat(after.getTeam().getTeamId()).isEqualTo(teamId);
        assertThat(activityRows(4)).isEqualTo(activities);
        assertThat(evaluationRows(4)).isEqualTo(evaluations);
        assertThat(ratingRows(4)).isEqualTo(ratings);
    }

    @Test
    void studentWithoutTeamStaysWithoutTeam() throws Exception {
        // T01 / BR-student-deactivation-membership: enrollment does not imply a team.
        Student student = this.studentRepository.findById(15).orElseThrow();
        assertThat(student.getTeam()).isNull();
        Integer sectionId = student.getSection().getSectionId();
        deactivate(15, login("b.gates@abc.edu")).andExpect(status().isOk());
        this.entityManager.clear();
        Student reloaded = this.studentRepository.findById(15).orElseThrow();
        assertThat(reloaded.isEnabled()).isFalse();
        assertThat(reloaded.getTeam()).isNull();
        assertThat(reloaded.getSection().getSectionId()).isEqualTo(sectionId);
    }

    @Test
    void courseOwnerDoesNotNeedSeparateSectionAssignment() throws Exception {
        // T02 / BR-student-lifecycle: ownership admits the course admin through the scoped query.
        Section section = this.sectionRepository.findById(2).orElseThrow();
        Instructor owner = this.instructorRepository.findById(1).orElseThrow();
        section.removeInstructor(owner);
        this.entityManager.flush();
        this.entityManager.clear();
        String ownerToken = login("b.wei@abc.edu");
        this.mockMvc.perform(get(this.baseUrl + "/students/4").header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(4));
        this.mockMvc.perform(get(this.baseUrl + "/students/4").header(HttpHeaders.AUTHORIZATION, login("t.cook@abc.edu")))
                .andExpect(status().isForbidden());
        deactivate(4, ownerToken).andExpect(status().isOk());
        assertThat(this.studentRepository.findById(4).orElseThrow().isEnabled()).isFalse();
    }

    @Test
    void missingCredentialsAndInactiveCallerAreUnauthorized() throws Exception {
        // T03 / extension 1a: fail authentication and leave the selected student alone.
        this.mockMvc.perform(put(this.baseUrl + "/students/4/deactivation")).andExpect(status().isUnauthorized());
        String billToken = login("b.gates@abc.edu");
        Instructor bill = this.instructorRepository.findById(2).orElseThrow();
        bill.setEnabled(false);
        this.entityManager.flush();
        deactivate(4, billToken).andExpect(status().isUnauthorized());
        this.entityManager.clear();
        assertThat(this.studentRepository.findById(4).orElseThrow().isEnabled()).isTrue();
    }

    @Test
    void unauthorizedCallersAndUnknownTargetAreIndistinguishable() throws Exception {
        // T04 / extension 2a: self, another student, unassigned instructor and unrelated admin all receive 403.
        String john = login("j.smith@abc.edu");
        String eric = login("e.hudson@abc.edu");
        String bill = login("b.gates@abc.edu");
        String tim = login("t.cook@abc.edu");
        Section section = this.sectionRepository.findById(2).orElseThrow();
        section.removeInstructor(this.instructorRepository.findById(2).orElseThrow());
        this.entityManager.flush();
        for (String token : List.of(john, eric, bill, tim)) {
            deactivate(4, token).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.flag").value(false)).andExpect(jsonPath("$.message").value("No permission."));
        }
        deactivate(999999, login("b.wei@abc.edu")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("No permission."))
                .andExpect(jsonPath("$.data.id").doesNotExist());
        this.mockMvc.perform(put(this.baseUrl + "/students/not-a-number/deactivation")
                        .header(HttpHeaders.AUTHORIZATION, login("b.wei@abc.edu")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value("No permission."));
        this.entityManager.clear();
        assertThat(this.studentRepository.findById(4).orElseThrow().isEnabled()).isTrue();
    }

    @Test
    void authorizedRepeatSucceedsWithoutAnotherWrite() throws Exception {
        // T06 / extension 6a: a lost response can be retried without changing history.
        String token = login("b.gates@abc.edu");
        deactivate(4, token).andExpect(status().isOk());
        List<?> evaluations = evaluationRows(4);
        org.mockito.Mockito.clearInvocations(this.studentRepository);
        deactivate(4, token).andExpect(status().isOk()).andExpect(jsonPath("$.data.enabled").value(false));
        org.mockito.Mockito.verify(this.studentRepository, org.mockito.Mockito.never()).saveAndFlush(any());
        this.entityManager.clear();
        assertThat(evaluationRows(4)).isEqualTo(evaluations);
    }

    @Test
    void permissionIsRecheckedAfterViewingStudent() throws Exception {
        // T07 / extension 6b: loading details does not grant permission for a later confirmation.
        String token = login("b.gates@abc.edu");
        this.mockMvc.perform(get(this.baseUrl + "/students/4").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk());
        this.sectionRepository.findById(2).orElseThrow()
                .removeInstructor(this.instructorRepository.findById(2).orElseThrow());
        this.entityManager.flush();
        this.entityManager.clear();
        deactivate(4, token).andExpect(status().isForbidden());
        assertThat(this.studentRepository.findById(4).orElseThrow().isEnabled()).isTrue();
    }

    @Test
    void emptyScopedLookupAfterRouteApprovalIsStillForbidden() throws Exception {
        // T07 / extension 6b: a service no-match stays the same neutral refusal after the route has admitted it.
        String token = login("b.gates@abc.edu");
        Student target = this.studentRepository.findById(4).orElseThrow();
        org.mockito.Mockito.doReturn(java.util.Optional.of(target), java.util.Optional.empty())
                .when(this.studentRepository).findManageableStudentById(4, 2);
        deactivate(4, token).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("No permission."))
                .andExpect(jsonPath("$.data.id").doesNotExist());
        assertThat(target.isEnabled()).isTrue();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void saveFailureRollsBackAFlushedChange() throws Exception {
        // T08 / extension 6c: run without an outer test transaction so the API owns the rollback.
        String token = login("b.gates@abc.edu");
        doAnswer(invocation -> {
            this.entityManager.flush(); // The dirty enabled flag reaches MySQL before the forced failure.
            throw new DataAccessResourceFailureException("Forced deactivation save failure");
        }).when(this.studentRepository).saveAndFlush(any(Student.class));

        deactivate(4, token).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.flag").value(false));
        assertThat(this.studentRepository.findById(4).orElseThrow().isEnabled()).isTrue();
    }

    @Test
    void existingTokenAndFreshLoginFailAfterDeactivationWhileAnotherStudentStillWorks() throws Exception {
        // T10 / FR-SEC-active-account: already logged-in users lose the next protected request.
        String john = login("j.smith@abc.edu");
        String eric = login("e.hudson@abc.edu");
        this.mockMvc.perform(get(this.baseUrl + "/students/4").header(HttpHeaders.AUTHORIZATION, john)).andExpect(status().isOk());
        deactivate(4, login("b.gates@abc.edu")).andExpect(status().isOk());
        this.mockMvc.perform(get(this.baseUrl + "/students/4").header(HttpHeaders.AUTHORIZATION, john)).andExpect(status().isUnauthorized());
        this.mockMvc.perform(post(this.baseUrl + "/users/login").with(httpBasic("j.smith@abc.edu", "123456"))).andExpect(status().isUnauthorized());
        this.mockMvc.perform(get(this.baseUrl + "/students/5").header(HttpHeaders.AUTHORIZATION, eric)).andExpect(status().isOk());
    }

    @Test
    void invalidClaimsExpiryAndSignatureFailThroughSecurityChain() throws Exception {
        // T11 / FR-SEC-authentication: account checks preserve expiry and RSA signature validation.
        for (Object userId : new Object[]{null, "invalid", 999999}) {
            unauthorizedToken(signedToken(userId, Instant.now().plusSeconds(300)));
        }
        unauthorizedToken(signedToken(4, Instant.now().minusSeconds(300)));
        String valid = signedToken(4, Instant.now().plusSeconds(300));
        String[] parts = valid.split("\\.");
        parts[2] = (parts[2].startsWith("A") ? "B" : "A") + parts[2].substring(1);
        unauthorizedToken(String.join(".", parts));
        this.studentRepository.findById(4).orElseThrow().setEnabled(false);
        this.instructorRepository.findById(2).orElseThrow().setEnabled(false);
        this.entityManager.flush();
        unauthorizedToken(valid);
        unauthorizedToken(signedToken(2, Instant.now().plusSeconds(300)));
        this.mockMvc.perform(post(this.baseUrl + "/users/login").with(httpBasic("b.gates@abc.edu", "123456")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void staleNewEvaluationIsRejectedButSavedEvaluationCanStillBeEdited() throws Exception {
        // T13 / BR-evaluation-active-evaluatee: active targets work; new inactive targets fail; saved work remains editable.
        String john = login("j.smith@abc.edu");
        for (PeerEvaluation evaluation : this.evaluationRepository.findByWeekAndEvaluatorId("2023-W32", 4)) {
            if (evaluation.getEvaluatee().getId().equals(5)) this.evaluationRepository.delete(evaluation);
        }
        this.entityManager.flush();
        String body = evaluationBody(5);
        MvcResult created = this.mockMvc.perform(post(this.baseUrl + "/evaluations")
                        .header(HttpHeaders.AUTHORIZATION, john).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        int evaluationId = new JSONObject(created.getResponse().getContentAsString()).getJSONObject("data").getInt("evaluationId");
        deactivate(5, login("b.gates@abc.edu")).andExpect(status().isOk());
        this.mockMvc.perform(post(this.baseUrl + "/evaluations").header(HttpHeaders.AUTHORIZATION, john)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("New peer evaluations require an active teammate. Reload the current targets."));
        this.mockMvc.perform(put(this.baseUrl + "/evaluations/" + evaluationId).header(HttpHeaders.AUTHORIZATION, john)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.flag").value(true));
    }

    @Test
    void profileEditingCannotDeactivateButCanStillReactivate() throws Exception {
        // T15 / separate confirmed action: reject bypass with 400, preserve the existing reactivation path.
        String token = login("b.gates@abc.edu");
        Student student = this.studentRepository.findById(4).orElseThrow();
        Map<String, Object> body = Map.of("username", student.getUsername(), "firstName", "Changed",
                "lastName", student.getLastName(), "email", student.getEmail(), "enabled", false);
        Instructor owner = this.instructorRepository.findById(1).orElseThrow();
        owner.setRoles("admin");
        this.entityManager.flush();
        // Existing login only creates data for student/instructor roles; exercise the permitted admin-only API with a signed token.
        String adminToken = "Bearer " + signedToken(1, Instant.now().plusSeconds(300), "ROLE_admin");
        for (String caller : List.of(token, login("j.smith@abc.edu"), adminToken)) {
            this.mockMvc.perform(put(this.baseUrl + "/students/4").header(HttpHeaders.AUTHORIZATION, caller)
                        .contentType(MediaType.APPLICATION_JSON).content(this.jsonMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.flag").value(false));
        }
        this.entityManager.clear();
        assertThat(this.studentRepository.findById(4).orElseThrow().getFirstName()).isEqualTo("John");
        deactivate(4, token).andExpect(status().isOk());
        student = this.studentRepository.findById(4).orElseThrow();
        Map<String, Object> reactivation = Map.of("username", student.getUsername(), "firstName", student.getFirstName(),
                "lastName", student.getLastName(), "email", student.getEmail(), "enabled", true);
        this.mockMvc.perform(put(this.baseUrl + "/students/4").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(this.jsonMapper.writeValueAsString(reactivation)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.enabled").value(true));
        this.entityManager.flush();
        this.entityManager.clear();
        assertThat(this.studentRepository.findById(4).orElseThrow().isEnabled()).isTrue();
    }

    private org.springframework.test.web.servlet.ResultActions deactivate(int id, String token) throws Exception {
        return this.mockMvc.perform(put(this.baseUrl + "/students/" + id + "/deactivation")
                .header(HttpHeaders.AUTHORIZATION, token));
    }

    private String login(String email) throws Exception {
        MvcResult result = this.mockMvc.perform(post(this.baseUrl + "/users/login").with(httpBasic(email, "123456")))
                .andExpect(status().isOk()).andReturn();
        return "Bearer " + new JSONObject(result.getResponse().getContentAsString()).getJSONObject("data").getString("token");
    }

    private String signedToken(Object userId, Instant expiry) {
        return signedToken(userId, expiry, "ROLE_student");
    }

    private String signedToken(Object userId, Instant expiry, String authorities) {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder().subject("test").issuedAt(expiry.minusSeconds(600))
                .expiresAt(expiry).claim("authorities", authorities);
        if (userId != null) claims.claim("userId", userId);
        return this.jwtEncoder.encode(JwtEncoderParameters.from(claims.build())).getTokenValue();
    }

    private void unauthorizedToken(String token) throws Exception {
        this.mockMvc.perform(get(this.baseUrl + "/students/4").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.flag").value(false));
    }

    private String evaluationBody(int evaluateeId) throws Exception {
        Student student = this.studentRepository.findById(4).orElseThrow();
        List<?> ratings = student.getSection().getRubric().getCriteria().stream()
                .map(criterion -> Map.of("criterionId", criterion.getCriterionId(), "actualScore", 8.0)).toList();
        return this.jsonMapper.writeValueAsString(Map.of("week", "2023-W32", "evaluatorId", 4, "evaluateeId", evaluateeId,
                "ratings", ratings, "publicComment", "Retained public", "privateComment", "Retained private"));
    }

    private List<?> activityRows(int studentId) {
        return this.entityManager.createQuery("select a.activityId, a.student.id, a.week, a.team.teamId, a.category, a.activity, a.description, a.plannedHours, a.actualHours, a.status, a.comments from Activity a where a.student.id = :id order by a.activityId", Object[].class)
                .setParameter("id", studentId).getResultList().stream().map(java.util.Arrays::asList).toList();
    }

    private List<?> evaluationRows(int studentId) {
        return this.entityManager.createQuery("select e.peerEvaluationId, e.week, e.totalScore, e.publicComment, e.privateComment from PeerEvaluation e where e.evaluatee.id = :id or e.evaluator.id = :id order by e.peerEvaluationId", Object[].class)
                .setParameter("id", studentId).getResultList().stream().map(java.util.Arrays::asList).toList();
    }

    private List<?> ratingRows(int studentId) {
        return this.entityManager.createQuery("select r.ratingId, r.criterion.criterionId, r.actualScore, r.peerEvaluation.peerEvaluationId from Rating r where r.peerEvaluation.evaluatee.id = :id or r.peerEvaluation.evaluator.id = :id order by r.ratingId", Object[].class)
                .setParameter("id", studentId).getResultList().stream().map(java.util.Arrays::asList).toList();
    }
}
