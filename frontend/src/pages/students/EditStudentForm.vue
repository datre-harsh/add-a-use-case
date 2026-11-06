<template>
  <el-form
    ref="studentForm"
    :model="studentBeingEdited"
    :rules="rules"
    label-width="110px"
    style="padding-right: 30px"
    label-position="left"
  >
    <el-form-item label="Id:">
      <el-input v-model="studentBeingEdited.id" disabled></el-input>
    </el-form-item>
    <el-form-item label="Username:" prop="username">
      <el-input v-model="studentBeingEdited.username"></el-input>
    </el-form-item>
    <el-form-item label="Status:" prop="enabled">
      <template v-if="wasActive">
        <el-tag type="success" data-cy="student-status">Active</el-tag>
        <el-button
          type="danger"
          plain
          data-cy="deactivate-open"
          :disabled="!loaded || deactivating || resultUnknown"
          @click="deactivationDialogVisible = true"
          style="margin-left: 12px"
          >Deactivate</el-button
        >
      </template>
      <el-switch
        v-else
        v-model="studentBeingEdited.enabled"
        inline-prompt
        active-text="Enabled"
        inactive-text="Disabled"
        style="--el-switch-on-color: #13ce66; --el-switch-off-color: #ff4949"
      />
    </el-form-item>
    <el-form-item label="First Name:" prop="firstName">
      <el-input v-model="studentBeingEdited.firstName"></el-input>
    </el-form-item>
    <el-form-item label="Last Name:" prop="lastName">
      <el-input v-model="studentBeingEdited.lastName"></el-input>
    </el-form-item>
    <el-form-item label="Email:" prop="email">
      <el-input v-model="studentBeingEdited.email"></el-input>
    </el-form-item>
    <el-form-item label="Team:" prop="teamName">
      <el-input v-model="studentBeingEdited.teamName" disabled></el-input>
    </el-form-item>
    <div style="display: flex; justify-content: end">
      <el-button :disabled="deactivating" @click="$emit('close-dialog')"> Cancel </el-button>
      <el-button
        type="primary"
        :disabled="!loaded || deactivating || resultUnknown"
        @click="updateExistingStudent()"
      >
        Confirm
      </el-button>
    </div>
    <el-alert
      v-if="deactivationError"
      :title="deactivationError"
      type="error"
      :closable="false"
      data-cy="deactivation-error"
      style="margin-top: 16px"
    />
    <el-button v-if="resultUnknown" data-cy="deactivation-reload" @click="reloadStudent"
      >Reload student details</el-button
    >
  </el-form>
  <el-dialog
    v-model="deactivationDialogVisible"
    title="Deactivate student"
    width="min(540px, 90vw)"
    append-to-body
    :close-on-click-modal="!deactivating"
    :close-on-press-escape="!deactivating"
    :show-close="!deactivating"
    data-cy="deactivation-dialog"
  >
    <p data-cy="deactivation-target">
      <strong>{{ studentBeingEdited.firstName }} {{ studentBeingEdited.lastName }}</strong> (ID:
      {{ studentBeingEdited.id }})
    </p>
    <p>
      The student's next protected request and future logins will be refused. Requests already
      accepted may finish, and information already displayed stays on their page.
    </p>
    <p>
      They will receive no submission reminders. Their teammates will no longer be asked or allowed
      to create new peer evaluations of them.
    </p>
    <p>
      The student's enrollment, team assignment, weekly activity reports, peer evaluations, scores,
      ratings, and comments are kept. Saved evaluations remain editable within their normal window.
      Earlier report results stay available under the existing visibility rules.
    </p>
    <p>This does not delete the student. Their account can be reactivated later.</p>
    <template #footer>
      <el-button
        data-cy="deactivation-cancel"
        :disabled="deactivating"
        @click="deactivationDialogVisible = false"
        >Cancel</el-button
      >
      <el-button
        type="danger"
        data-cy="deactivation-confirm"
        :loading="deactivating"
        :disabled="deactivating"
        @click="confirmDeactivation"
        >Deactivate student</el-button
      >
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus'
import type { Student } from '@/apis/student/types'
import type { FormInstance } from 'element-plus'
import { findStudentById, updateStudent, deactivateStudent } from '@/apis/student'
import { isAxiosError } from 'axios'
import { ref, onMounted } from 'vue'

const { studentId } = defineProps(['studentId'])
const emit = defineEmits(['close-dialog'])
const loaded = ref(false)
const wasActive = ref(false)
const deactivating = ref(false)
const deactivationDialogVisible = ref(false)
const deactivationError = ref('')
const resultUnknown = ref(false)

// Note that the structure of the userData is NOT the same as the User interface since the roles are stored as an array
const studentBeingEdited = ref<Student>({
  // Data of the user being added or edited
  id: NaN,
  username: '',
  enabled: false,
  firstName: '',
  lastName: '',
  email: ''
})

const studentForm = ref<FormInstance>() // Form reference

// Validation rules
const rules = {
  username: [
    { required: true, message: 'Please input your email as your username', trigger: 'blur' },
    {
      pattern: /^[^\s@]+@[^\s@]+\.[^\s@]+$/,
      message: 'Enter your email as username.',
      trigger: 'blur'
    }
  ],
  enabled: [{ required: true, message: 'Please select the status of the user.', trigger: 'blur' }],
  firstName: [
    { required: true, message: 'Please provide the first name of the user.', trigger: 'blur' }
  ],
  lastName: [
    { required: true, message: 'Please provide the last name of the user.', trigger: 'blur' }
  ],
  email: [
    { required: true, message: 'Please provide the email of the user.', trigger: 'blur' },
    { type: 'email', message: 'Please provide a valid email.', trigger: 'blur' }
  ]
}

async function loadStudent(studentId: number) {
  // Load the user to be edited
  const result = await findStudentById(studentId)

  studentBeingEdited.value.id = result.data.id as number
  studentBeingEdited.value.username = result.data.username
  studentBeingEdited.value.enabled = result.data.enabled
  studentBeingEdited.value.firstName = result.data.firstName
  studentBeingEdited.value.lastName = result.data.lastName
  studentBeingEdited.value.email = result.data.email
  studentBeingEdited.value.teamName = result.data.teamName
  wasActive.value = result.data.enabled === true
  loaded.value = true
}

async function reloadStudent() {
  await loadStudent(studentId)
  resultUnknown.value = false
  deactivationError.value = ''
}

function isFailedResult(value: unknown): boolean {
  if (!value || typeof value !== 'object') return false
  const result = value as Record<string, unknown>
  return (
    result.flag === false && typeof result.code === 'number' && typeof result.message === 'string'
  )
}

async function confirmDeactivation() {
  if (deactivating.value || !deactivationDialogVisible.value || resultUnknown.value) return
  deactivating.value = true
  deactivationError.value = ''
  try {
    const result = await deactivateStudent(studentId)
    if (
      result.flag === true &&
      result.code === 200 &&
      result.data?.id === studentId &&
      result.data.enabled === false
    ) {
      studentBeingEdited.value = result.data
      wasActive.value = false
      deactivationDialogVisible.value = false
      ElMessage.success('Student deactivated successfully')
      emit('close-dialog')
    } else if (isFailedResult(result)) {
      deactivationError.value = 'Deactivation failed. Reload student details before trying again.'
    } else {
      resultUnknown.value = true
      deactivationError.value =
        'The deactivation result is unknown. Reload student details before trying again.'
    }
  } catch (error) {
    resultUnknown.value = !isAxiosError(error) || !isFailedResult(error.response?.data)
    deactivationError.value = resultUnknown.value
      ? 'The deactivation result is unknown. Reload student details before trying again.'
      : 'Deactivation failed. Reload student details before trying again.'
  } finally {
    deactivating.value = false
    deactivationDialogVisible.value = false
  }
}

onMounted(() => {
  loadStudent(studentId) // Load the artifact to be edited
})

async function updateExistingStudent() {
  if (!loaded.value || deactivating.value || resultUnknown.value) return
  await studentForm.value!.validate() // Validate the form

  const updatedStudent: Student = {
    id: studentBeingEdited.value.id,
    username: studentBeingEdited.value.username,
    enabled: studentBeingEdited.value.enabled,
    firstName: studentBeingEdited.value.firstName,
    lastName: studentBeingEdited.value.lastName,
    email: studentBeingEdited.value.email
  }
  console.log(updatedStudent)
  // Call the API to update the user
  await updateStudent(updatedStudent)

  ElMessage.success('Student updated successfully')
  emit('close-dialog')
}
</script>

<style scoped lang="scss"></style>
