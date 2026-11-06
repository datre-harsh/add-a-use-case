# Student Design

> Realizes: [UC-STU-deactivate-student](../requirements/use-cases.md#uc-stu-deactivate-student-the-course-admininstructor-deactivates-a-student)
> Depends on: FR-SEC-authentication, FR-SEC-authorization, FR-SEC-active-account in the [SRS](../requirements/software-requirements-specification.md).
> Follows the [architectural conventions](architectural-design.md#architectural-conventions) and the existing controller → service → repository pattern.

## Overview

Add a confirmed deactivation action to the existing student details form. Keep the current student record and its `enabled` field. The related evaluation changes stay in `EvaluationService`; reminders continue using `SubmissionStatusService` and BR-submission-owed.

## Components and classes

```mermaid
classDiagram
    direction TB
    StudentController --> StudentService
    StudentService --> StudentRepository
    SecurityConfiguration --> StudentLifecycleAuthorizationManager
    StudentLifecycleAuthorizationManager --> StudentSecurityService
    StudentSecurityService --> StudentRepository
    SecurityConfiguration --> ActiveAccountJwtValidator
    ActiveAccountJwtValidator --> UserRepository
```

| Component | Work |
|---|---|
| [StudentController](../../backend/src/main/java/team/projectpulse/student/StudentController.java), [StudentService](../../backend/src/main/java/team/projectpulse/student/StudentService.java), [StudentRepository](../../backend/src/main/java/team/projectpulse/student/StudentRepository.java) | Reuse. Add the endpoint, a transactional service method, and a query limited to students the caller may manage. |
| [StudentSecurityService](../../backend/src/main/java/team/projectpulse/student/StudentSecurityService.java) | Reuse. Check instructor role and either assignment to the student's course section or ownership of its course. The service repeats the same scope check before saving. |
| `security/authorizationmanagers/StudentLifecycleAuthorizationManager.java` | New. Read `studentId` with `PathVariables` and delegate the permission check to `StudentSecurityService`. |
| `security/ActiveAccountJwtValidator.java`, [SecurityConfiguration](../../backend/src/main/java/team/projectpulse/security/SecurityConfiguration.java), [UserRepository](../../backend/src/main/java/team/projectpulse/user/UserRepository.java) | One new validator. Add it to the existing JWT decoder alongside its normal expiry checks. Look up the token's `userId`; a missing or inactive account fails validation. Keep the signature check. |
| [EvaluationService](../../backend/src/main/java/team/projectpulse/evaluation/EvaluationService.java) | Reuse. Check the evaluatee's active status before adding an evaluation. In the course section report, include an inactive student only when evaluations of her exist for the requested week. Check for saved rows, not a nonzero average. |
| [EditStudentForm.vue](../../frontend/src/pages/students/EditStudentForm.vue), [Students.vue](../../frontend/src/pages/students/Students.vue), [student API](../../frontend/src/apis/student/index.ts) | Reuse. Add a Deactivate button, an Element Plus confirmation dialog, and one API function. Refresh the student list after success. |
| [SubmitTeamsEvaluations.vue](../../frontend/src/pages/evaluations/SubmitTeamsEvaluations.vue) | Reuse. Filter inactive students out of the list used to create new evaluations. Keep the existing saved-evaluation editing path. |

The new validator applies to student and instructor accounts because FR-SEC-active-account covers both. The new authorization manager must deny student self-deactivation. An admin role alone does not grant access to another course's students.

## Deactivation sequence

```mermaid
sequenceDiagram
    actor Instructor
    participant UI as Student details
    participant API as Security and controller
    participant Service as StudentService
    participant Repository as StudentRepository

    Instructor->>UI: View selected student (1–2)
    Instructor->>UI: Choose Deactivate (3)
    UI-->>Instructor: Show name, ID, and warning (4)
    Instructor->>UI: Confirm (5)
    UI->>API: PUT /students/{studentId}/deactivation (6)
    API->>API: Check token, active account, and permission
    API->>Service: deactivateStudent(studentId)
    Service->>Repository: Load student within caller's scope
    alt Student is active
        Service->>Repository: Save and flush enabled = false
    else Student is already inactive
        Service->>Service: Keep current record (6a)
    end
    Service-->>API: Student
    API-->>UI: 200 Result with StudentDto
    UI-->>Instructor: Show inactive status (6–7)
```

The dialog explains account access, reminders, new evaluations, retained membership and work, and possible reactivation, as specified in the use case. Cancel, Escape, and closing the dialog send no request. Disable Confirm while saving. A failed or unanswered request does not display success; an unanswered request asks the instructor to reload before trying again.

For an active student, replace the editable status switch with status text and the Deactivate button. Keep the existing status switch for an inactive student so her current reactivation path still works.

## API contract

All responses use the existing `Result` envelope. Caller identity comes from `UserUtils`, never the request body.

| Endpoint | Caller and input | Success | Errors |
|---|---|---|---|
| New `PUT /api/v1/students/{studentId}/deactivation` | Course owner or instructor assigned to the student's course section. Path ID only; no body. Register the lifecycle manager before the API's deny-all rule. | `200`, StudentDto with `enabled: false`. An already inactive student returns the same state. | `401` for extension 1a. `403` for 2a or 6b, including an unknown target. `500` for 6c after rollback. No response is handled by the page under 6d. |
| Existing `PUT /api/v1/students/{studentId}` | Existing profile-edit permissions and body. | Existing response for profile changes. Keep the existing reactivation behavior. | `400` if an instructor tries to change an active student to inactive through profile editing; direct her to the dedicated action. |
| Existing `POST /api/v1/evaluations` | Existing student permissions and body. | Existing response for an active teammate. | `400` if the evaluatee is inactive, including a page loaded before deactivation (UC-EVA-submit-evaluation 9a). |
| Existing `GET /api/v1/evaluations/sections/{sectionId}/week/{week}` | Existing course section report permissions and inputs. | Existing report, with inactive students included under BR-deactivated-student-report. | Existing access and input errors. |

## Key decisions

| Choice | Reason and alternative |
|---|---|
| Keep the team assignment | This preserves the link used by existing reports. Removing the student would need extra handling for that history. BR-submission-owed already excludes inactive members. |
| Choose historical report rows by saved evaluations | Filtering out every inactive student would hide past results. A withdrawal-date cutoff would need new data and could still hide valid records. |
| Check account status when validating each token | Waiting for the two-hour token expiry would break FR-SEC-active-account. Reuse the current user repository instead of adding a token blocklist. |
| Use a separate lifecycle permission check | The existing user-ownership manager also admits the student herself. That is too broad for this action. |
| Accept repeated deactivation | The first request may have succeeded even if its response was lost. Returning a conflict would make that retry harder to handle. |
| Check new evaluation targets on the server | Hiding a teammate in the page alone does not stop a stale page or a direct API request. Saved evaluations keep their existing edit window. |

## Data and shared code

There is no schema change. Reuse the [student model](../../backend/src/main/java/team/projectpulse/student/Student.java) and the SRS's [Business Domain Model](../requirements/software-requirements-specification.md#business-domain-model). Deactivation changes only `enabled`; it does not call a delete method or change enrollment, team membership, activities, evaluations, or ratings.

The first implementation commit contains the shared JWT and evaluation changes with their tests. Review that commit before adding the endpoint and UI. Keep the new route's permission rule in a separate commit so it is easy to review. These changes stay within the existing component boundaries.

## Test list

Each row states an expected result from the use case or a cited rule. Unit tests use the project's JUnit 5 and Mockito pattern. API tests use the existing MySQL test containers. UI tests use the existing Cypress setup.

| ID | Flow or rule | Level | Expected result |
|---|---|---|---|
| T01 | Main scenario; BR-student-deactivation-membership | Integration | An assigned instructor deactivates the student. A fresh database read shows `enabled: false`; enrollment, team, activities, evaluations, and ratings are preserved. Include a student without a team. |
| T02 | BR-student-lifecycle | Integration | The course owner can deactivate a student even when she is not separately listed as an instructor of the course section. |
| T03 | Extension 1a | Integration | Missing credentials and an inactive caller are refused with `401`; the target is unchanged. |
| T04 | Extension 2a | Integration | Student self, another student, an unassigned instructor, an admin of another course, and an unknown target receive `403`; nothing changes. |
| T05 | Extension 4a | UI integration | Cancel, Escape, and close dismiss the warning and send no deactivation request. |
| T06 | Extension 6a | Unit and integration | Repeating an authorized request succeeds without another save or changes to retained data. |
| T07 | Extension 6b | Integration | An instructor removed from the course section after loading the warning cannot confirm deactivation. |
| T08 | Extension 6c | Integration | Force a persistence failure. The API returns failure; a fresh read shows that the account change rolled back. |
| T09 | Extension 6d | UI integration | A network failure shows an unknown result, no success message, and no automatic retry. |
| T10 | FR-SEC-active-account | Integration | A student's token works before deactivation and receives `401` afterward. Fresh login also fails. Another active student still has access. |
| T11 | JWT validation | Unit | Missing or invalid `userId`, a deleted account, and inactive student or instructor accounts fail validation. Keep the existing token-expiry checks. |
| T12 | BR-submission-owed | Unit | The inactive student owes neither item. Active teammates no longer owe an evaluation of her. |
| T13 | BR-evaluation-active-evaluatee | Unit and integration | A new evaluation of an inactive teammate is rejected, including a stale-page request. An active teammate remains a valid target. |
| T14 | BR-deactivated-student-report | Unit | An inactive student with saved evaluations appears for that week, including zero scores and a retained result after a separate team removal. An inactive student with no results for that week is omitted. |
| T15 | Separate confirmed action | Unit and integration | Profile editing cannot deactivate an active account. The existing inactive-to-active profile update still works. |
| T16 | Steps 4–6; BR-evaluation-active-evaluatee | UI integration | Show the target and warning, send one confirmed request, prevent duplicate clicks, and refresh status after success. New evaluation targets omit inactive teammates. Existing saved evaluations keep their normal editing path. |

## Limits to check during implementation

- Account validation adds a user lookup per authenticated request. A failed lookup must deny access.
- Requests accepted before deactivation finishes may complete, as stated in the use case.
- The design does not build UC-STU-reactivate-student. Its Associated Information records the membership decision for later work.
- The build PR must report which tests actually ran. Test code alone is not verification.
