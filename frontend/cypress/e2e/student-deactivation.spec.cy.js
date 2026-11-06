// UC-STU-deactivate-student UI oracles come from stu.md T05,T08,T09,T16.
// These are browser integration tests with stubbed API responses; MySQL/API tests prove persistence and permissions.
const student = {
  id: 4,
  username: 'j.smith@abc.edu',
  firstName: 'John',
  lastName: 'Smith',
  email: 'j.smith@abc.edu',
  enabled: true,
  roles: 'student',
  sectionId: 2,
  sectionName: '2023-2024',
  teamId: 1,
  teamName: 'Team1'
}
const result = (data) => ({ flag: true, code: 200, message: 'Success', data })

function visitAs(path, userInfo) {
  cy.visit(path, {
    onBeforeLoad(win) {
      const payload = win.btoa(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + 3600 }))
      win.localStorage.setItem('token', JSON.stringify({ token: `e30.${payload}.signature` }))
      win.localStorage.setItem('userInfo', JSON.stringify({ userInfo }))
      win.localStorage.setItem(
        'settings',
        JSON.stringify({ defaultSectionId: 2, defaultCourseId: 1 })
      )
    }
  })
}

describe('Confirmed student deactivation', () => {
  let currentStudent

  beforeEach(() => {
    currentStudent = { ...student }
    cy.intercept('POST', '**/students/search*', (request) =>
      request.reply(
        result({
          content: [currentStudent],
          number: 0,
          size: 50,
          totalElements: 1
        })
      )
    ).as('students')
    cy.intercept('GET', '**/students/4', (request) => request.reply(result(currentStudent))).as(
      'student'
    )
    cy.intercept('PUT', '**/students/4/deactivation', (request) => {
      currentStudent = { ...currentStudent, enabled: false }
      request.reply({ delay: 700, body: result(currentStudent) })
    }).as('deactivation')
    visitAs('/students', { id: 2, firstName: 'Bill', lastName: 'Gates', roles: 'instructor' })
    cy.wait('@students')
    cy.get('[data-cy=student-edit-4]').click()
    cy.wait('@student')
  })

  function openWarning() {
    cy.get('[data-cy=deactivate-open]').click()
    cy.get('[data-cy=deactivation-dialog]').should('be.visible')
  }

  function noSuccess() {
    cy.contains('.el-message--success', 'Student deactivated successfully').should('not.exist')
  }

  it('identifies the target and explains every warning effect before sending anything (T16)', () => {
    openWarning()
    cy.get('[data-cy=deactivation-target]').should('contain', 'John Smith').and('contain', 'ID: 4')
    cy.get('[data-cy=deactivation-dialog]').within(() => {
      for (const effect of [
        'protected request',
        'future logins',
        'already accepted',
        'already displayed',
        'no submission reminders',
        'no longer be asked or allowed',
        'enrollment',
        'team assignment',
        'weekly activity reports',
        'scores, ratings, and comments',
        'Saved evaluations remain editable',
        'Earlier report results',
        'does not delete',
        'reactivated'
      ]) {
        cy.contains(effect).should('be.visible')
      }
    })
    cy.get('@deactivation.all').should('have.length', 0)
    cy.screenshot('student-deactivation-warning')
  })

  for (const dismissal of ['Cancel', 'Escape', 'close']) {
    it(`sends no request when the warning is dismissed with ${dismissal} (T05 / 4a)`, () => {
      openWarning()
      if (dismissal === 'Cancel') cy.get('[data-cy=deactivation-cancel]').click()
      else if (dismissal === 'Escape') cy.get('body').type('{esc}')
      else cy.get('[data-cy=deactivation-dialog] .el-dialog__headerbtn').click()
      cy.get('[data-cy=deactivation-dialog]').should('not.be.visible')
      cy.get('@deactivation.all').should('have.length', 0)
      cy.get('[data-cy=student-status]').should('contain', 'Active')
    })
  }

  it('sends one confirmed request and refreshes the list after success (T16)', () => {
    openWarning()
    cy.get('[data-cy=deactivation-confirm]').click()
    cy.get('[data-cy=deactivation-confirm]').should('be.disabled').click({ force: true })
    cy.wait('@deactivation').then(({ request }) => {
      expect([undefined, null, '']).to.include(request.body)
    })
    cy.get('@deactivation.all').should('have.length', 1)
    cy.wait('@students')
    cy.get('.el-table').should('contain', 'Disabled')
    cy.contains('.el-message--success', 'Student deactivated successfully').should('be.visible')
  })

  it('shows a save failure without success or retry (T08 / 6c)', () => {
    cy.intercept('PUT', '**/students/4/deactivation', {
      statusCode: 500,
      body: { flag: false, code: 500, message: 'Deactivation failed' }
    }).as('failed')
    openWarning()
    cy.get('[data-cy=deactivation-confirm]').click()
    cy.wait('@failed')
    cy.get('[data-cy=deactivation-error]').should('contain', 'Deactivation failed')
    cy.get('[data-cy=student-status]').should('contain', 'Active')
    cy.get('@failed.all').should('have.length', 1)
    noSuccess()
  })

  it('treats an unusable success response as unknown (T09 / 6d)', () => {
    cy.intercept('PUT', '**/students/4/deactivation', { body: result(student) }).as('unusable')
    openWarning()
    cy.get('[data-cy=deactivation-confirm]').click()
    cy.wait('@unusable')
    cy.get('[data-cy=deactivation-error]')
      .should('contain', 'result is unknown')
      .and('contain', 'Reload')
    cy.get('[data-cy=deactivate-open]').should('be.disabled')
    cy.get('@unusable.all').should('have.length', 1)
    noSuccess()
  })

  it('shows an unknown network result and reloads a committed change without retrying (T09 / 6d)', () => {
    cy.intercept('PUT', '**/students/4/deactivation', (request) => {
      currentStudent = { ...currentStudent, enabled: false } // Commit before the response exceeds Axios's 10-second timeout.
      request.reply({ delay: 11000, body: result(currentStudent) })
    }).as('lost')
    openWarning()
    cy.get('[data-cy=deactivation-confirm]').click()
    cy.get('[data-cy=deactivation-error]', { timeout: 15000 })
      .should('contain', 'result is unknown')
      .and('contain', 'Reload')
    cy.get('@lost.all').should('have.length', 1)
    noSuccess()
    cy.get('[data-cy=deactivation-reload]').click()
    cy.wait('@student')
    cy.get('[data-cy=deactivate-open]').should('not.exist')
    cy.get('.el-switch').should('not.have.class', 'is-checked')
    cy.get('@lost.all').should('have.length', 1)
    noSuccess()
  })

  it('treats a gateway HTML response after a committed change as unknown (T09 / 6d)', () => {
    cy.intercept('PUT', '**/students/4/deactivation', (request) => {
      currentStudent = { ...currentStudent, enabled: false }
      request.reply({ statusCode: 504, body: '<html>Gateway timeout</html>' })
    }).as('gateway')
    openWarning()
    cy.get('[data-cy=deactivation-confirm]').click()
    cy.wait('@gateway')
    cy.get('[data-cy=deactivation-error]').should('contain', 'result is unknown')
    cy.get('[data-cy=deactivate-open]').should('be.disabled')
    cy.get('@gateway.all').should('have.length', 1)
    noSuccess()
    cy.get('[data-cy=deactivation-reload]').click()
    cy.wait('@student')
    cy.get('[data-cy=deactivate-open]').should('not.exist')
    cy.get('.el-switch').should('not.have.class', 'is-checked')
    cy.get('@gateway.all').should('have.length', 1)
    noSuccess()
  })

  it('does not show success for a failed Result in an HTTP 200 response (T08)', () => {
    cy.intercept('PUT', '**/students/4/deactivation', {
      body: { flag: false, code: 500, message: 'Save failed', data: null }
    }).as('failedResult')
    openWarning()
    cy.get('[data-cy=deactivation-confirm]').click()
    cy.wait('@failedResult')
    cy.get('[data-cy=deactivation-error]').should('contain', 'Deactivation failed')
    noSuccess()
  })
})

describe('Peer evaluation targets after deactivation', () => {
  const inactive = { ...student, id: 5, firstName: 'Eric', lastName: 'Hudson', enabled: false }
  const saved = {
    evaluationId: 20,
    week: '2023-W32',
    evaluatorId: 4,
    evaluateeId: 5,
    evaluateeName: 'Eric Hudson',
    ratings: [{ criterionId: 1, actualScore: 8 }],
    publicComment: 'Saved comment',
    privateComment: 'Private'
  }

  function openEvaluations(evaluations) {
    cy.intercept('GET', '**/students/teams/1', { body: result([student, inactive]) })
    cy.intercept('GET', '**/sections/2', { body: result({ rubricId: 1 }) })
    cy.intercept('GET', '**/rubrics/1', {
      body: result({
        criteria: [{ criterionId: 1, criterion: 'Quality', description: 'Quality', maxScore: 10 }]
      })
    })
    cy.intercept('GET', '**/evaluations/evaluators/4/week/*', { body: result(evaluations) }).as(
      'evaluations'
    )
    visitAs('/evaluations/submit-evaluations', student)
    cy.wait('@evaluations')
  }

  it('omits inactive teammates from new targets (T16 / BR-evaluation-active-evaluatee)', () => {
    openEvaluations([])
    cy.get('.el-table').should('contain', 'John Smith').and('not.contain', 'Eric Hudson')
    cy.contains('button', 'Submit').scrollIntoView().should('be.visible')
  })

  it('keeps a saved evaluation of an inactive teammate on the update path (T16)', () => {
    cy.intercept('PUT', '**/evaluations/20', { body: result(saved) }).as('update')
    openEvaluations([saved])
    cy.get('.el-table').should('contain', 'Eric Hudson')
    cy.contains('button', 'Update').click()
    cy.wait('@update').its('request.body.evaluateeId').should('eq', 5)
    cy.contains('.el-message--success', 'Peer evaluations updated successfully').should(
      'be.visible'
    )
  })

  it('asks for a reload when a previously active target is refused (T16 / UC-EVA 9a)', () => {
    cy.intercept('POST', '**/evaluations', {
      statusCode: 400,
      body: {
        flag: false,
        code: 400,
        message: 'New peer evaluations require an active teammate. Reload the current targets.'
      }
    }).as('stale')
    openEvaluations([])
    cy.contains('button', 'Submit').click()
    cy.wait('@stale')
    cy.get('[data-cy=evaluation-submit-error]')
      .should('contain', 'Reload the current targets')
      .and('contain', 'Saved evaluations are kept')
    cy.contains('.el-message--success', 'Peer evaluations submitted successfully').should(
      'not.exist'
    )
    cy.contains('button', 'Submit').should('not.be.disabled')
  })
})
