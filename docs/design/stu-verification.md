# Student deactivation verification

This records the implementation evidence for [UC-STU-deactivate-student](../requirements/use-cases.md#uc-stu-deactivate-student-the-course-admininstructor-deactivates-a-student) and the [merged design](stu.md). The 13 cases in `student-deactivation.spec.cy.js` stub the API; they prove page behavior, not database persistence or server permissions. The separate `student-deactivation.live.spec.cy.js` passed against the running application without stubbed responses, including cleanup that restored the seeded account and checked its state with another GET.

## Design test list

| Row | Tests and oracle | Run result |
|---|---|---|
| T01 | `StudentDeactivationIntegrationTest.assignedInstructorDeactivatesAndPreservesTheDatabaseRecord`, `studentWithoutTeamStaysWithoutTeam`; main step 6 and BR-student-deactivation-membership. Scalar snapshots check activities, evaluations and ratings after clearing the persistence context. The live browser test also confirms the real change. | MySQL and live browser passed |
| T02 | `courseOwnerDoesNotNeedSeparateSectionAssignment`, `existingTokenAndFreshLoginFailAfterDeactivationWhileAnotherStudentStillWorks`, `permissionIsRecheckedAfterViewingStudent`; BR-student-lifecycle and details GET contract. | Passed on MySQL |
| T03 | `missingCredentialsAndInactiveCallerAreUnauthorized`; extension 1a requires 401 and no target change. | Passed on MySQL |
| T04 | `unauthorizedCallersAndUnknownTargetAreIndistinguishable`, `StudentDeactivationServiceTest.studentCannotDeactivateHerself`, `StudentLifecycleAuthorizationManagerTest`; extension 2a and BR-student-lifecycle. | Unit and MySQL tests passed |
| T05 | Cypress `sends no request when the warning is dismissed with Cancel`, `Escape`, `close`; extension 4a. | Passed in the 13-case browser run |
| T06 | `authorizedRepeatSucceedsWithoutAnotherWrite`, `repeatRequestChecksPermissionAndDoesNotSaveAgain`; extension 6a. | Unit and MySQL tests passed |
| T07 | `permissionIsRecheckedAfterViewingStudent`, `emptyScopedLookupAfterRouteApprovalIsStillForbidden`, `lostPermissionOrUnknownTargetIsDeniedByScopedLookup`, `detailsLookupAlsoFailsClosedWithinCallerScope`; extension 6b and scoped-lookup contract. | Unit and MySQL tests passed |
| T08 | `saveFailureRollsBackAFlushedChange`, `StudentDeactivationControllerTest.reportsPersistenceFailureWithoutSuccess`; Cypress `shows a save failure without success or retry`, `does not show success for a failed Result in an HTTP 200 response`; extension 6c. | Controller, MySQL and browser tests passed |
| T09 | Cypress `treats an unusable success response as unknown`, `shows an unknown network result and reloads a committed change without retrying`, `treats a gateway HTML response after a committed change as unknown`; extension 6d. Explicit authorized retries are covered by T06. | Passed in the 13-case browser run |
| T10 | `existingTokenAndFreshLoginFailAfterDeactivationWhileAnotherStudentStillWorks`, `ActiveAccountJwtValidatorTest.checksCurrentStatusAgainForTheSameToken`, `JwtDecoderTest.configuredDecoderRechecksAccountStatus`; FR-SEC-active-account. The live browser test also checks the old token and fresh login. | Unit, MySQL and live browser passed |
| T11 | `ActiveAccountJwtValidatorTest`, `JwtDecoderTest`, `invalidClaimsExpiryAndSignatureFailThroughSecurityChain`; FR-SEC-authentication, FR-SEC-active-account and design JWT limits. | 10 unit tests and MySQL tests passed |
| T12 | Existing `SubmissionStatusServiceTest.testWeeklyActivityReportOwedByEligibleStudentsWithNoActivity`, `testPeerEvaluationOwedUntilEveryActiveTeammateIncludingSelfIsEvaluated`, `testFindOwedItemsListsOnlyTheItemsEachStudentOwes`; BR-submission-owed. | Passed |
| T13 | `EvaluationServiceTest.newEvaluationOfInactiveTeammateIsRefused`, `testAddPeerEvaluationSuccess`, `testUpdatePeerEvaluation`, `testUpdatePeerEvaluationAfterTheSubmissionWindowClosed`; `staleNewEvaluationIsRejectedButSavedEvaluationCanStillBeEdited`; BR-evaluation-active-evaluatee and the existing edit window. | Unit and MySQL tests passed |
| T14 | `inactiveStudentKeepsZeroScoresAfterSeparateTeamRemoval`, `inactiveStudentAppearsOnlyInWeeksWithSavedResults`, `activeStudentWithoutTeamIsOmittedFromSectionReport`; BR-deactivated-student-report and BR-evaluation-visibility. | Passed; mutation went red and restored test passed |
| T15 | `StudentServiceTest.profileEditingCannotDeactivateAnActiveStudent`, `profileEditingStillReactivatesAnInactiveStudent`, `testStudentUpdateOwnInfo`, `testInstructorUpdatesStudent`; `profileEditingCannotDeactivateButCanStillReactivate`; separate-action contract. | Unit and corrected MySQL tests passed |
| T16 | Cypress warning, single confirmed request/list refresh, new-target filtering, saved-evaluation updating, and stale-target reload cases; steps 4–6 and BR-evaluation-active-evaluatee. The live browser case checks confirmation and list refresh against the real API. | 13 stubbed cases and 1 live case passed |

## Test oracles

Each line identifies the source of the expected result. Expected values are not copied from the implementation.

| Test | Oracle |
|---|---|
| `ActiveAccountJwtValidatorTest.acceptsActiveStudentAndInstructor` | T11 and FR-SEC-active-account apply to both account types. |
| `rejectsStudentAndInstructorAfterDeactivation` | T11 and FR-SEC-active-account deny otherwise-valid credentials for inactive accounts. |
| `checksCurrentStatusAgainForTheSameToken` | T10 and the UC's Associated Information require refusal on the next protected request. |
| `rejectsMissingOrMalformedUserIdWithoutQueryingAnAccount` | T11 requires invalid or missing identity to fail closed. |
| `acceptsRepresentationsUsedByTheExistingIdentityReader` | T11 preserves valid identity decoding; supported representations come from existing `UserUtils.getUserId`. |
| `rejectsDeletedAccount` | T11 requires refusal when the current account no longer exists. |
| `rejectsWhenAccountLookupFails` | The design's Limits section requires failed account lookups to deny access. |
| `JwtDecoderTest.configuredDecoderRechecksAccountStatus` | T10 requires the configured decoder, not just an unused validator, to revoke current access. |
| `configuredDecoderStillRejectsExpiredToken` | T11 and FR-SEC-authentication require normal expiry validation to remain active. |
| `configuredDecoderStillRejectsAnotherSignature` | T11 and FR-SEC-authentication require the RSA signature check to remain active. |
| `EvaluationServiceTest.newEvaluationOfInactiveTeammateIsRefused` | T13 and BR-evaluation-active-evaluatee forbid new evaluations of inactive teammates. |
| `inactiveStudentKeepsZeroScoresAfterSeparateTeamRemoval` | T14 and BR-deactivated-student-report explicitly keep saved zero scores after a separate team removal; BR-evaluation-visibility withholds private comments from the student summary. |
| `inactiveStudentAppearsOnlyInWeeksWithSavedResults` | T14 selects inactive report rows by saved results in the requested week. |
| `activeStudentWithoutTeamIsOmittedFromSectionReport` | T14 keeps the existing team requirement for active students. |
| `testUpdatePeerEvaluation` (extended) | T13 and BR-evaluation-active-evaluatee keep saved evaluations editable after the evaluatee becomes inactive. |
| `StudentServiceTest.profileEditingCannotDeactivateAnActiveStudent` | T15 and the separate-action contract reject before any profile fields change. |
| `profileEditingStillReactivatesAnInactiveStudent` | T15 preserves the existing inactive-to-active profile update. |
| `StudentServiceTest.testFindStudentById` (adjusted) | T02 and the details GET contract preserve student self access through the scoped finder. |
| `testStudentUpdateOwnInfo` (adjusted) | T15 preserves ordinary student profile edits with active status unchanged; the fixture no longer relies on the old unconfirmed deactivation path. |
| `testInstructorUpdatesStudent` (adjusted) | T15 preserves ordinary instructor profile edits with active status unchanged; the fixture no longer relies on the old unconfirmed deactivation path. |
| `StudentIntegrationTest.testStudentJohnUpdatesOwnInfo` (adjusted) | T15 preserves ordinary profile edits and the existing refusal to change roles; the request keeps the student's active status. |
| `StudentDeactivationServiceTest.deactivationKeepsEnrollmentAndOptionalTeam` | T01 and BR-student-deactivation-membership change only enabled, including students without a team. |
| `repeatRequestChecksPermissionAndDoesNotSaveAgain` | T06 and extension 6a require permission even when the account is already inactive. |
| `studentCannotDeactivateHerself` | T04 and BR-student-lifecycle require an instructor/course owner, not self ownership. |
| `lostPermissionOrUnknownTargetIsDeniedByScopedLookup` | T04/T07 and extensions 2a/6b require a current scoped lookup and neutral refusal. |
| `detailsLookupAlsoFailsClosedWithinCallerScope` | T02/T07 and the details GET contract require the second enforcement point. |
| `StudentLifecycleSecurityTest.instructorPermissionUsesCurrentScopedQuery` | T02/T04/T07 and BR-student-lifecycle require current ownership or section assignment. |
| `studentRoleDoesNotUseTheLifecycleQuery` | T04 excludes student self-deactivation. |
| `databaseFailureDeniesPermission` | The architecture requires route guards to fail closed. |
| `StudentLifecycleAuthorizationManagerTest.usesBoundStudentId` | Architectural authorization conventions require bound path variables. |
| `deniesMissingOrMalformedId` | T04 and extension 2a require unresolved targets to be refused. |
| `StudentDeactivationControllerTest.returnsInactiveStudentInResultEnvelope` | Step 6 and the API contract require an inactive DTO in the Result envelope, with no password. |
| `reportsRefusalWithoutSuccess` | Extension 6b requires refusal rather than success. This standalone test checks the API envelope, not the route rule. |
| `reportsPersistenceFailureWithoutSuccess` | Extension 6c requires a failed API result. This standalone test does not prove rollback. |
| `StudentDeactivationIntegrationTest.assignedInstructorDeactivatesAndPreservesTheDatabaseRecord` | T01/main step 6 and BR-student-deactivation-membership. |
| `studentWithoutTeamStaysWithoutTeam` | T01 and BR-student-deactivation-membership. |
| `courseOwnerDoesNotNeedSeparateSectionAssignment` | T02 and BR-student-lifecycle. |
| `missingCredentialsAndInactiveCallerAreUnauthorized` | T03 and extension 1a. |
| `unauthorizedCallersAndUnknownTargetAreIndistinguishable` | T04 and extension 2a. |
| `authorizedRepeatSucceedsWithoutAnotherWrite` | T06 and extension 6a. |
| `permissionIsRecheckedAfterViewingStudent` | T07 and extension 6b. |
| `emptyScopedLookupAfterRouteApprovalIsStillForbidden` | T07's second-enforcement-point case. |
| `saveFailureRollsBackAFlushedChange` | T08 and extension 6c. The API owns the transaction; a forced exception follows a real flush, and the next repository read checks rollback. |
| `existingTokenAndFreshLoginFailAfterDeactivationWhileAnotherStudentStillWorks` | T10 and FR-SEC-active-account. |
| `invalidClaimsExpiryAndSignatureFailThroughSecurityChain` | T11 and FR-SEC-authentication. |
| `staleNewEvaluationIsRejectedButSavedEvaluationCanStillBeEdited` | T13 and BR-evaluation-active-evaluatee. |
| `profileEditingCannotDeactivateButCanStillReactivate` | T15, tested through permitted student, instructor and admin-only profile callers. |
| Cypress `identifies the target and explains every warning effect before sending anything` | T16 and UC Associated Information. |
| Cypress `sends no request when the warning is dismissed with Cancel` | T05 and extension 4a; Cancel sends zero outgoing requests. |
| Cypress `sends no request when the warning is dismissed with Escape` | T05 and extension 4a; Escape sends zero outgoing requests. |
| Cypress `sends no request when the warning is dismissed with close` | T05 and extension 4a; closing the dialog sends zero outgoing requests. |
| Cypress `sends one confirmed request and refreshes the list after success` | T16 and main steps 5–6. |
| Cypress `shows a save failure without success or retry` | T08 and extension 6c. |
| Cypress `treats an unusable success response as unknown` | T09 and extension 6d. |
| Cypress `shows an unknown network result and reloads a committed change without retrying` | T09 and extension 6d; the simulated write commits before the response exceeds the shared Axios timeout. This proves only the page's handling. |
| Cypress `treats a gateway HTML response after a committed change as unknown` | T09 and extension 6d require an unusable HTTP reply to remain unknown rather than claim the write failed. |
| Cypress `does not show success for a failed Result in an HTTP 200 response` | T08 and the design's successful-Result requirement. |
| Cypress `omits inactive teammates from new targets` | T16 and BR-evaluation-active-evaluatee. |
| Cypress `keeps a saved evaluation of an inactive teammate on the update path` | T16 and the saved-work exception in BR-evaluation-active-evaluatee. |
| Cypress `asks for a reload when a previously active target is refused` | T16 and UC-EVA-submit-evaluation extension 9a. |
| Live Cypress `confirms the real change and refuses the student token and fresh login` | T01/main step 6, T10/FR-SEC-active-account and T16 require the real page, API change, status refresh and access refusal; cleanup restores the seeded account. |

The old ordinary-profile success fixtures were changed to send `enabled: true`, because the previous fixture attempted deactivation through profile saving. The saved-evaluation update test was extended with an inactive evaluatee. No test was deleted to hide a failing requirement.

The first MySQL run passed the 12 existing student API tests and 12 of the 13 new tests. T15's fixture first needed a signed token for its admin-only caller, then an explicit flush before clearing the test-owned transaction for its final fresh read. The reactivation request now uses a plain request map instead of toggling a managed entity to construct its body. The database assertion remains unchanged. The corrected method passed separately; the full 13-test class then passed together on MySQL in 42.37 seconds, with the existing 12 student API tests also passing again.

## Production mutation

The historical-report test was selected because a zero average can be mistaken for no saved result. The oracle is the explicit zero-score and retained-history policy in BR-deactivated-student-report, not the report's implementation.

One production line in `EvaluationService.generateWeeklyPeerEvaluationReportForSection` was changed from `: this.evaluationRepository.existsByWeekAndEvaluateeId(week, student.getId()))` to `: !this.evaluationRepository.existsByWeekAndEvaluateeId(week, student.getId()))`.

Running `EvaluationServiceTest#inactiveStudentKeepsZeroScoresAfterSeparateTeamRemoval` went red: one test ran, one failed, zero errors. Its assertion reported `Expected size: 1 but was: 0`. The line was restored and the same test passed: one test, zero failures, zero errors, zero skips. The mutation is not part of the committed production code.

## Review and corrections

The first trunk checkpoint was `2e03084` (`ActiveAccountJwtValidator` and seven tests); JWT wiring followed separately in `040f20a`. This records the code checkpoint, without claiming the student personally read it.

Checking the implementation against the merged design caught the profile-save rule: T15 applies to every permitted profile caller. The guard now runs before changing fields regardless of role, and API tests include student, instructor and admin-only callers. The details GET also uses its own scoped lookup, as the design requires.

A fresh-context review identified one P2 issue: gateway error replies could have been treated as a definite save failure. Commit `a2bc109` fixes it: only a recognizable failed application `Result` establishes failure; missing or unusable replies require reloading. A 504 HTML response after a simulated commit has its own browser test, and all 13 feature browser cases passed. The reviewer checked the correction and documentation again and found no further concrete issues. This was a static review; the reviewer did not run the suites.

The architecture's account-status paragraph and JWT debt description were updated to describe the implemented account check and the remaining individual-token revocation gap. The matching stale limit in `stu.md` was corrected; the component boundaries, API and data design did not change.

## Checks and limits

- The full backend suite passed against MySQL and Mailpit test containers: 481 tests, zero failures, zero errors, zero skips; Maven reported `BUILD SUCCESS` in 15:42. This includes all 13 new API methods and all 12 existing student API tests, as well as the targeted unit/controller coverage.
- The final standalone `npm run build-only` production bundle passed in 14.22 seconds, and targeted ESLint checks passed for all four changed production frontend files after the gateway fix. The full `npm run build` command fails its type-check step with 18 errors in unchanged files: `SearchPeriod.vue`, `SearchWeek.vue`, `TopNavigationBar.vue`, `SectionsActivities.vue`, `StudentActivities.vue`, `StudentEvaluations.vue`, `RamDocumentEditor.vue`, `Teams.vue`, and `UserRegistration.vue`. An independent type-check of the untouched merged baseline (`6e1bfdd`) produced exactly the same 18 error lines. No reported error is in a changed feature file.
- The full Cypress run completed: 33 tests total, 25 passed, 4 failed and 4 skipped. All 13 stubbed deactivation cases, the live deactivation case and all 9 reminder cases passed. The four failures are in unchanged legacy specs, as detailed below. The [stubbed confirmation screenshot](assets/student-deactivation.png) and [live confirmation screenshot](assets/student-deactivation-live.png) were visually checked.
- No load/performance test or production deployment is claimed. Existing evaluation conversion uses unscoped lookups before its team comparison; this feature preserves that pre-existing path rather than refactoring unrelated authorization code.
- The existing login service returns no token for an account whose only role is `admin`. T15 therefore uses a real signed JWT for the admin-only API caller; admin-only login is unchanged and is not claimed to work.
- This document records automated checks, implementation decisions and the static review above. It does not claim that the student personally reviewed the code or ran the tests.

## Full browser run

The complete six-spec run used the running development API and Electron 118 through Cypress 13.17.0. It completed in 1:37. The full frontend suite is not green.

| Spec | Passed | Failed | Skipped | Observed result |
|---|---:|---:|---:|---|
| `login.spec.cy.js` | 2 | 2 | 0 | `displays the login page correctly` could not find `Login` in an `h1`. `logs in successfully with correct credentials` could not find the success message; it uses `john`, while the seeded student is `j.smith@abc.edu`. |
| `peerevalutions.spec.cy.js` | 0 | 1 | 2 | Its `before each` calls `cy.login('john', '123456')`; the URL stayed `/login` instead of reaching `/home`. The hook failure skipped the remaining two cases. |
| `reminders.spec.cy.js` | 9 | 0 | 0 | All existing reminder browser cases passed. |
| `student-deactivation.live.spec.cy.js` | 1 | 0 | 0 | The real warning, confirmed API change, refreshed status, old-token rejection, fresh-login rejection and account-restoration cleanup passed. No response was stubbed. |
| `student-deactivation.spec.cy.js` | 13 | 0 | 0 | All feature page cases passed, including cancellation, duplicate prevention, failure, timeout/gateway uncertainty and evaluation targets. |
| `weeklyactivities.spec.cy.js` | 0 | 1 | 2 | Its `before each` also logs in as `john`; the URL stayed `/login`. The hook failure skipped the remaining two cases. |

The legacy login, peer-evaluation and weekly-activity specs and their shared login command were not changed in this feature. The four skipped cases therefore remain unverified by this run. The live case used the seeded teacher and student accounts successfully and restored the student to active afterward.
