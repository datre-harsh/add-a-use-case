// Running-app check against the seeded development API. No API response is stubbed.
// Oracle: T01/main step 6, T10/FR-SEC-active-account and T16 in docs/design/stu.md.
describe('Student deactivation in the running app', () => {
  const apiBaseUrl = Cypress.env('apiBaseUrl') || 'http://localhost/api/v1'
  let teacher
  let studentToken
  let originalStudent

  before(() => {
    cy.request({
      method: 'POST',
      url: `${apiBaseUrl}/users/login`,
      auth: { user: 'b.gates@abc.edu', pass: '123456' },
      log: false
    }).then(({ body }) => {
      expect(body.flag).to.eq(true)
      teacher = body.data
    })
    cy.request({
      method: 'POST',
      url: `${apiBaseUrl}/users/login`,
      auth: { user: 'j.smith@abc.edu', pass: '123456' },
      log: false
    }).then(({ body }) => {
      expect(body.flag).to.eq(true)
      expect(body.data.userInfo.id).to.eq(4)
      studentToken = body.data.token
    })
    cy.then(() => {
      cy.request({
        url: `${apiBaseUrl}/students/4`,
        headers: { Authorization: `Bearer ${teacher.token}` },
        log: false
      }).then(({ body }) => {
        expect(body.flag).to.eq(true)
        expect(body.data.enabled).to.eq(true)
        originalStudent = body.data
      })
    })
  })

  after(() => {
    if (teacher && originalStudent) {
      // Restore the seeded account through the existing profile reactivation path, even after a failed assertion.
      cy.request({
        method: 'PUT',
        url: `${apiBaseUrl}/students/4`,
        headers: { Authorization: `Bearer ${teacher.token}` },
        body: { ...originalStudent, enabled: true },
        log: false
      }).then(({ body }) => {
        expect(body.flag).to.eq(true)
        expect(body.data.enabled).to.eq(true)
      })
      cy.request({
        url: `${apiBaseUrl}/students/4`,
        headers: { Authorization: `Bearer ${teacher.token}` },
        log: false
      }).then(({ body }) => {
        expect(body.data.enabled).to.eq(true)
      })
    }
  })

  it('confirms the real change and refuses the student token and fresh login (T01/T10/T16)', () => {
    cy.intercept('POST', '**/students/search*').as('liveStudents')
    cy.intercept('GET', '**/students/4').as('liveStudent')
    cy.intercept('PUT', '**/students/4/deactivation').as('liveDeactivation')
    cy.visit('/students', {
      onBeforeLoad(win) {
        win.localStorage.setItem('token', JSON.stringify({ token: teacher.token }))
        win.localStorage.setItem('userInfo', JSON.stringify({ userInfo: teacher.userInfo }))
        win.localStorage.setItem(
          'settings',
          JSON.stringify({ defaultSectionId: 2, defaultCourseId: 1 })
        )
      }
    })
    cy.wait('@liveStudents')
    cy.get('[data-cy=student-edit-4]').click()
    cy.wait('@liveStudent')
    cy.get('[data-cy=deactivate-open]').click()
    cy.get('[data-cy=deactivation-dialog]').should('be.visible')
    cy.get('[data-cy=deactivation-target]').should('contain', 'John Smith').and('contain', 'ID: 4')
    cy.screenshot('student-deactivation-live-warning')
    cy.get('[data-cy=deactivation-confirm]').click()
    cy.wait('@liveDeactivation').then(({ response }) => {
      expect(response.statusCode).to.eq(200)
      expect(response.body.flag).to.eq(true)
      expect(response.body.data.enabled).to.eq(false)
    })
    cy.wait('@liveStudents')
    cy.get('.el-table').should('contain', 'Disabled')
    cy.get('@liveDeactivation.all').should('have.length', 1)

    cy.request({
      url: `${apiBaseUrl}/students/4`,
      headers: { Authorization: `Bearer ${studentToken}` },
      failOnStatusCode: false,
      log: false
    }).then(({ status, body }) => {
      expect(status).to.eq(401)
      expect(body.flag).to.eq(false)
    })
    cy.request({
      method: 'POST',
      url: `${apiBaseUrl}/users/login`,
      auth: { user: 'j.smith@abc.edu', pass: '123456' },
      failOnStatusCode: false,
      log: false
    }).then(({ status, body }) => {
      expect(status).to.eq(401)
      expect(body.flag).to.eq(false)
    })
  })
})
