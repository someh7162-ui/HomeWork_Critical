<template>
    <view class="page-container">
        <view v-if="assignment" class="detail-card">
            <view class="detail-header">
                <view class="detail-title-wrap">
                    <view class="tag-row">
                        <text v-if="assignment.className" class="detail-class">{{ assignment.className }}</text>
                        <text v-if="assignment.subject" class="badge-subject">{{ assignment.subject }}</text>
                        <text class="type-pill">{{ isWorkbook ? '练习册' : '作文' }}</text>
                    </view>
                    <text class="detail-title">{{ assignment.title }}</text>
                </view>
                <text class="detail-score">满分{{ assignment.totalScore }}分</text>
            </view>

            <view class="fold-section" @click="toggle('question')">
                <view class="fold-head">
                    <text class="fold-title">{{ isWorkbook ? '题号范围' : '题目要求' }}</text>
                    <text class="fold-arrow" :class="{ open: !collapsed.question }">{{ collapsed.question ? '›' : '⌄' }}</text>
                </view>
                <view v-if="!collapsed.question" class="fold-body">
                    <text class="section-content">{{ isWorkbook ? assignment.questionRange : assignment.question }}</text>
                </view>
            </view>

            <view class="fold-section" v-if="isWorkbook" @click="toggle('answer')">
                <view class="fold-head">
                    <text class="fold-title">答案页解析</text>
                    <text class="fold-arrow" :class="{ open: !collapsed.answer }">{{ collapsed.answer ? '›' : '⌄' }}</text>
                </view>
                <view v-if="!collapsed.answer" class="fold-body" @click.stop>
                    <text class="section-content">{{ answerStatusText }}</text>
                    <view v-if="answerKeyDraft?.questions?.length" class="answer-review">
                        <text class="answer-review-hint">请在学生提交前检查标准答案。发现识别偏差时，可直接修正后保存。</text>
                        <view v-for="(item, index) in answerKeyDraft.questions" :key="index" class="answer-row">
                            <view class="answer-row-head">
                                <text class="answer-no">第 {{ item.questionNo }} 题</text>
                                <input class="answer-score" type="digit" v-model.number="item.maxScore" placeholder="分值" />
                            </view>
                            <textarea class="answer-input" v-model="item.answer" auto-height placeholder="标准答案" />
                            <textarea class="answer-input muted" v-model="item.analysis" auto-height placeholder="简要解析" />
                            <input class="answer-input" v-model="item.knowledgePoint" placeholder="知识点" />
                        </view>
                        <button class="save-answer-btn" :disabled="savingAnswerKey" @click.stop="saveAnswerKey">
                            {{ savingAnswerKey ? '保存中...' : '确认并保存标准答案' }}
                        </button>
                    </view>
                </view>
            </view>

            <view class="fold-section" v-else @click="toggle('essay')">
                <view class="fold-head">
                    <text class="fold-title">参考范文</text>
                    <text class="fold-arrow" :class="{ open: !collapsed.essay }">{{ collapsed.essay ? '›' : '⌄' }}</text>
                </view>
                <view v-if="!collapsed.essay" class="fold-body">
                    <text class="section-content model-essay">{{ assignment.modelEssay }}</text>
                </view>
            </view>
        </view>

        <view class="action-buttons surface-panel">
            <button class="btn grade-btn" @click="goCapture">{{ isWorkbook ? '代传练习册' : '开始批改' }}</button>
            <button class="btn record-btn" @click="goRecords">查看记录</button>
            <button class="btn stats-btn" @click="goStats">班级统计</button>
            <button class="btn export-btn" @click="handleExport">导出Excel</button>
            <button class="btn delete-btn" @click="handleDelete">删除</button>
        </view>

        <view v-if="submissionSummary" class="submission-panel card">
            <view class="submission-head" @click="toggle('submission')">
                <text class="section-title">提交情况</text>
                <view style="display:flex;align-items:center;gap:12rpx;">
                    <text class="submission-sub">{{ submissionSummary.submittedCount }}/{{ submissionSummary.totalStudents }} 已提交</text>
                    <text class="fold-arrow small" :class="{ open: !collapsed.submission }">{{ collapsed.submission ? '›' : '⌄' }}</text>
                </view>
            </view>
            <view v-if="!collapsed.submission">
                <view class="class-overview">
                    <view class="overview-top">
                        <view>
                            <text class="overview-label">全班提交率</text>
                            <text class="overview-value">{{ submissionStats.submitRate }}%</text>
                        </view>
                        <text class="overview-desc">{{ submissionStats.submitted }}/{{ submissionStats.total }} 已提交</text>
                    </view>
                    <view class="overview-bar">
                        <view class="overview-bar-fill" :style="{ width: submissionStats.submitRate + '%' }"></view>
                    </view>
                </view>
                <view class="submission-metrics">
                    <view><text>全班</text><strong>{{ submissionStats.total }}</strong></view>
                    <view><text>未提交</text><strong>{{ submissionStats.unsubmitted }}</strong></view>
                    <view><text>批改中</text><strong>{{ submissionStats.processing }}</strong></view>
                    <view><text>已批改</text><strong>{{ submissionStats.graded }}</strong></view>
                    <view class="review-metric"><text>待复核</text><strong>{{ submissionStats.reviewRequired }}</strong></view>
                    <view><text>失败</text><strong>{{ submissionStats.failed }}</strong></view>
                </view>
                <view v-for="item in submissionSummary.students" :key="item.studentId" class="submission-row">
                    <view>
                        <text class="submission-name">{{ item.studentName }}</text>
                        <text class="submission-meta">{{ item.studentNo || '无学号' }}</text>
                    </view>
                    <view class="submission-action">
                        <view :class="['submission-status', statusClass(item.submission)]">
                            <view v-if="isProcessing(item.submission)" class="status-spinner"></view>
                            <text>{{ statusText(item.submission) }}</text>
                        </view>
                        <button v-if="!item.submitted" class="assist-btn" @click="goCaptureForStudent(item)">帮忙上传</button>
                        <button v-else-if="item.submission?.essayId" class="assist-btn ghost" @click="viewSubmission(item)">查看</button>
                    </view>
                </view>
            </view>
        </view>
    </view>
</template>

<script>
import { getAssignmentAnswerKey, getAssignmentDetail, deleteAssignment, getAssignmentSubmissions, getServerOrigin, updateAssignmentAnswerKey } from '@/api/index.js'

export default {
    data() {
        return {
            assignment: null,
            id: '',
            answerKey: null,
            answerKeyDraft: null,
            savingAnswerKey: false,
            submissionSummary: null,
            submissionTimer: null,
            answerTimer: null,
            collapsed: { question: true, essay: true, answer: true, submission: false }
        }
    },
    computed: {
        isWorkbook() {
            return this.assignment?.assignmentType === 'WORKBOOK'
        },
        answerStatusText() {
            const status = this.answerKey?.status || this.assignment?.answerKeyStatus || 'PENDING'
            const message = this.answerKey?.message || this.assignment?.answerKeyMessage || ''
            const map = { PENDING: '等待上传答案页', PARSING: '答案页解析中', READY: '答案页已解析', FAILED: '答案页解析失败' }
            return (map[status] || status) + (message ? '：' + message : '')
        },
        submissionStats() {
            const students = this.submissionSummary?.students || []
            const total = Number(this.submissionSummary?.totalStudents || students.length || 0)
            const submitted = Number(this.submissionSummary?.submittedCount || students.filter(item => item.submitted).length || 0)
            const processing = students.filter(item => this.isProcessing(item.submission)).length
            const graded = Number(this.submissionSummary?.gradedCount || students.filter(item => {
                const status = item.submission?.status
                return status === 'GRADED' || status === 'TEACHER_REVIEWED'
            }).length || 0)
            const failed = Number(this.submissionSummary?.failedCount || students.filter(item => item.submission?.status === 'FAILED').length || 0)
            const reviewRequired = Number(this.submissionSummary?.reviewRequiredCount || students.filter(item => {
                return item.submission?.reviewRequired && item.submission?.status !== 'TEACHER_REVIEWED'
            }).length || 0)
            const unsubmitted = Number(this.submissionSummary?.unsubmittedCount || Math.max(total - submitted, 0))
            const submitRate = total > 0 ? Math.round(submitted * 100 / total) : 0
            return { total, submitted, processing, graded, failed, reviewRequired, unsubmitted, submitRate }
        }
    },
    onLoad(options) {
        this.id = options.id
        this.loadDetail()
    },
    onUnload() {
        this.stopSubmissionPolling()
        if (this.answerTimer) clearInterval(this.answerTimer)
    },
    methods: {
        toggle(key) {
            this.collapsed[key] = !this.collapsed[key]
            this.$forceUpdate()
        },
        async loadDetail() {
            try {
                const res = await getAssignmentDetail(this.id)
                if (res.code === 200) {
                    this.assignment = res.data
                    if (this.isWorkbook) this.loadAnswerKey()
                    this.loadSubmissions()
                }
            } catch(e) {}
        },
        async loadAnswerKey() {
            try {
                const res = await getAssignmentAnswerKey(this.id)
                if (res.code === 200) {
                    this.answerKey = res.data
                    if (res.data?.answerKey && !this.answerKeyDraft) {
                        this.answerKeyDraft = JSON.parse(JSON.stringify(res.data.answerKey))
                    }
                    if (res.data?.status === 'PARSING' && !this.answerTimer) {
                        this.answerTimer = setInterval(this.loadAnswerKey, 3000)
                    }
                    if (res.data?.status !== 'PARSING' && this.answerTimer) {
                        clearInterval(this.answerTimer)
                        this.answerTimer = null
                    }
                }
            } catch(e) {}
        },
        async saveAnswerKey() {
            if (!this.answerKeyDraft?.questions?.length || this.savingAnswerKey) return
            this.savingAnswerKey = true
            try {
                const res = await updateAssignmentAnswerKey(this.id, this.answerKeyDraft)
                if (res.code === 200) {
                    this.answerKey = res.data
                    uni.showToast({ title: '标准答案已保存', icon: 'success' })
                }
            } catch(e) {
            } finally {
                this.savingAnswerKey = false
            }
        },
        async loadSubmissions() {
            try {
                const res = await getAssignmentSubmissions(this.id)
                if (res.code === 200) {
                    this.submissionSummary = res.data
                    if (this.hasProcessingSubmissions()) this.startSubmissionPolling()
                    else this.stopSubmissionPolling()
                }
            } catch(e) {}
        },
        startSubmissionPolling() {
            if (this.submissionTimer) return
            this.submissionTimer = setInterval(this.loadSubmissions, 3000)
        },
        stopSubmissionPolling() {
            if (this.submissionTimer) {
                clearInterval(this.submissionTimer)
                this.submissionTimer = null
            }
        },
        hasProcessingSubmissions() {
            return (this.submissionSummary?.students || []).some(item => this.isProcessing(item.submission))
        },
        goCapture() {
            if (this.isWorkbook && (this.answerKey?.status || this.assignment?.answerKeyStatus) !== 'READY') {
                uni.showToast({ title: '答案页还未解析完成', icon: 'none' })
                return
            }
            uni.navigateTo({
                url: '/pages/grading/capture?assignmentId=' + this.id
                    + '&title=' + encodeURIComponent(this.assignment?.title || '')
                    + '&assignmentType=' + encodeURIComponent(this.assignment?.assignmentType || 'ESSAY')
            })
        },
        goRecords() {
            uni.navigateTo({ url: '/pages/grading/list?assignmentId=' + this.id + '&title=' + encodeURIComponent(this.assignment?.title || '') })
        },
        goCaptureForStudent(item) {
            uni.navigateTo({
                url: '/pages/grading/capture?assignmentId=' + this.id
                    + '&title=' + encodeURIComponent(this.assignment?.title || '')
                    + '&assignmentType=' + encodeURIComponent(this.assignment?.assignmentType || 'ESSAY')
                    + '&studentId=' + item.studentId
            })
        },
        viewSubmission(item) {
            uni.navigateTo({ url: '/pages/grading/result?essayId=' + item.submission.essayId + '&studentName=' + encodeURIComponent(item.studentName) })
        },
        statusText(submission) {
            if (!submission) return '未提交'
            if (submission.reviewRequired && submission.status === 'GRADED') return '待复核'
            const map = { UPLOADED: '正在批改', OCR_PROCESSING: '正在批改', OCR_DONE: '正在批改', AI_PROCESSING: '正在批改', GRADED: '已批改', TEACHER_REVIEWED: '已确认', FAILED: '失败' }
            return map[submission.status] || submission.status || '已提交'
        },
        statusClass(submission) {
            if (!submission) return 'pending'
            if (submission.reviewRequired && submission.status === 'GRADED') return 'review'
            if (submission.status === 'GRADED' || submission.status === 'TEACHER_REVIEWED') return 'done'
            if (submission.status === 'FAILED') return 'failed'
            return 'processing'
        },
        isProcessing(submission) {
            return submission && ['UPLOADED', 'OCR_PROCESSING', 'OCR_DONE', 'AI_PROCESSING'].includes(submission.status)
        },
        handleDelete() {
            uni.showModal({
                title: '确认删除',
                content: '删除后作业及批改记录将无法恢复',
                success: async (res) => {
                    if (res.confirm) {
                        try {
                            await deleteAssignment(this.id)
                            uni.showToast({ title: '已删除', icon: 'success' })
                            setTimeout(() => uni.navigateBack(), 1000)
                        } catch(e) {}
                    }
                }
            })
        },
        goStats() {
            uni.navigateTo({ url: '/pages/grading/stats?assignmentId=' + this.id + '&title=' + encodeURIComponent(this.assignment?.title || '') })
        },
        handleExport() {
            uni.showLoading({ title: '生成中...' })
            const serverOrigin = getServerOrigin()
            const token = uni.getStorageSync('token')
            const title = encodeURIComponent(this.assignment?.title || '成绩单')
            uni.downloadFile({
                url: serverOrigin + '/api/essays/assignment/' + this.id + '/export?title=' + title,
                header: { 'Authorization': 'Bearer ' + token },
                success: (res) => {
                    uni.hideLoading()
                    if (res.statusCode === 200) {
                        uni.saveFile({
                            tempFilePath: res.tempFilePath,
                            success: (saveRes) => {
                                const path = saveRes.savedFilePath
                                uni.showModal({
                                    title: '导出成功',
                                    content: '文件已保存到：' + path + '\n\n是否打开？',
                                    confirmText: '打开',
                                    cancelText: '稍后',
                                    success: (m) => {
                                        if (m.confirm) {
                                            uni.openDocument({ filePath: path, showMenu: true, fail: () => {
                                                uni.showToast({ title: '请安装WPS或Excel查看', icon: 'none' })
                                            }})
                                        }
                                    }
                                })
                            },
                            fail: () => {
                                uni.openDocument({ filePath: res.tempFilePath, showMenu: true, fail: () => {
                                    uni.showToast({ title: '导出成功，请在文件管理查看', icon: 'none' })
                                }})
                            }
                        })
                    } else {
                        uni.showToast({ title: '导出失败', icon: 'none' })
                    }
                },
                fail: () => {
                    uni.hideLoading()
                    uni.showToast({ title: '下载失败，请检查网络', icon: 'none' })
                }
            })
        }
    }
}
</script>

<style scoped>
.page-container { padding: 30rpx; }
.detail-card { background: linear-gradient(180deg, #FFFFFF, #F8FAFC); border-radius: 16rpx; padding: 30rpx; margin-bottom: 30rpx; border: 1rpx solid var(--color-border); box-shadow: var(--shadow-sm); }
.detail-header { display: flex; justify-content: space-between; align-items: flex-start; padding-bottom: 24rpx; border-bottom: 1rpx solid #F0F0F0; margin-bottom: 24rpx; gap: var(--space-md); }
.detail-title-wrap { flex: 1; }
.tag-row { display: flex; flex-wrap: wrap; gap: 8rpx; margin-bottom: 12rpx; }
.detail-title { font-size: 34rpx; font-weight: bold; display: block; color: var(--color-text-primary); line-height: 1.4; }
.detail-class, .badge-subject, .type-pill { display: inline-block; font-size: 22rpx; border-radius: var(--radius-full); padding: 5rpx 16rpx; font-weight: 700; }
.detail-class { color: var(--color-mint); background: var(--color-mint-light); }
.badge-subject { color: var(--color-primary); background: var(--color-primary-light); }
.type-pill { color: #B45309; background: var(--color-warning-light); }
.detail-score { background: var(--color-primary-light); color: var(--color-primary); padding: 6rpx 20rpx; border-radius: 20rpx; font-size: 24rpx; }
.section-content { font-size: 30rpx; line-height: 1.8; color: var(--color-text-primary); white-space: pre-wrap; }
.model-essay { background: var(--color-surface); padding: 20rpx; border-radius: 12rpx; border-left: 4rpx solid var(--color-primary); border: 1rpx solid var(--color-border); }
.action-buttons { display: grid; grid-template-columns: 1fr 1fr; gap: 12rpx; padding: 14rpx; position: sticky; bottom: 20rpx; }
.btn { width: 100%; height: 72rpx; line-height: 72rpx; border-radius: var(--radius-md); font-size: 26rpx; border: none; }
.grade-btn { background: var(--color-primary-gradient); color: #FFF; }
.record-btn { background: var(--color-primary-light); color: var(--color-primary); }
.stats-btn { background: var(--color-mint-light); color: var(--color-mint); }
.export-btn { background: var(--color-success-light); color: #065F46; }
.delete-btn { background: var(--color-danger-light); color: var(--color-danger); }
.submission-panel { margin-top: 24rpx; padding: 24rpx; }
.submission-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 18rpx; }
.submission-sub { color: var(--color-primary); font-size: 24rpx; font-weight: 700; }
.class-overview { background: linear-gradient(180deg, #F8FAFC, #FFFFFF); border: 1rpx solid var(--color-border); border-radius: var(--radius-lg); padding: 22rpx; margin-bottom: 18rpx; }
.overview-top { display: flex; justify-content: space-between; align-items: flex-end; margin-bottom: 16rpx; }
.overview-label { display: block; color: var(--color-text-tertiary); font-size: 22rpx; font-weight: 700; }
.overview-value { display: block; color: var(--color-primary); font-size: 52rpx; font-weight: 900; line-height: 1.1; }
.overview-desc { color: var(--color-text-secondary); font-size: 24rpx; font-weight: 700; }
.overview-bar { height: 14rpx; border-radius: 7rpx; overflow: hidden; background: #E5E7EB; }
.overview-bar-fill { height: 100%; border-radius: 7rpx; background: linear-gradient(90deg, #2563EB, #38BDF8); transition: width 0.35s var(--ease-out); }
.fold-section {
    border-bottom: 1rpx solid var(--color-divider);
    padding: 18rpx 0;
    transition: background 0.2s;
}
.fold-section:last-child { border-bottom: none; }
.fold-section:active { background: var(--color-bg); }
.fold-head {
    display: flex;
    justify-content: space-between;
    align-items: center;
}
.fold-title { font-size: 28rpx; font-weight: 700; color: var(--color-text-primary); }
.fold-arrow {
    font-size: 36rpx;
    color: var(--color-text-tertiary);
    transition: transform 0.25s var(--ease-out);
}
.fold-arrow.open { color: var(--color-primary); }
.fold-arrow.small { font-size: 28rpx; }
.fold-body {
    margin-top: 16rpx;
    animation: fadeIn 0.25s var(--ease-out);
}
.submission-metrics { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10rpx; margin-bottom: 18rpx; }
.submission-metrics view { background: var(--color-surface-muted); border-radius: var(--radius-md); text-align: center; padding: 14rpx 8rpx; }
.submission-metrics text { display: block; color: var(--color-text-tertiary); font-size: 22rpx; }
.submission-metrics strong { display: block; color: var(--color-text-primary); font-size: 34rpx; }
.submission-row { display: flex; justify-content: space-between; align-items: center; padding: 18rpx 0; border-bottom: 1rpx solid var(--color-divider); }
.submission-row:last-child { border-bottom: none; }
.submission-name { display: block; font-size: 28rpx; color: var(--color-text-primary); font-weight: 700; }
.submission-meta { display: block; font-size: 22rpx; color: var(--color-text-tertiary); }
.submission-action { display: flex; align-items: center; gap: 10rpx; }
.submission-status { display: flex; align-items: center; gap: 8rpx; font-size: 22rpx; padding: 6rpx 14rpx; border-radius: var(--radius-full); font-weight: 700; white-space: nowrap; }
.submission-status.pending { background: var(--color-divider); color: var(--color-text-tertiary); }
.submission-status.processing { background: var(--color-primary-light); color: var(--color-primary); }
.submission-status.done { background: var(--color-success-light); color: var(--color-success); }
.submission-status.review { background: var(--color-warning-light); color: #B45309; }
.submission-status.failed { background: var(--color-danger-light); color: var(--color-danger); }
.status-spinner { width: 20rpx; height: 20rpx; border-radius: 50%; border: 3rpx solid rgba(37, 99, 235, 0.22); border-top-color: var(--color-primary); animation: spin 0.8s linear infinite; flex-shrink: 0; }
@keyframes spin { to { transform: rotate(360deg); } }
.assist-btn { width: 140rpx; height: 56rpx; line-height: 56rpx; border-radius: var(--radius-full); background: var(--color-primary); color: #FFF; font-size: 22rpx; border: none; }
.assist-btn.ghost { background: var(--color-primary-light); color: var(--color-primary); }
.answer-review { margin-top: 18rpx; }
.answer-review-hint { display: block; margin-bottom: 16rpx; color: #B45309; font-size: 23rpx; line-height: 1.6; }
.answer-row { padding: 18rpx; margin-bottom: 14rpx; border: 1rpx solid var(--color-border); border-radius: var(--radius-md); background: #FFFFFF; }
.answer-row-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12rpx; }
.answer-no { color: var(--color-text-primary); font-size: 26rpx; font-weight: 800; }
.answer-score { width: 120rpx; height: 58rpx; text-align: center; border: 1rpx solid var(--color-border); border-radius: var(--radius-sm); font-size: 24rpx; }
.answer-input { display: block; box-sizing: border-box; width: 100%; min-height: 62rpx; padding: 12rpx; margin-top: 10rpx; border: 1rpx solid var(--color-border); border-radius: var(--radius-sm); background: #FFFFFF; color: var(--color-text-primary); font-size: 24rpx; line-height: 1.5; }
.answer-input.muted { background: #F8FAFC; }
.save-answer-btn { height: 70rpx; line-height: 70rpx; border: none; border-radius: var(--radius-md); background: var(--color-primary); color: #FFFFFF; font-size: 25rpx; font-weight: 700; }
</style>
