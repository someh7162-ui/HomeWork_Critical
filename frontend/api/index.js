// API 基础配置
const DEFAULT_SERVER_ORIGIN = 'http://121.196.197.223:8099'
const SERVER_ORIGIN_KEY = 'serverOrigin'

let token = uni.getStorageSync('token') || ''
let serverOrigin = resolveInitialServerOrigin()

export function getServerOrigin() {
    return serverOrigin
}

export function setServerOrigin(origin) {
    serverOrigin = normalizeServerOrigin(origin)
    uni.setStorageSync(SERVER_ORIGIN_KEY, serverOrigin)
    return serverOrigin
}

export function getBaseUrl() {
    return serverOrigin + '/api'
}

function normalizeServerOrigin(origin) {
    let value = String(origin || '').trim()
    if (!value) return DEFAULT_SERVER_ORIGIN
    if (!/^https?:\/\//i.test(value)) {
        value = 'http://' + value
    }
    value = value.replace(/\/+$/, '')
    value = value.replace(/\/api$/i, '')
    value = value.replace(/\/+$/, '')
    return value || DEFAULT_SERVER_ORIGIN
}

function resolveInitialServerOrigin() {
    const storedOrigin = normalizeServerOrigin(uni.getStorageSync(SERVER_ORIGIN_KEY) || DEFAULT_SERVER_ORIGIN)
    const isLocalDemoOrigin = /^https?:\/\/(localhost|127\.0\.0\.1|10\.|172\.(1[6-9]|2\d|3[0-1])\.|192\.168\.)/i.test(storedOrigin)
    const origin = isLocalDemoOrigin ? DEFAULT_SERVER_ORIGIN : storedOrigin
    uni.setStorageSync(SERVER_ORIGIN_KEY, origin)
    return origin
}

function request(url, options = {}) {
    return new Promise((resolve, reject) => {
        const currentToken = getToken()
        uni.request({
            url: getBaseUrl() + url,
            method: options.method || 'GET',
            data: normalizeRequestData(options.data),
            header: {
                ...(options.header || {}),
                'Authorization': currentToken ? 'Bearer ' + currentToken : '',
                'Content-Type': (options.header && options.header['Content-Type'])
                    ? options.header['Content-Type']
                    : (options.method === 'UPLOAD' ? 'multipart/form-data' : 'application/json')
            },
            success(res) {
                if (res.statusCode === 401) {
                    handleUnauthorized()
                    return
                }
                if (res.statusCode === 200) {
                    resolve(res.data)
                } else {
                    const errorData = normalizeResponseData(res.data)
                    uni.showToast({ title: errorData.message || '请求失败', icon: 'none' })
                    reject(errorData)
                }
            },
            fail(err) {
                uni.showToast({ title: '网络请求失败', icon: 'none' })
                reject(err)
            }
        })
    })
}

function normalizeRequestData(data) {
    if (data === undefined || data === null) return data
    try {
        return JSON.parse(JSON.stringify(data))
    } catch (e) {
        return data
    }
}

function handleUnauthorized() {
    token = ''
    uni.removeStorageSync('token')
    uni.removeStorageSync('teacherInfo')
    uni.removeStorageSync('studentInfo')
    uni.removeStorageSync('userRole')
    uni.showToast({ title: '登录已过期，请重新登录', icon: 'none' })
    setTimeout(() => {
        uni.reLaunch({ url: '/pages/login/login' })
    }, 300)
}

function normalizeResponseData(data) {
    if (typeof data === 'string') {
        try {
            return JSON.parse(data)
        } catch (e) {
            return { code: 500, message: data || '请求失败' }
        }
    }
    return data || { code: 500, message: '请求失败' }
}

function parseStoredJson(key) {
    const value = uni.getStorageSync(key)
    if (!value) return null
    if (typeof value === 'object') return value
    try {
        return JSON.parse(value)
    } catch (e) {
        return null
    }
}

function upload(url, filePath, options = {}) {
    return new Promise((resolve, reject) => {
        const currentToken = getToken()
        uni.uploadFile({
            url: getBaseUrl() + url,
            filePath: filePath,
            name: options.name || 'file',
            timeout: options.timeout || 120000,
            formData: options.formData || {},
            header: {
                'Authorization': currentToken ? 'Bearer ' + currentToken : ''
            },
            success(res) {
                if (res.statusCode === 401) {
                    handleUnauthorized()
                    reject({ code: 401, message: '登录已过期' })
                    return
                }
                const data = normalizeResponseData(res.data)
                if (res.statusCode === 200) {
                    resolve(data)
                } else {
                    uni.showToast({ title: data.message || options.errorMessage || '上传失败', icon: 'none' })
                    reject(data)
                }
            },
            fail(err) {
                uni.showToast({ title: options.networkMessage || '网络请求失败', icon: 'none' })
                reject(err)
            }
        })
    })
}

function uploadMany(url, filePaths, options = {}) {
    return new Promise((resolve, reject) => {
        const currentToken = getToken()
        const files = (filePaths || []).map(path => ({ name: options.name || 'files', uri: path }))
        uni.uploadFile({
            url: getBaseUrl() + url,
            files,
            timeout: options.timeout || 180000,
            formData: options.formData || {},
            header: {
                'Authorization': currentToken ? 'Bearer ' + currentToken : ''
            },
            success(res) {
                if (res.statusCode === 401) {
                    handleUnauthorized()
                    reject({ code: 401, message: '登录已过期' })
                    return
                }
                const data = normalizeResponseData(res.data)
                if (res.statusCode === 200) {
                    resolve(data)
                } else {
                    uni.showToast({ title: data.message || options.errorMessage || '上传失败', icon: 'none' })
                    reject(data)
                }
            },
            fail(err) {
                uni.showToast({ title: options.networkMessage || '网络请求失败', icon: 'none' })
                reject(err)
            }
        })
    })
}

// 设置 token
export function setToken(t) {
    token = t
    uni.setStorageSync('token', t)
}

export function getToken() {
    token = uni.getStorageSync('token') || token || ''
    return token
}

export function logout() {
    token = ''
    uni.removeStorageSync('token')
    uni.removeStorageSync('teacherInfo')
    uni.removeStorageSync('studentInfo')
    uni.removeStorageSync('userRole')
}

export function getTeacherInfo() {
    return parseStoredJson('teacherInfo')
}

export function setTeacherInfo(info) {
    uni.setStorageSync('teacherInfo', JSON.stringify(info || {}))
    uni.setStorageSync('userRole', 'TEACHER')
}

export function updateProfile(data) {
    return request('/auth/profile', { method: 'PUT', data })
}

export function getStudentInfo() {
    return parseStoredJson('studentInfo')
}

export function setStudentInfo(info) {
    uni.setStorageSync('studentInfo', JSON.stringify(info || {}))
    uni.setStorageSync('userRole', 'STUDENT')
}

export function getUserRole() {
    return uni.getStorageSync('userRole') || 'TEACHER'
}

export function saveQuotaSummary(quotaSummary) {
    const info = getTeacherInfo() || {}
    info.quotaSummary = quotaSummary || null
    setTeacherInfo(info)
    return info
}

// 认证
export function login(data) {
    return request('/auth/login', {
        method: 'POST',
        header: {
            'Content-Type': 'application/json'
        },
        data: {
            username: (data.username || '').trim(),
            password: data.password || ''
        }
    })
}

export function registerTeacher(data) {
    return request('/auth/register', {
        method: 'POST',
        header: {
            'Content-Type': 'application/json'
        },
        data: {
            username: (data.username || '').trim(),
            password: data.password || '',
            name: (data.name || '').trim(),
            subject: (data.subject || '').trim()
        }
    })
}

export function wechatLogin(code) {
    return request('/auth/wechat-login', { method: 'POST', data: { code } })
}

export function studentLogin(data) {
    return request('/auth/student-login', {
        method: 'POST',
        data: {
            username: (data.username || '').trim(),
            password: data.password || ''
        }
    })
}

export function getQuotaSummary() {
    return request('/quota/me')
}

export function createPaymentOrder(channel = 'WECHAT') {
    return request('/pay/orders', {
        method: 'POST',
        data: { channel }
    })
}

export function mockPayOrder(orderNo) {
    return request('/pay/orders/' + orderNo + '/mock-pay', {
        method: 'POST'
    })
}

// 作业
export function createAssignment(data) {
    return request('/assignments', { method: 'POST', data })
}

export function getAssignments() {
    return request('/assignments')
}

export function getAssignmentDetail(id) {
    return request('/assignments/' + id)
}

export function deleteAssignment(id) {
    return request('/assignments/' + id, { method: 'DELETE' })
}

export function uploadAssignmentAnswerKey(assignmentId, filePaths) {
    return uploadMany('/assignments/' + assignmentId + '/answer-key', filePaths, {
        timeout: 180000,
        errorMessage: '答案页上传失败',
        networkMessage: '网络请求失败'
    })
}

export function getAssignmentAnswerKey(assignmentId) {
    return request('/assignments/' + assignmentId + '/answer-key')
}

export function updateAssignmentAnswerKey(assignmentId, answerKey) {
    return request('/assignments/' + assignmentId + '/answer-key', {
        method: 'PUT',
        data: answerKey
    })
}

// 学生
export function addStudent(data) {
    return request('/students', { method: 'POST', data })
}

export function getStudents(className) {
    const query = className ? '?className=' + encodeURIComponent(className) : ''
    return request('/students' + query)
}

export function getStudentClasses() {
    return request('/students/classes')
}

export function deleteStudent(id) {
    return request('/students/' + id, { method: 'DELETE' })
}

// 花名册
export function rosterOcr(filePath) {
    return upload('/students/roster-ocr', filePath, {
        timeout: 120000,
        errorMessage: '花名册识别失败',
        networkMessage: '网络请求失败'
    })
}

export function batchAddStudents(data) {
    return request('/students/batch', { method: 'POST', data })
}

export function deleteClass(className) {
    return request('/students/class?className=' + encodeURIComponent(className), { method: 'DELETE' })
}

// 作文批改
export function uploadEssay(filePath, assignmentId, studentId) {
    return upload('/essays/upload', filePath, {
        timeout: 180000,
        formData: {
            assignmentId: String(assignmentId),
            studentId: String(studentId)
        },
        errorMessage: '上传失败',
        networkMessage: '网络请求失败'
    })
}

export function uploadWorkbook(files, assignmentId, studentId) {
    return uploadMany('/essays/upload-workbook', files, {
        timeout: 240000,
        formData: {
            assignmentId: String(assignmentId),
            studentId: String(studentId)
        },
        errorMessage: '练习册上传失败',
        networkMessage: '网络请求失败'
    })
}

// OCR 单独识别（用于拍照上传范文、题目等）
export function ocrRecognize(filePath) {
    return upload('/ocr/recognize', filePath, {
        timeout: 120000,
        errorMessage: 'OCR识别失败',
        networkMessage: '网络请求失败，请检查网络连接'
    })
}

export function getEssayDetail(id) {
    return request('/essays/' + id)
}

export function getEssayProgress(id) {
    return request('/essays/' + id + '/progress')
}

export function getEssaysByAssignment(assignmentId) {
    return request('/essays/assignment/' + assignmentId)
}

export function regradeEssay(id) {
    return request('/essays/' + id + '/regrade', { method: 'POST' })
}

export function updateEssayFinalScore(id, finalScore) {
    return request('/essays/' + id + '/final-score', {
        method: 'PUT',
        data: { finalScore }
    })
}

export function reviewEssay(id, data) {
    return request('/essays/' + id + '/review', {
        method: 'PUT',
        data
    })
}

export function updateAssignment(id, data) {
    return request('/assignments/' + id, {
        method: 'PUT',
        data
    })
}

export function exportEssayExcel(assignmentId, title) {
    return request('/essays/assignment/' + assignmentId + '/export?title=' + encodeURIComponent(title),
        { responseType: 'arraybuffer' })
}

export function getAssignmentStats(assignmentId) {
    return request('/essays/assignment/' + assignmentId + '/stats')
}

export function getAssignmentSubmissions(assignmentId) {
    return request('/essays/assignment/' + assignmentId + '/submissions')
}

// 学生端
export function getStudentAssignments() {
    return request('/student/assignments')
}

export function getStudentAssignmentDetail(assignmentId) {
    return request('/student/assignments/' + assignmentId)
}

export function submitStudentAssignment(assignmentId, filePath) {
    return upload('/student/assignments/' + assignmentId + '/submit', filePath, {
        timeout: 180000,
        errorMessage: '提交失败',
        networkMessage: '网络请求失败'
    })
}

export function submitStudentWorkbook(assignmentId, files) {
    return uploadMany('/student/assignments/' + assignmentId + '/workbook-submit', files, {
        timeout: 240000,
        errorMessage: '练习册提交失败',
        networkMessage: '网络请求失败'
    })
}

export function getStudentEssayProgress(essayId) {
    return request('/student/essays/' + essayId + '/progress')
}

export function getStudentEssayDetail(essayId) {
    return request('/student/essays/' + essayId)
}

export function getStudentSummary() {
    return request('/student/summary')
}

export function getFeedbackList() {
    return request('/feedback')
}

export function getFeedbackSummary() {
    return request('/feedback/summary')
}

export function submitFeedback(data) {
    return request('/feedback', { method: 'POST', data })
}

export function getStudentFeedbackList() {
    return request('/student/feedback')
}

export function getStudentFeedbackSummary() {
    return request('/student/feedback/summary')
}

export function submitStudentFeedback(data) {
    return request('/student/feedback', { method: 'POST', data })
}

export function redeemCard(cardKey) {
    return request('/auth/redeem', { method: 'POST', data: { cardKey } })
}
