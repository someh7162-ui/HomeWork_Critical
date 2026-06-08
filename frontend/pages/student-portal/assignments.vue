<template>
    <view class="page-container">
        <view class="student-head">
            <view>
                <text class="student-label">学生端</text>
                <text class="student-name">{{ studentInfo.name || '学生' }}</text>
                <text class="student-class">{{ studentInfo.className || '' }} {{ studentInfo.studentNo || '' }}</text>
            </view>
            <view class="student-actions">
                <button class="feedback-btn" @click="goFeedback">反馈</button>
                <button class="logout-btn" @click="handleLogout">退出</button>
            </view>
        </view>

        <view class="summary-grid">
            <view class="summary-card"><text>作业</text><strong>{{ assignments.length }}</strong></view>
            <view class="summary-card pending"><text>待提交</text><strong>{{ pendingAssignments.length }}</strong></view>
            <view class="summary-card processing"><text>批改中</text><strong>{{ processingAssignments.length }}</strong></view>
            <view class="summary-card done"><text>已完成</text><strong>{{ gradedCount }}</strong></view>
        </view>

        <view v-if="summary" class="analysis-card" @click="showAnalysis = !showAnalysis">
            <view class="analysis-header">
                <text class="analysis-title">📊 学习分析</text>
                <text class="analysis-arrow">{{ showAnalysis ? '▾' : '▸' }}</text>
            </view>
            <view v-if="showAnalysis" class="analysis-body">
                <view class="analysis-row">
                    <text class="analysis-label">历史平均分</text>
                    <text class="analysis-val">{{ summary.averageScore || 0 }}</text>
                </view>
                <view class="analysis-row">
                    <text class="analysis-label">已批改次数</text>
                    <text class="analysis-val">{{ summary.totalEssays || 0 }}</text>
                </view>
                <view v-if="summary.knowledgePoints && summary.knowledgePoints.length" class="kp-section">
                    <text class="analysis-label">涉及知识点</text>
                    <view class="kp-tags">
                        <text v-for="kp in summary.knowledgePoints" :key="kp" class="kp-tag">{{ kp }}</text>
                    </view>
                </view>
                <button class="export-btn" @click.stop="handleExport">📥 导出成绩报告</button>
            </view>
        </view>

        <view v-if="assignments.length === 0" class="empty-state">
            <text class="empty-state-text">暂无作业</text>
        </view>

        <block v-for="section in assignmentSections" :key="section.key">
            <view v-if="section.items.length" class="task-section">
                <view class="task-section-head">
                    <view>
                        <text class="task-section-title">{{ section.title }}</text>
                        <text class="task-section-hint">{{ section.hint }}</text>
                    </view>
                    <text :class="['task-count', section.key]">{{ section.items.length }}</text>
                </view>

                <view v-for="item in section.items" :key="item.id" class="assignment-card card" :class="'section-' + section.key">
                    <view class="card-top">
                        <view>
                            <view class="tag-row">
                                <text v-if="item.className" class="class-tag">{{ item.className }}</text>
                                <text v-if="item.subject" class="subject-tag">{{ item.subject }}</text>
                                <text class="type-tag">{{ item.assignmentType === 'WORKBOOK' ? '练习册' : '作文' }}</text>
                            </view>
                            <text class="assignment-title">{{ item.title }}</text>
                        </view>
                        <view :class="['status-tag', statusClass(item.submission)]">
                            <view v-if="isProcessing(item.submission)" class="status-spinner"></view>
                            <text>{{ statusText(item.submission) }}</text>
                        </view>
                    </view>

                    <text class="question">{{ item.assignmentType === 'WORKBOOK' ? ('题号范围：' + (item.questionRange || '')) : item.question }}</text>

                    <view class="action-row">
                        <button v-if="canViewResult(item.submission)" class="mini-btn primary" @click="viewResult(item.submission)">
                            查看批改
                        </button>
                        <button v-else-if="isProcessing(item.submission)" class="mini-btn primary" @click="viewResult(item.submission)">
                            查看进度
                        </button>
                        <button v-else class="mini-btn primary" @click="chooseAndSubmit(item)">
                            {{ item.assignmentType === 'WORKBOOK' ? '上传练习册' : '上传作业' }}
                        </button>
                        <button v-if="item.submission" class="mini-btn ghost" @click="chooseAndSubmit(item)">
                            重新提交
                        </button>
                    </view>
                </view>
            </view>
        </block>
    </view>
</template>

<script>
import {
    getStudentAssignments,
    getStudentInfo,
    getStudentSummary,
    logout,
    submitStudentAssignment,
    submitStudentWorkbook
} from '@/api/index.js'

export default {
    data() {
        return {
            studentInfo: getStudentInfo() || {},
            assignments: [],
            loading: false,
            summary: null,
            showAnalysis: false,
            assignmentTimer: null
        }
    },
    computed: {
        submittedCount() {
            return this.assignments.filter(a => a.submission).length
        },
        gradedCount() {
            return this.assignments.filter(a => this.canViewResult(a.submission)).length
        },
        pendingAssignments() {
            return this.assignments.filter(a => !a.submission || a.submission.status === 'FAILED')
        },
        processingAssignments() {
            return this.assignments.filter(a => this.isProcessing(a.submission))
        },
        gradedAssignments() {
            return this.assignments.filter(a => this.canViewResult(a.submission))
        },
        assignmentSections() {
            return [
                {
                    key: 'pending',
                    title: '待提交',
                    hint: '优先完成这些作业',
                    items: this.pendingAssignments
                },
                {
                    key: 'processing',
                    title: '批改中',
                    hint: '系统正在分析，稍后可查看结果',
                    items: this.processingAssignments
                },
                {
                    key: 'done',
                    title: '已批改',
                    hint: '查看反馈和原图批注',
                    items: this.gradedAssignments
                }
            ]
        }
    },
    onShow() {
        this.loadAssignments()
        this.loadSummary()
    },
    onHide() {
        this.stopAssignmentPolling()
    },
    onUnload() {
        this.stopAssignmentPolling()
    },
    onPullDownRefresh() {
        Promise.all([this.loadAssignments(), this.loadSummary()])
            .finally(() => uni.stopPullDownRefresh())
    },
    methods: {
        async loadSummary() {
            try {
                const res = await getStudentSummary()
                if (res.code === 200) this.summary = res.data
            } catch(e) {}
        },
        async loadAssignments() {
            this.loading = true
            try {
                const res = await getStudentAssignments()
                if (res.code === 200) this.assignments = res.data || []
            } catch (e) {}
            this.loading = false
            if (this.processingAssignments.length) this.startAssignmentPolling()
            else this.stopAssignmentPolling()
        },
        startAssignmentPolling() {
            if (this.assignmentTimer) return
            this.assignmentTimer = setInterval(this.loadAssignments, 3000)
        },
        stopAssignmentPolling() {
            if (this.assignmentTimer) {
                clearInterval(this.assignmentTimer)
                this.assignmentTimer = null
            }
        },
        chooseAndSubmit(item) {
            uni.chooseImage({
                count: item.assignmentType === 'WORKBOOK' ? 9 : 1,
                sizeType: ['original'],
                sourceType: ['camera', 'album'],
                success: async (imgRes) => {
                    await this.submit(item, imgRes.tempFilePaths || [])
                }
            })
        },
        async submit(item, filePaths) {
            uni.showLoading({ title: '提交中...', mask: true })
            try {
                const res = item.assignmentType === 'WORKBOOK'
                    ? await submitStudentWorkbook(item.id, filePaths)
                    : await submitStudentAssignment(item.id, filePaths[0])
                uni.hideLoading()
                if (res.code === 200) {
                    uni.showToast({ title: '已提交', icon: 'success' })
                    const essayId = res.data?.essayId
                    if (essayId) {
                        setTimeout(() => {
                            uni.navigateTo({ url: '/pages/student-portal/result?essayId=' + essayId })
                        }, 500)
                    } else {
                        this.loadAssignments()
                    }
                } else {
                    uni.showToast({ title: res.message || '提交失败', icon: 'none' })
                }
            } catch (e) {
                uni.hideLoading()
                uni.showToast({ title: '提交失败', icon: 'none' })
            }
        },
        statusText(submission) {
            if (!submission) return '未提交'
            if (submission.reviewRequired && submission.status === 'GRADED') return '老师复核中'
            const map = {
                UPLOADED: '已提交',
                OCR_PROCESSING: '识别中',
                OCR_DONE: '识别完成',
                AI_PROCESSING: '批改中',
                GRADED: '已批改',
                TEACHER_REVIEWED: '老师已确认',
                FAILED: '需重传'
            }
            return map[submission.status] || submission.status || '已提交'
        },
        statusClass(submission) {
            if (!submission) return 'muted'
            if (submission.reviewRequired && submission.status === 'GRADED') return 'review'
            if (submission.status === 'GRADED' || submission.status === 'TEACHER_REVIEWED') return 'success'
            if (submission.status === 'FAILED') return 'danger'
            return 'primary'
        },
        canViewResult(submission) {
            return submission && (submission.status === 'GRADED' || submission.status === 'TEACHER_REVIEWED')
        },
        isProcessing(submission) {
            return submission && !this.canViewResult(submission) && submission.status !== 'FAILED'
        },
        viewResult(submission) {
            if (!submission?.essayId) return
            uni.navigateTo({ url: '/pages/student-portal/result?essayId=' + submission.essayId })
        },
        goFeedback() {
            uni.navigateTo({ url: '/pages/feedback/feedback' })
        },
        handleLogout() {
            logout()
            uni.reLaunch({ url: '/pages/login/login' })
        },
        handleExport() {
            if (!this.summary || !this.summary.records) return
            let text = this.studentInfo.name + ' 学习报告\n'
            text += '班级：' + (this.studentInfo.className || '') + '\n'
            text += '平均分：' + (this.summary.averageScore || 0) + '\n'
            text += '批改次数：' + (this.summary.totalEssays || 0) + '\n\n'
            text += '---成绩记录---\n'
            this.summary.records.forEach((r, i) => {
                text += (i+1) + '. 分数:' + (r.score||0) + '  ' + (r.createdAt||'') + '\n'
            })
            uni.setClipboardData({ data: text, success: () => {
                uni.showToast({ title: '报告已复制到剪贴板', icon: 'success' })
            }})
        }
    }
}
</script>

<style scoped>
.student-head { background: var(--color-ink); color: #FFF; border-radius: var(--radius-xl); padding: 28rpx; display: flex; justify-content: space-between; align-items: center; margin-bottom: var(--space-lg); }
.student-label { display: block; color: #BAE6FD; font-size: 22rpx; font-weight: 700; }
.student-name { display: block; font-size: 36rpx; font-weight: 800; line-height: 1.3; }
.student-class { display: block; color: rgba(255,255,255,0.72); font-size: 24rpx; }
.student-actions { display: flex; gap: 12rpx; flex-shrink: 0; }
.feedback-btn { width: 120rpx; height: 60rpx; line-height: 60rpx; border-radius: var(--radius-full); color: #FFF; background: rgba(21, 163, 122, 0.86); border: 1rpx solid rgba(255,255,255,0.22); font-size: 24rpx; }
.logout-btn { width: 120rpx; height: 60rpx; line-height: 60rpx; border-radius: var(--radius-full); color: #FFF; background: rgba(255,255,255,0.14); border: 1rpx solid rgba(255,255,255,0.22); font-size: 24rpx; }
.summary-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12rpx; margin-bottom: var(--space-lg); }
.summary-card { background: var(--color-surface); border-radius: var(--radius-md); padding: 20rpx; text-align: center; box-shadow: var(--shadow-sm); }
.summary-card text { display: block; font-size: 22rpx; color: var(--color-text-tertiary); }
.summary-card strong { display: block; font-size: 40rpx; color: var(--color-primary); }
.summary-card.pending strong { color: #D97706; }
.summary-card.processing strong { color: var(--color-primary); }
.summary-card.done strong { color: var(--color-success); }
.task-section { margin-bottom: var(--space-lg); }
.task-section-head { display: flex; align-items: center; justify-content: space-between; margin: 8rpx 2rpx 14rpx; }
.task-section-title { display: block; font-size: 32rpx; font-weight: 800; color: var(--color-text-primary); }
.task-section-hint { display: block; margin-top: 4rpx; font-size: 22rpx; color: var(--color-text-tertiary); }
.task-count { min-width: 52rpx; height: 52rpx; line-height: 52rpx; text-align: center; border-radius: 26rpx; font-size: 24rpx; font-weight: 800; }
.task-count.pending { background: var(--color-warning-light); color: #B45309; }
.task-count.processing { background: var(--color-primary-light); color: var(--color-primary); }
.task-count.done { background: var(--color-success-light); color: var(--color-success); }
.assignment-card { padding: 24rpx; }
.assignment-card.section-pending { border-left: 6rpx solid #F59E0B; }
.assignment-card.section-processing { border-left: 6rpx solid var(--color-primary); }
.assignment-card.section-done { border-left: 6rpx solid var(--color-success); }
.card-top { display: flex; justify-content: space-between; gap: var(--space-md); margin-bottom: 14rpx; }
.assignment-title { display: block; font-size: 32rpx; font-weight: 800; color: var(--color-text-primary); line-height: 1.35; }
.class-tag, .subject-tag, .type-tag { font-size: 20rpx; padding: 4rpx 14rpx; border-radius: var(--radius-full); font-weight: 700; margin-right: 8rpx; }
.class-tag { background: var(--color-mint-light); color: var(--color-mint); }
.subject-tag { background: var(--color-primary-light); color: var(--color-primary); }
.type-tag { background: var(--color-warning-light); color: #B45309; }
.status-tag { min-height: 48rpx; padding: 0 18rpx; border-radius: var(--radius-full); font-size: 22rpx; font-weight: 700; white-space: nowrap; display: flex; align-items: center; gap: 8rpx; }
.status-tag.primary { background: var(--color-primary-light); color: var(--color-primary); }
.status-tag.success { background: var(--color-success-light); color: var(--color-success); }
.status-tag.review { background: var(--color-warning-light); color: #B45309; }
.status-tag.danger { background: var(--color-danger-light); color: var(--color-danger); }
.status-tag.muted { background: var(--color-divider); color: var(--color-text-tertiary); }
.status-spinner { width: 20rpx; height: 20rpx; border-radius: 50%; border: 3rpx solid rgba(37, 99, 235, 0.22); border-top-color: var(--color-primary); animation: spin 0.8s linear infinite; flex-shrink: 0; }
@keyframes spin { to { transform: rotate(360deg); } }
.question { display: block; font-size: 26rpx; color: var(--color-text-secondary); line-height: 1.7; margin-bottom: 20rpx; }
.action-row { display: flex; gap: 12rpx; }
.mini-btn { flex: 1; height: 72rpx; line-height: 72rpx; border-radius: var(--radius-full); border: none; font-size: 26rpx; font-weight: 700; }
.mini-btn.primary { background: var(--color-primary); color: #FFF; }
.mini-btn.ghost { background: var(--color-primary-light); color: var(--color-primary); }

.analysis-card {
    background: var(--color-surface);
    border-radius: var(--radius-lg);
    border: 2rpx solid var(--color-primary-soft);
    padding: 24rpx;
    margin-bottom: var(--space-lg);
}
.analysis-header { display: flex; justify-content: space-between; align-items: center; }
.analysis-title { font-size: 28rpx; font-weight: 700; color: var(--color-primary); }
.analysis-arrow { font-size: 24rpx; color: var(--color-text-tertiary); }
.analysis-body { margin-top: var(--space-lg); }
.analysis-row { display: flex; justify-content: space-between; padding: 12rpx 0; border-bottom: 1rpx solid var(--color-divider); }
.analysis-label { font-size: 26rpx; color: var(--color-text-secondary); }
.analysis-val { font-size: 28rpx; font-weight: 700; color: var(--color-primary); }
.kp-section { margin-top: 16rpx; }
.kp-tags { display: flex; flex-wrap: wrap; gap: 8rpx; margin-top: 8rpx; }
.kp-tag { font-size: 22rpx; background: var(--color-primary-light); color: var(--color-primary); padding: 6rpx 16rpx; border-radius: var(--radius-full); font-weight: 500; }
.export-btn {
    width: 100%; height: 72rpx; line-height: 72rpx;
    background: var(--color-success); color: #FFF;
    border-radius: var(--radius-full); font-size: 26rpx; font-weight: 700;
    border: none; margin-top: var(--space-md);
}
.export-btn:active { transform: scale(0.96); }
</style>
