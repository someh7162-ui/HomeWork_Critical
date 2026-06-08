<script setup>
import { computed, onMounted, reactive, ref } from 'vue'

const API_BASE = import.meta.env.VITE_API_BASE || `${window.location.origin}/api`

const token = ref(localStorage.getItem('adminToken') || '')
const role = ref(localStorage.getItem('adminRole') || 'ADMIN')
const userName = ref(localStorage.getItem('adminName') || '')
const activePage = ref(role.value === 'TEACHER' ? 'teacherDashboard' : 'dashboard')
const loading = ref(false)
const toast = ref('')
const loginMode = ref('ADMIN')
const loginForm = reactive({ username: 'admin', password: 'admin123' })
const grantForm = reactive({ teacherId: '', amount: 100 })
const cardGenForm = reactive({ amount: 500, count: 1 })

const dashboard = ref(null)
const device = ref(null)
const teachers = ref([])
const essays = ref([])
const cards = ref([])
const generatedKeys = ref([])
const teacherDashboard = ref(null)
const teacherAssignments = ref([])

const teacherKeyword = ref('')
const essayKeyword = ref('')
const essayStatus = ref('')
const cardStatus = ref('')

const isLoggedIn = computed(() => Boolean(token.value))
const isTeacher = computed(() => role.value === 'TEACHER')

const navItems = computed(() => isTeacher.value
  ? [
      { id: 'teacherDashboard', label: '教学驾驶舱' },
      { id: 'teacherAssignments', label: '作业数据' },
      { id: 'weakness', label: '薄弱知识点' },
    ]
  : [
      { id: 'dashboard', label: '运营总览' },
      { id: 'device', label: '连接检测' },
      { id: 'teachers', label: '教师管理' },
      { id: 'essays', label: '批改记录' },
      { id: 'cards', label: '卡密管理' },
    ])

const pageTitle = computed(() => {
  const item = navItems.value.find((nav) => nav.id === activePage.value)
  return item?.label || 'PaperCritical'
})

const statCards = computed(() => {
  const data = dashboard.value || {}
  return [
    { label: '教师', value: data.teacherCount ?? 0, note: '已入驻账号' },
    { label: '学生', value: data.studentCount ?? 0, note: '班级花名册' },
    { label: '作业', value: data.assignmentCount ?? 0, note: '已创建任务' },
    { label: '批改', value: data.essayCount ?? 0, note: `今日 ${data.todayEssayCount ?? 0}` },
    { label: '成功率', value: `${data.successRate ?? 100}%`, note: `失败 ${data.failedEssayCount ?? 0}` },
  ]
})

const teacherHeroStats = computed(() => {
  const data = teacherDashboard.value || {}
  return [
    { label: '班级学生', value: data.studentCount ?? 0, note: '当前教师名下' },
    { label: '作业任务', value: data.assignmentCount ?? 0, note: '作文 + 练习册' },
    { label: '提交率', value: `${data.submitRate ?? 0}%`, note: `${data.submittedCount ?? 0} 次提交` },
    { label: '平均分', value: data.averageScore ?? 0, note: `${data.gradedCount ?? 0} 份已批改` },
    { label: '待复核', value: data.reviewRequiredCount ?? 0, note: `${data.processingCount ?? 0} 份批改中` },
  ]
})

const weakPoints = computed(() => teacherDashboard.value?.weakKnowledgePoints || [])
const lessonRecommendations = computed(() => teacherDashboard.value?.lessonRecommendations || [])
const recentAssignments = computed(() => teacherDashboard.value?.recentAssignments || [])

const serviceHealth = computed(() => {
  const item = device.value || {}
  return [
    { label: '后端服务', value: item.backendStatus || 'UNKNOWN', ok: item.backendStatus === 'UP' },
    { label: '上传目录', value: item.uploadWritable ? '可写' : '异常', ok: Boolean(item.uploadWritable) },
    { label: 'OCR 配置', value: item.ocrConfigured ? '已配置' : '未配置', ok: Boolean(item.ocrConfigured) },
    { label: 'AI 配置', value: item.aiConfigured ? '已配置' : '未配置', ok: Boolean(item.aiConfigured) },
  ]
})

const teacherMetrics = computed(() => {
  const remaining = teachers.value.reduce((sum, item) => sum + Number(item.quotaSummary?.remainingTotal || 0), 0)
  const active = teachers.value.filter((item) => Number(item.essayCount || 0) > 0).length
  return [
    { label: '教师总数', value: teachers.value.length },
    { label: '活跃教师', value: active },
    { label: '剩余额度', value: remaining },
  ]
})

const filteredTeachers = computed(() => {
  const kw = teacherKeyword.value.trim().toLowerCase()
  if (!kw) return teachers.value
  return teachers.value.filter((item) => `${item.name || ''} ${item.username || ''} ${item.subject || ''}`.toLowerCase().includes(kw))
})

const filteredEssays = computed(() => {
  const kw = essayKeyword.value.trim().toLowerCase()
  return essays.value.filter((item) => {
    const status = normalizeStatus(item.status)
    const matchStatus = !essayStatus.value || status === essayStatus.value
    const text = `${item.teacherName || ''} ${item.studentName || ''} ${item.className || ''} ${item.assignmentTitle || ''}`.toLowerCase()
    return matchStatus && (!kw || text.includes(kw))
  })
})

const filteredCards = computed(() => {
  if (!cardStatus.value) return cards.value
  return cards.value.filter((item) => item.status === cardStatus.value)
})

function setLoginMode(mode) {
  loginMode.value = mode
  if (mode === 'ADMIN') {
    loginForm.username = 'admin'
    loginForm.password = 'admin123'
  } else {
    loginForm.username = 'teacher1'
    loginForm.password = '123456'
  }
}

async function request(path, options = {}) {
  const res = await fetch(API_BASE + path, {
    method: options.method || 'GET',
    headers: {
      'Content-Type': 'application/json',
      Authorization: token.value ? `Bearer ${token.value}` : '',
    },
    body: options.body ? JSON.stringify(options.body) : undefined,
  })
  const data = await res.json().catch(() => ({ code: 500, message: '接口返回格式错误' }))
  if (res.status === 401 || res.status === 403) {
    logout()
    throw new Error('登录已失效，请重新登录')
  }
  if (!res.ok || data.code !== 200) {
    throw new Error(data.message || '请求失败')
  }
  return data.data
}

function showToast(message) {
  toast.value = message
  window.clearTimeout(showToast.timer)
  showToast.timer = window.setTimeout(() => {
    toast.value = ''
  }, 2200)
}

async function handleLogin() {
  loading.value = true
  try {
    const path = loginMode.value === 'ADMIN' ? '/admin/login' : '/auth/login'
    const data = await request(path, { method: 'POST', body: loginForm })
    token.value = data.token
    role.value = loginMode.value
    userName.value = data.name || (loginMode.value === 'ADMIN' ? '系统管理员' : '教师')
    localStorage.setItem('adminToken', data.token)
    localStorage.setItem('adminRole', role.value)
    localStorage.setItem('adminName', userName.value)
    activePage.value = role.value === 'TEACHER' ? 'teacherDashboard' : 'dashboard'
    await loadAll()
    showToast(role.value === 'TEACHER' ? '已进入教师数据驾驶舱' : '已进入管理面板')
  } catch (error) {
    window.alert(error.message)
  } finally {
    loading.value = false
  }
}

function logout() {
  token.value = ''
  role.value = 'ADMIN'
  userName.value = ''
  localStorage.removeItem('adminToken')
  localStorage.removeItem('adminRole')
  localStorage.removeItem('adminName')
}

async function loadAll() {
  if (!token.value) return
  loading.value = true
  try {
    if (isTeacher.value) {
      await Promise.all([loadTeacherDashboard(), loadTeacherAssignments()])
    } else {
      await Promise.all([loadDashboard(), loadDevice(), loadTeachers(), loadEssays()])
      if (activePage.value === 'cards') await loadCards()
    }
  } catch (error) {
    window.alert(error.message)
  } finally {
    loading.value = false
  }
}

async function openPage(pageId) {
  activePage.value = pageId
  if (pageId === 'cards' && !cards.value.length) await loadCards()
}

async function loadDashboard() {
  dashboard.value = await request('/admin/dashboard')
}

async function loadDevice() {
  device.value = await request('/admin/device')
}

async function loadTeachers() {
  teachers.value = await request('/admin/teachers')
}

async function loadEssays() {
  essays.value = await request('/admin/essays')
}

async function loadCards() {
  cards.value = await request('/admin/cards')
}

async function loadTeacherDashboard() {
  teacherDashboard.value = await request('/essays/teacher-dashboard')
}

async function loadTeacherAssignments() {
  teacherAssignments.value = await request('/assignments')
}

async function grantQuota() {
  if (!grantForm.teacherId || !grantForm.amount) {
    window.alert('请选择教师并填写额度')
    return
  }
  try {
    await request(`/admin/teachers/${grantForm.teacherId}/quota`, {
      method: 'POST',
      body: { amount: Number(grantForm.amount) },
    })
    grantForm.teacherId = ''
    grantForm.amount = 100
    await Promise.all([loadTeachers(), loadDashboard()])
    showToast('额度已增加')
  } catch (error) {
    window.alert(error.message)
  }
}

async function generateCards() {
  if (!cardGenForm.count || cardGenForm.count < 1) {
    window.alert('请输入生成数量')
    return
  }
  try {
    const data = await request('/admin/cards/generate', {
      method: 'POST',
      body: { amount: Number(cardGenForm.amount), count: Number(cardGenForm.count) },
    })
    generatedKeys.value = data.keys || []
    cardGenForm.count = 1
    await loadCards()
    showToast('卡密已生成')
  } catch (error) {
    window.alert(error.message)
  }
}

function imageSrc(imageUrl) {
  if (!imageUrl) return ''
  return API_BASE.replace('/api', '') + '/uploads/' + imageUrl
}

function statusLabel(status) {
  const map = {
    GRADED: '已批改',
    TEACHER_REVIEWED: '已确认',
    FAILED: '失败',
    UPLOADED: '已上传',
    OCR_PROCESSING: '解析中',
    OCR_DONE: '解析完成',
    AI_PROCESSING: 'AI批改中',
  }
  return map[status] || status || '未知'
}

function normalizeStatus(status) {
  if (status === 'GRADED' || status === 'TEACHER_REVIEWED') return 'GRADED'
  if (status === 'FAILED') return 'FAILED'
  return 'PROCESSING'
}

function statusClass(status) {
  const normalized = normalizeStatus(status)
  if (normalized === 'GRADED') return 'status-chip ok'
  if (normalized === 'FAILED') return 'status-chip bad'
  return 'status-chip wait'
}

function assignmentTypeLabel(type) {
  return type === 'WORKBOOK' ? '练习册' : '作文'
}

function formatQuota(summary) {
  if (!summary) return '-'
  return `${summary.remainingTotal ?? 0} 次`
}

function cardStatusLabel(status) {
  return status === 'UNUSED' ? '未使用' : '已使用'
}

function copyText(value) {
  if (!value) return
  navigator.clipboard?.writeText(value)
  showToast('已复制')
}

function copyKeys() {
  if (!generatedKeys.value.length) return
  copyText(generatedKeys.value.join('\n'))
}

onMounted(() => {
  if (token.value) loadAll()
})
</script>

<template>
  <div v-if="!isLoggedIn" class="login-shell">
    <section class="login-card">
      <div class="login-mark">PaperCritical</div>
      <h1>教学数据与运营看板</h1>
      <p class="muted">管理员看全局运营，教师看班级学情、薄弱知识点和备课建议。</p>
      <div class="segmented">
        <button :class="{ active: loginMode === 'ADMIN' }" @click="setLoginMode('ADMIN')">管理员</button>
        <button :class="{ active: loginMode === 'TEACHER' }" @click="setLoginMode('TEACHER')">教师端</button>
      </div>
      <label>账号</label>
      <input v-model="loginForm.username" />
      <label>密码</label>
      <input v-model="loginForm.password" type="password" @keyup.enter="handleLogin" />
      <button class="primary-action" :disabled="loading" @click="handleLogin">
        {{ loading ? '进入中...' : loginMode === 'ADMIN' ? '进入后台' : '进入教师驾驶舱' }}
      </button>
      <p class="hint">{{ loginMode === 'ADMIN' ? '默认 admin / admin123' : '演示 teacher1 / 123456' }}</p>
    </section>
  </div>

  <div v-else class="admin-shell">
    <aside class="sidebar">
      <div class="brand">
        <div class="logo">P</div>
        <div>
          <strong>PaperCritical</strong>
          <small>{{ isTeacher ? 'Teacher Insight' : 'Admin Console' }}</small>
        </div>
      </div>
      <nav>
        <button
          v-for="item in navItems"
          :key="item.id"
          :class="{ active: activePage === item.id }"
          @click="openPage(item.id)"
        >
          {{ item.label }}
        </button>
      </nav>
      <div class="sidebar-footer">
        <button class="soft-button" @click="loadAll">刷新数据</button>
        <button class="text-button" @click="logout">退出登录</button>
      </div>
    </aside>

    <main class="content">
      <header class="topbar">
        <div>
          <p class="eyebrow">{{ isTeacher ? 'TEACHER ANALYTICS' : 'SYSTEM OPS' }}</p>
          <h1>{{ pageTitle }}</h1>
        </div>
        <div class="top-actions">
          <span class="user-pill">{{ userName || (isTeacher ? '教师' : '管理员') }}</span>
          <span v-if="loading" class="loading-dot">同步中</span>
          <button @click="loadAll">刷新</button>
        </div>
      </header>

      <section v-if="isTeacher && activePage === 'teacherDashboard'" class="panel-stack">
        <div class="teacher-hero">
          <div>
            <p class="eyebrow">CLASSROOM COMMAND CENTER</p>
            <h2>从批改结果直接生成备课线索</h2>
            <p>系统会聚合学生提交、AI批改、教师复核和知识点错误，帮助你快速判断下一节课应该讲什么。</p>
          </div>
          <div class="hero-badge">
            <span>提交率</span>
            <strong>{{ teacherDashboard?.submitRate ?? 0 }}%</strong>
          </div>
        </div>

        <div class="stats-grid">
          <article v-for="item in teacherHeroStats" :key="item.label" class="stat accent">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
            <small>{{ item.note }}</small>
          </article>
        </div>

        <div class="teacher-grid">
          <section class="panel">
            <div class="panel-title-row">
              <h2>最近作业表现</h2>
              <span class="subtle">按最新作业排序</span>
            </div>
            <div class="assignment-list">
              <article v-for="item in recentAssignments" :key="item.id" class="assignment-row">
                <div>
                  <strong>{{ item.title }}</strong>
                  <small>{{ item.className || '未设置班级' }} · {{ item.subject || '-' }} · {{ assignmentTypeLabel(item.assignmentType) }}</small>
                </div>
                <div class="progress-cell">
                  <span>{{ item.submittedCount }}/{{ item.expectedCount }}</span>
                  <div class="bar"><i :style="{ width: `${Math.min(item.submitRate || 0, 100)}%` }"></i></div>
                </div>
                <div class="score-chip">{{ item.averageScore }}</div>
              </article>
            </div>
          </section>

          <section class="panel">
            <div class="panel-title-row">
              <h2>备课建议</h2>
              <span class="subtle">AI Agent 汇总</span>
            </div>
            <div class="lesson-list">
              <article v-for="item in lessonRecommendations" :key="item.title" class="lesson-card">
                <strong>{{ item.title }}</strong>
                <p>{{ item.focus }}</p>
                <small>{{ item.reason }}</small>
                <em>{{ item.resourceHint }}</em>
              </article>
            </div>
          </section>
        </div>
      </section>

      <section v-if="isTeacher && activePage === 'teacherAssignments'" class="panel-stack">
        <section class="panel">
          <div class="panel-title-row">
            <h2>我的作业数据</h2>
            <span class="subtle">教师端只看自己的班级</span>
          </div>
          <table>
            <thead>
              <tr>
                <th>作业</th>
                <th>类型</th>
                <th>班级</th>
                <th>科目</th>
                <th>答案页</th>
                <th>创建时间</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in teacherAssignments" :key="item.id">
                <td><strong>{{ item.title }}</strong><br><small>{{ item.questionRange || item.question || '-' }}</small></td>
                <td>{{ assignmentTypeLabel(item.assignmentType) }}</td>
                <td>{{ item.className || '-' }}</td>
                <td>{{ item.subject || '-' }}</td>
                <td>{{ item.answerKeyStatus || '-' }}</td>
                <td>{{ item.createdAt }}</td>
              </tr>
            </tbody>
          </table>
        </section>
      </section>

      <section v-if="isTeacher && activePage === 'weakness'" class="teacher-grid">
        <section class="panel">
          <div class="panel-title-row">
            <h2>知识点薄弱排行</h2>
            <span class="subtle">用于备课优先级</span>
          </div>
          <div class="weak-list">
            <article v-for="(item, index) in weakPoints" :key="item.name" class="weak-row">
              <div class="rank">{{ index + 1 }}</div>
              <div>
                <strong>{{ item.name }}</strong>
                <small>{{ item.suggestion || '建议结合错题做针对讲解' }}</small>
              </div>
              <div class="weak-meter">
                <span>{{ item.wrongRate ?? 0 }}%</span>
                <div class="bar danger"><i :style="{ width: `${Math.min(item.wrongRate || 0, 100)}%` }"></i></div>
              </div>
            </article>
          </div>
        </section>
        <section class="panel prep-panel">
          <h2>课堂行动清单</h2>
          <article v-for="item in lessonRecommendations" :key="item.title" class="prep-item">
            <strong>{{ item.title }}</strong>
            <p>{{ item.focus }}</p>
          </article>
        </section>
      </section>

      <section v-if="!isTeacher && activePage === 'dashboard'" class="panel-stack">
        <div class="hero-panel">
          <div>
            <p class="eyebrow">LIVE OPERATIONS</p>
            <h2>系统演示状态总览</h2>
            <p>展示教师、学生、作业、批改和稳定性指标，适合比赛现场快速说明产品闭环。</p>
          </div>
          <div class="hero-number">
            <span>今日批改</span>
            <strong>{{ dashboard?.todayEssayCount ?? 0 }}</strong>
          </div>
        </div>
        <div class="stats-grid">
          <article v-for="item in statCards" :key="item.label" class="stat">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
            <small>{{ item.note }}</small>
          </article>
        </div>
        <div class="split-grid">
          <section class="panel">
            <div class="panel-title-row">
              <h2>最近批改</h2>
              <button class="mini-button" @click="openPage('essays')">查看全部</button>
            </div>
            <table>
              <thead><tr><th>教师</th><th>学生</th><th>作业</th><th>状态</th><th>分数</th></tr></thead>
              <tbody>
                <tr v-for="item in dashboard?.recentEssays || []" :key="item.id">
                  <td>{{ item.teacherName || '-' }}</td>
                  <td>{{ item.studentName }}</td>
                  <td>{{ item.assignmentTitle }}</td>
                  <td><span :class="statusClass(item.status)">{{ statusLabel(item.status) }}</span></td>
                  <td>{{ item.finalScore ?? item.aiScore ?? '-' }}</td>
                </tr>
              </tbody>
            </table>
          </section>
          <section class="panel">
            <h2>服务健康</h2>
            <div class="health-list">
              <div v-for="item in serviceHealth" :key="item.label" class="health-row">
                <span :class="['health-light', item.ok ? 'ok' : 'bad']"></span>
                <div><strong>{{ item.label }}</strong><small>{{ item.value }}</small></div>
              </div>
            </div>
          </section>
        </div>
      </section>

      <section v-if="!isTeacher && activePage === 'device'" class="panel-stack">
        <div class="connection-card">
          <div>
            <p class="eyebrow">ANDROID BASE CHECK</p>
            <h2>安卓基座连接检测</h2>
            <p>用于确认后端、上传目录、OCR 和 AI 配置是否就绪。</p>
          </div>
          <button @click="loadDevice">重新检测</button>
        </div>
        <div class="stats-grid four">
          <article v-for="item in serviceHealth" :key="item.label" class="stat">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
            <small>{{ item.ok ? '正常' : '需要检查' }}</small>
          </article>
        </div>
        <section class="panel">
          <h2>连接信息</h2>
          <div class="definition-grid">
            <span>服务地址</span><strong>{{ device?.appServerUrl || '-' }}</strong>
            <span>上传目录</span><strong>{{ device?.uploadPath || '-' }}</strong>
            <span>检测时间</span><strong>{{ device?.checkedAt || '-' }}</strong>
          </div>
        </section>
      </section>

      <section v-if="!isTeacher && activePage === 'teachers'" class="panel-stack">
        <div class="stats-grid three">
          <article v-for="item in teacherMetrics" :key="item.label" class="stat">
            <span>{{ item.label }}</span><strong>{{ item.value }}</strong>
          </article>
        </div>
        <section class="panel">
          <div class="panel-title-row">
            <h2>额度发放</h2>
          </div>
          <div class="control-panel">
            <select v-model="grantForm.teacherId">
              <option value="">选择教师</option>
              <option v-for="teacher in teachers" :key="teacher.id" :value="teacher.id">
                {{ teacher.name }}（{{ teacher.username }}）
              </option>
            </select>
            <input v-model.number="grantForm.amount" type="number" min="1" />
            <button @click="grantQuota">发放</button>
          </div>
        </section>
        <section class="panel">
          <div class="panel-title-row">
            <h2>教师列表</h2>
            <div class="filters"><input v-model="teacherKeyword" placeholder="搜索姓名、账号、学科" /></div>
          </div>
          <table>
            <thead><tr><th>ID</th><th>教师</th><th>学科</th><th>学生</th><th>作业</th><th>批改</th><th>额度</th><th>创建时间</th></tr></thead>
            <tbody>
              <tr v-for="teacher in filteredTeachers" :key="teacher.id">
                <td>{{ teacher.id }}</td>
                <td><strong>{{ teacher.name }}</strong><br><small>{{ teacher.username }}</small></td>
                <td>{{ teacher.subject || '-' }}</td>
                <td>{{ teacher.studentCount }}</td>
                <td>{{ teacher.assignmentCount }}</td>
                <td>{{ teacher.essayCount }}</td>
                <td>{{ formatQuota(teacher.quotaSummary) }}<br><small>免费 {{ teacher.quotaSummary?.freeRemaining ?? 0 }} / 付费 {{ teacher.quotaSummary?.paidRemaining ?? 0 }}</small></td>
                <td>{{ teacher.createdAt }}</td>
              </tr>
            </tbody>
          </table>
        </section>
      </section>

      <section v-if="!isTeacher && activePage === 'essays'" class="panel">
        <div class="panel-title-row">
          <h2>批改记录</h2>
          <div class="filters">
            <input v-model="essayKeyword" placeholder="搜索教师、学生、班级、作业" />
            <select v-model="essayStatus">
              <option value="">全部状态</option>
              <option value="GRADED">已批改</option>
              <option value="PROCESSING">处理中</option>
              <option value="FAILED">失败</option>
            </select>
          </div>
        </div>
        <table>
          <thead><tr><th>ID</th><th>教师</th><th>学生</th><th>班级</th><th>作业</th><th>状态</th><th>分数</th><th>图片</th><th>OCR/解析文本</th></tr></thead>
          <tbody>
            <tr v-for="item in filteredEssays" :key="item.id">
              <td>{{ item.id }}</td>
              <td>{{ item.teacherName || '-' }}</td>
              <td :class="{ missing: item.studentMissing }">{{ item.studentName }}</td>
              <td>{{ item.className || '-' }}</td>
              <td>{{ item.assignmentTitle }}</td>
              <td><span :class="statusClass(item.status)">{{ statusLabel(item.status) }}</span></td>
              <td>{{ item.finalScore ?? item.aiScore ?? '-' }}</td>
              <td><a v-if="item.imageUrl" :href="imageSrc(item.imageUrl)" target="_blank">查看</a><span v-else>-</span></td>
              <td class="ocr-cell">{{ item.ocrText || '-' }}</td>
            </tr>
          </tbody>
        </table>
      </section>

      <section v-if="!isTeacher && activePage === 'cards'" class="panel-stack">
        <section class="panel">
          <div class="panel-title-row">
            <h2>生成卡密</h2>
            <button v-if="generatedKeys.length" class="mini-button" @click="copyKeys">复制本次卡密</button>
          </div>
          <div class="control-panel card-control">
            <input v-model.number="cardGenForm.amount" type="number" min="1" placeholder="额度" />
            <input v-model.number="cardGenForm.count" type="number" min="1" placeholder="数量" />
            <button @click="generateCards">生成</button>
          </div>
          <div v-if="generatedKeys.length" class="key-grid">
            <button v-for="key in generatedKeys" :key="key" class="key-item" @click="copyText(key)">{{ key }}</button>
          </div>
        </section>
        <section class="panel">
          <div class="panel-title-row">
            <h2>卡密列表</h2>
            <select v-model="cardStatus">
              <option value="">全部</option>
              <option value="UNUSED">未使用</option>
              <option value="USED">已使用</option>
            </select>
          </div>
          <table>
            <thead><tr><th>卡密</th><th>额度</th><th>状态</th><th>使用教师</th><th>创建时间</th></tr></thead>
            <tbody>
              <tr v-for="item in filteredCards" :key="item.id">
                <td><code>{{ item.cardKey }}</code></td>
                <td>{{ item.amount }}</td>
                <td>{{ cardStatusLabel(item.status) }}</td>
                <td>{{ item.usedByTeacherId || '-' }}</td>
                <td>{{ item.createdAt }}</td>
              </tr>
            </tbody>
          </table>
        </section>
      </section>
    </main>
    <div v-if="toast" class="toast">{{ toast }}</div>
  </div>
</template>
