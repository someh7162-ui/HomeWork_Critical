<template>
    <view class="page-container">
        <view v-if="!isReady" class="progress-card" :class="{ failed: isFailed }">
            <view class="progress-head">
                <text class="progress-title">{{ isFailed ? '批改失败' : '正在批改' }}</text>
                <text class="progress-chip">{{ statusText }}</text>
            </view>
            <view class="progress-bar-wrap">
                <view class="progress-bar-bg">
                    <view class="progress-bar-fill" :style="{ width: displayPercent + '%' }" :class="isFailed ? 'fill-failed' : 'fill-active'"></view>
                    <view class="progress-bar-shimmer" v-if="!isFailed"></view>
                </view>
            </view>
            <text class="progress-percent">{{ displayPercent }}%</text>
            <text v-if="isFailed" class="failure-message">{{ failureMessage || '请返回重新提交更清晰的图片。' }}</text>
            <view class="stage-row">
                <text class="stage-label" :class="{ active: displayPercent >= 10 }">提交</text>
                <view :class="['stage-dot', displayPercent >= 10 ? 'active' : '']"></view>
                <view :class="['stage-line', displayPercent >= 35 ? 'active' : '']"></view>
                <view :class="['stage-dot', displayPercent >= 35 ? 'active' : '']"></view>
                <view :class="['stage-line', displayPercent >= 65 ? 'active' : '']"></view>
                <view :class="['stage-dot', displayPercent >= 65 ? 'active' : '']"></view>
                <view :class="['stage-line', displayPercent >= 95 ? 'active' : '']"></view>
                <view :class="['stage-dot', displayPercent >= 95 ? 'active' : '']"></view>
                <text class="stage-label" :class="{ active: displayPercent >= 95 }">完成</text>
            </view>
        </view>

        <view v-if="isReady" class="score-card">
            <text class="score-label">{{ finalScore !== null && finalScore !== undefined ? '老师确认分' : 'AI评分' }}</text>
            <view class="score-row">
                <text class="score-number">{{ displayScore }}</text>
                <text class="score-max">/ 满分{{ maxScore }}</text>
            </view>
        </view>

        <view v-if="isReady" class="review-state" :class="{ confirmed: status === 'TEACHER_REVIEWED', warning: reviewRequired && status !== 'TEACHER_REVIEWED' }">
            <text class="review-state-title">{{ reviewStateTitle }}</text>
            <text class="review-state-desc">{{ reviewStateDesc }}</text>
            <text class="review-state-chip">{{ confidenceLabel }} {{ confidencePercent }}%</text>
        </view>

        <view v-if="isReady" class="card">
            <text class="section-title">总体评价</text>
            <text class="comment">{{ overallComment }}</text>
        </view>

        <view v-if="isReady && workbookResult?.questions?.length" class="card">
            <text class="section-title">逐题结果</text>
            <view v-for="item in workbookResult.questions" :key="item.questionNo" class="question-row">
                <view class="q-head">
                    <text class="q-no">第 {{ item.questionNo }} 题</text>
                    <text :class="['q-status', item.isCorrect ? 'ok' : 'bad']">{{ item.score }} / {{ item.maxScore }}</text>
                </view>
                <text class="q-line">你的答案：{{ item.studentAnswer || '未识别/空白' }}</text>
                <text class="q-line">参考答案：{{ item.correctAnswer || '-' }}</text>
                <text class="q-point">{{ item.knowledgePoint || '知识点未分类' }}</text>
                <text class="q-feedback">{{ item.feedback || '' }}</text>
            </view>
        </view>

        <view v-if="isReady && workbookResult?.knowledgePoints?.length" class="card">
            <text class="section-title">知识点提醒</text>
            <view v-for="point in workbookResult.knowledgePoints" :key="point.name" class="point-row">
                <text class="point-name">{{ point.name }}</text>
                <text class="point-meta">错 {{ point.wrongCount || 0 }} / {{ point.totalCount || 0 }}</text>
                <text class="point-suggestion">{{ point.suggestion || '建议回看对应例题并整理错因。' }}</text>
            </view>
        </view>

        <view v-if="isReady && gradingResult?.dimensions?.length" class="card">
            <text class="section-title">评分明细</text>
            <view v-for="(dim, idx) in gradingResult.dimensions" :key="idx" class="dimension-item">
                <view class="dim-head">
                    <text class="dim-name">{{ dim.name }}</text>
                    <text class="dim-score">{{ dim.score }} / {{ dim.maxScore }}</text>
                </view>
                <text class="dim-comment">{{ dim.comment }}</text>
            </view>
        </view>

        <view v-if="isReady && gradingResult?.grammarErrors?.length" class="card">
            <text class="section-title">问题订正</text>
            <view v-for="(err, idx) in gradingResult.grammarErrors" :key="idx" class="error-item">
                <text class="error-title">#{{ idx + 1 }} {{ err.original }}</text>
                <text class="error-fix">建议：{{ err.correction }}</text>
                <text class="error-desc">{{ err.explanation }}</text>
            </view>
        </view>

        <view v-if="isReady && imageUrl" class="card">
            <text class="section-title">提交图片</text>
            <text v-if="hasVisualAnnotations || hasAnnotatedImages" class="annotation-legend">{{ annotationLegendText }}</text>
            <view v-for="(page, pageIndex) in imagePages" :key="pageIndex" class="annotated-image-wrap">
                <view class="image-stage">
                    <image
                        :src="page.url"
                        mode="widthFix"
                        class="essay-image"
                        @error="handleImageError(pageIndex)"
                        @click="previewImage(pageIndex)"
                    />
                    <view class="annotation-layer" v-if="!page.annotated && annotationsForPage(pageIndex).length">
                        <view
                            v-for="(ann, idx) in annotationsForPage(pageIndex)"
                            :key="pageIndex + '-' + idx"
                            class="image-annotation"
                            :class="['ann-' + (ann.severity || 'error'), ann.type === 'pin' ? 'ann-pin' : '']"
                            :style="annotationStyle(ann)"
                        >
                            <text class="annotation-index">{{ idx + 1 }}</text>
                        </view>
                    </view>
                </view>
                <view class="annotation-list" v-if="annotationsForPage(pageIndex).length">
                    <view v-for="(ann, idx) in annotationsForPage(pageIndex)" :key="'note-' + pageIndex + '-' + idx" class="annotation-note">
                        <text class="annotation-note-index">#{{ idx + 1 }}</text>
                        <text class="annotation-note-text">{{ ann.comment || ann.feedback || ann.questionNo || '请查看红框位置' }}</text>
                    </view>
                </view>
            </view>
        </view>
    </view>
</template>

<script>
import { getServerOrigin, getStudentEssayDetail, getStudentEssayProgress } from '@/api/index.js'

export default {
    data() {
        return {
            essayId: '',
            status: 'UPLOADED',
            statusText: '作业已提交',
            progressPercent: 10,
            displayPercent: 10,
            animTimer: null,
            failureMessage: '',
            gradingResult: null,
            workbookResult: null,
            aiScore: null,
            finalScore: null,
            confidenceScore: 0.78,
            confidenceLevel: 'MEDIUM',
            reviewRequired: false,
            teacherReviewNote: '',
            maxScore: 15,
            imageUrl: '',
            imageCandidates: [],
            imagePages: [],
            pollTimer: null
        }
    },
    computed: {
        isReady() {
            return this.status === 'GRADED' || this.status === 'TEACHER_REVIEWED'
        },
        isFailed() {
            return this.status === 'FAILED'
        },
        displayScore() {
            return this.finalScore !== null && this.finalScore !== undefined ? this.finalScore : this.aiScore
        },
        overallComment() {
            return this.workbookResult?.overallComment || this.gradingResult?.overallComment || '暂无评价'
        },
        hasVisualAnnotations() {
            return this.collectAnnotations(this.workbookResult || this.gradingResult).length > 0
        },
        hasAnnotatedImages() {
            return this.imagePages.some(page => page.annotated)
        },
        annotationLegendText() {
            if (this.hasAnnotatedImages) return '红框、编号和批注说明已合成到图片中，点击可放大查看。'
            return '红框为原图定位批注，圆点为批改要点提示。'
        },
        confidencePercent() {
            return Math.round(Number(this.confidenceScore || 0.78) * 100)
        },
        confidenceLabel() {
            const map = { HIGH: '高置信度', MEDIUM: '中置信度', LOW: '低置信度' }
            return map[this.confidenceLevel] || '中置信度'
        },
        reviewStateTitle() {
            if (this.status === 'TEACHER_REVIEWED') return '老师已确认批改结果'
            if (this.reviewRequired) return '老师正在重点复核'
            return 'AI 初步批改已完成'
        },
        reviewStateDesc() {
            if (this.status === 'TEACHER_REVIEWED') return this.teacherReviewNote || '最终分和批注已经由老师确认。'
            if (this.reviewRequired) return '部分内容需要老师核对，当前结果可先用于预习订正。'
            return '老师确认前，当前分数和批注为 AI 初步结果。'
        }
    },
    onLoad(options) {
        this.essayId = options.essayId
        this.loadDetail()
    },
    onUnload() {
        this.stopPolling()
        this.stopAnim()
    },
    methods: {
        smoothProgress(target) {
            this.stopAnim()
            const next = Number(target || 0)
            if (next <= this.displayPercent) {
                this.displayPercent = next
                return
            }
            this.animTimer = setInterval(() => {
                if (this.displayPercent >= next) {
                    this.displayPercent = next
                    this.stopAnim()
                } else {
                    this.displayPercent = Math.min(this.displayPercent + 2, next)
                }
            }, 80)
        },
        stopAnim() {
            if (this.animTimer) {
                clearInterval(this.animTimer)
                this.animTimer = null
            }
        },
        async loadDetail() {
            try {
                const res = await getStudentEssayDetail(this.essayId)
                if (res.code !== 200) return
                const essay = res.data
                this.status = essay.status || 'UPLOADED'
                this.statusText = this.statusLabel(this.status)
                this.progressPercent = this.statusProgress(this.status)
                this.smoothProgress(this.progressPercent)
                this.failureMessage = essay.failureMessage || ''
                this.aiScore = essay.aiScore
                this.finalScore = essay.finalScore
                this.confidenceScore = essay.confidenceScore || 0.78
                this.confidenceLevel = essay.confidenceLevel || 'MEDIUM'
                this.reviewRequired = Boolean(essay.reviewRequired)
                this.teacherReviewNote = essay.teacherReviewNote || ''
                if (essay.imageUrl || essay.annotatedImageUrl) {
                    this.imagePages = this.buildImagePages(essay)
                    this.imageUrl = this.imagePages[0]?.url || ''
                    this.imageCandidates = this.imagePages[0]?.candidates || []
                }
                if (this.isReady) {
                    this.parseFeedback(essay)
                    this.stopPolling()
                } else if (!this.isFailed) {
                    this.startPolling()
                }
            } catch (e) {}
        },
        startPolling() {
            if (this.pollTimer) return
            this.pollTimer = setInterval(this.pollProgress, 2000)
            this.pollProgress()
        },
        stopPolling() {
            if (this.pollTimer) {
                clearInterval(this.pollTimer)
                this.pollTimer = null
            }
        },
        async pollProgress() {
            try {
                const res = await getStudentEssayProgress(this.essayId)
                const data = res.data || {}
                this.status = data.status || this.status
                this.statusText = data.statusText || this.statusLabel(this.status)
                this.progressPercent = data.progressPercent || this.statusProgress(this.status)
                this.smoothProgress(this.progressPercent)
                this.failureMessage = data.failureMessage || ''
                if (this.isReady) await this.loadDetail()
                if (this.isFailed) this.stopPolling()
            } catch (e) {}
        },
        parseFeedback(essay) {
            try {
                const parsed = JSON.parse(essay.workbookResultJson || essay.aiFeedback)
                if (parsed?.questions) {
                    this.workbookResult = parsed
                    this.maxScore = Number(essay.aiScore || parsed.totalScore || 0) > Number(parsed.totalScore || 0)
                        ? essay.aiScore
                        : this.maxScore
                    if (parsed.questions?.length) {
                        const total = parsed.questions.reduce((sum, item) => sum + Number(item.maxScore || 0), 0)
                        if (total > 0) this.maxScore = total
                    }
                } else {
                    this.gradingResult = parsed
                    if (parsed?.dimensions?.length) {
                        const total = parsed.dimensions.reduce((sum, item) => sum + Number(item.maxScore || 0), 0)
                        if (total > 0) this.maxScore = total
                    }
                }
            } catch (e) {
                this.gradingResult = { totalScore: essay.aiScore, overallComment: essay.aiFeedback, dimensions: [] }
            }
        },
        statusLabel(status) {
            const map = {
                UPLOADED: '作业已提交',
                OCR_PROCESSING: '正在识别文字',
                OCR_DONE: '文字识别完成',
                AI_PROCESSING: '正在AI批改',
                GRADED: 'AI批改完成',
                TEACHER_REVIEWED: '老师已确认成绩',
                FAILED: '批改失败'
            }
            return map[status] || '等待处理'
        },
        statusProgress(status) {
            const map = { UPLOADED: 10, OCR_PROCESSING: 30, OCR_DONE: 55, AI_PROCESSING: 75, GRADED: 100, TEACHER_REVIEWED: 100, FAILED: 100 }
            return map[status] || 0
        },
        buildImageCandidates(imageUrl) {
            const serverOrigin = getServerOrigin()
            let cleanPath = String(imageUrl || '').trim().replace(/^\/+/, '').replace(/^uploads\/+/i, '')
            const hasKnownPrefix = cleanPath.startsWith('essays/') || cleanPath.startsWith('annotated/')
            const primary = hasKnownPrefix
                ? serverOrigin + '/uploads/' + cleanPath
                : serverOrigin + '/uploads/essays/' + cleanPath
            const fallback = serverOrigin + '/uploads/' + cleanPath
            return primary === fallback ? [primary] : [primary, fallback]
        },
        parseUrlList(value) {
            if (!value) return []
            if (Array.isArray(value)) return value.filter(Boolean)
            try {
                const parsed = JSON.parse(value)
                return Array.isArray(parsed) ? parsed.filter(Boolean) : []
            } catch (e) {
                return []
            }
        },
        buildImagePages(essay) {
            const originalUrls = this.parseUrlList(essay?.imageUrls)
            const annotatedUrls = this.parseUrlList(essay?.annotatedImageUrls)
            if (!originalUrls.length && essay?.imageUrl) originalUrls.push(essay.imageUrl)
            if (!annotatedUrls.length && essay?.annotatedImageUrl) annotatedUrls.push(essay.annotatedImageUrl)
            const total = Math.max(originalUrls.length, annotatedUrls.length)
            return Array.from({ length: total }).map((_, index) => {
                const original = originalUrls[index] || originalUrls[0] || ''
                const annotated = annotatedUrls[index] || ''
                const annotatedCandidates = annotated ? this.buildImageCandidates(annotated) : []
                const originalCandidates = original ? this.buildImageCandidates(original) : []
                const candidates = [...annotatedCandidates, ...originalCandidates]
                    .filter((url, idx, arr) => url && arr.indexOf(url) === idx)
                return {
                    raw: original,
                    annotatedRaw: annotated,
                    annotated: Boolean(annotated),
                    url: candidates[0] || '',
                    candidates
                }
            })
        },
        collectAnnotations(source) {
            const list = []
            const seen = new Set()
            const pushOne = (item, fallback = {}) => {
                if (!item) return
                const ann = Object.assign({}, fallback, item)
                const x = this.clampNumber(ann.x, 0, 1)
                const y = this.clampNumber(ann.y, 0, 1)
                const width = this.clampNumber(ann.width || ann.w || 0.18, 0.04, 1)
                const height = this.clampNumber(ann.height || ann.h || 0.08, 0.035, 1)
                const normalized = Object.assign({}, ann, {
                    pageIndex: Number.isFinite(Number(ann.pageIndex)) ? Number(ann.pageIndex) : 0,
                    x,
                    y,
                    width,
                    height,
                    severity: ann.severity || ann.type || 'error'
                })
                const key = [normalized.pageIndex, normalized.type, normalized.x, normalized.y, normalized.width, normalized.height, normalized.questionNo || '', normalized.comment || ''].join('|')
                if (seen.has(key)) return
                seen.add(key)
                list.push(normalized)
            }
            if (Array.isArray(source?.annotations)) {
                source.annotations.forEach(item => pushOne(item))
            }
            if (Array.isArray(source?.questions)) {
                source.questions.forEach((question) => {
                    const fallback = {
                        questionNo: question.questionNo,
                        comment: question.feedback || question.knowledgePoint
                    }
                    if (Array.isArray(question.annotations)) {
                        question.annotations.forEach(item => pushOne(item, fallback))
                    } else if (question.annotation) {
                        pushOne(question.annotation, fallback)
                    }
                })
            }
            if (!list.length && Array.isArray(source?.questions)) {
                source.questions
                    .filter(question => question && (question.isCorrect === false || Number(question.score || 0) < Number(question.maxScore || 0)))
                    .slice(0, 8)
                    .forEach((question, idx) => {
                        pushOne({
                            pageIndex: 0,
                            type: 'pin',
                            x: 0.08,
                            y: Math.min(0.1 + idx * 0.1, 0.86),
                            width: 0.05,
                            height: 0.05,
                            questionNo: question.questionNo,
                            comment: (question.questionNo ? '第 ' + question.questionNo + ' 题：' : '') + (question.feedback || question.knowledgePoint || '此题建议复核订正'),
                            severity: 'error'
                        })
                    })
            }
            if (!list.length && Array.isArray(source?.grammarErrors)) {
                source.grammarErrors.slice(0, 8).forEach((err, idx) => {
                    pushOne({
                        pageIndex: 0,
                        type: 'pin',
                        x: 0.06,
                        y: Math.min(0.08 + idx * 0.11, 0.86),
                        width: 0.04,
                        height: 0.04,
                        comment: err.explanation || err.correction || err.original,
                        severity: 'error'
                    })
                })
            }
            if (!list.length && Array.isArray(source?.dimensions)) {
                source.dimensions
                    .filter(item => Number(item.score || 0) < Number(item.maxScore || 0))
                    .slice(0, 6)
                    .forEach((item, idx) => {
                        pushOne({
                            pageIndex: 0,
                            type: 'pin',
                            x: 0.08,
                            y: Math.min(0.12 + idx * 0.12, 0.84),
                            width: 0.05,
                            height: 0.05,
                            comment: (item.name ? item.name + '：' : '') + (item.comment || '该维度仍有提升空间'),
                            severity: 'warning'
                        })
                    })
            }
            if (!list.length && source?.overallComment) {
                pushOne({
                    pageIndex: 0,
                    type: 'pin',
                    x: 0.08,
                    y: 0.12,
                    width: 0.05,
                    height: 0.05,
                    comment: source.overallComment,
                    severity: 'info'
                })
            }
            return list
        },
        annotationsForPage(pageIndex) {
            return this.collectAnnotations(this.workbookResult || this.gradingResult)
                .filter(item => Number(item.pageIndex || 0) === pageIndex)
        },
        annotationStyle(ann) {
            return {
                left: (ann.x * 100) + '%',
                top: (ann.y * 100) + '%',
                width: (ann.width * 100) + '%',
                height: (ann.height * 100) + '%'
            }
        },
        clampNumber(value, min, max) {
            const num = Number(value)
            if (!Number.isFinite(num)) return min
            return Math.min(Math.max(num, min), max)
        },
        handleImageError(pageIndex = 0) {
            const page = this.imagePages[pageIndex]
            const currentIndex = page?.candidates?.indexOf(page.url) ?? -1
            if (page?.candidates?.length > currentIndex + 1) {
                page.url = page.candidates[currentIndex + 1]
                page.annotated = Boolean(page.annotatedRaw && page.url.includes('/annotated/'))
                this.$set ? this.$set(this.imagePages, pageIndex, page) : (this.imagePages[pageIndex] = page)
                return
            }
            if (page) {
                page.url = ''
            }
            if (pageIndex === 0) {
                this.imageUrl = ''
            }
        },
        previewImage(pageIndex = 0) {
            const urls = this.imagePages.map(page => page.url).filter(Boolean)
            if (urls.length) {
                uni.previewImage({ urls, current: urls[Math.min(pageIndex, urls.length - 1)] })
            }
        }
    }
}
</script>

<style scoped>
.progress-card, .score-card { background: var(--color-surface); border-radius: var(--radius-xl); padding: 36rpx 28rpx; margin-bottom: var(--space-lg); box-shadow: var(--shadow-sm); }
.progress-card.failed { border: 2rpx solid var(--color-danger); }
.progress-title { display: block; font-size: 34rpx; font-weight: 800; color: var(--color-text-primary); }
.progress-status { display: block; font-size: 26rpx; color: var(--color-text-secondary); margin: 8rpx 0 28rpx; }
.progress-percent { display: block; text-align: center; color: var(--color-primary); font-size: 38rpx; font-weight: 800; margin-top: 18rpx; }
.failure-message { display: block; margin-top: 18rpx; color: var(--color-danger); font-size: 24rpx; line-height: 1.6; }
.progress-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 18rpx; }
.progress-chip { max-width: 300rpx; padding: 8rpx 16rpx; border-radius: var(--radius-full); background: var(--color-primary-light); color: var(--color-primary); font-size: 20rpx; font-weight: 700; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.progress-bar-wrap { margin: 22rpx 0 14rpx; padding: 0 4rpx; }
.progress-bar-bg { height: 14rpx; background: #E5E7EB; border-radius: 7rpx; overflow: hidden; position: relative; }
.progress-bar-fill { height: 100%; border-radius: 7rpx; transition: width 0.5s ease-out; position: relative; }
.fill-active { background: linear-gradient(90deg, #2563EB, #38BDF8); }
.fill-failed { background: var(--color-danger); }
.progress-bar-shimmer { position: absolute; top: 0; left: -100%; width: 100%; height: 100%; background: linear-gradient(90deg, transparent, rgba(255,255,255,0.5), transparent); animation: shimmer 2s infinite; }
@keyframes shimmer { 0% { left: -100%; } 100% { left: 200%; } }
.stage-row { display: flex; align-items: center; justify-content: center; gap: 4rpx; margin-top: 20rpx; }
.stage-label { font-size: 20rpx; color: var(--color-text-tertiary); font-weight: 500; transition: color 0.4s; }
.stage-label.active { color: var(--color-primary); }
.stage-dot { width: 18rpx; height: 18rpx; border-radius: 9rpx; background: var(--color-border); }
.stage-dot.active { background: var(--color-primary); }
.stage-line { width: 90rpx; height: 4rpx; background: var(--color-border); }
.stage-line.active { background: var(--color-primary); }
.progress-card.failed .stage-dot.active, .progress-card.failed .stage-line.active { background: var(--color-danger); }
.score-card { background: linear-gradient(135deg, #1D4ED8, #38BDF8); color: #FFF; text-align: center; }
.score-label { font-size: 26rpx; opacity: 0.85; }
.score-row { display: flex; justify-content: center; align-items: baseline; gap: 10rpx; }
.score-number { font-size: 96rpx; font-weight: 900; line-height: 1; }
.score-max { font-size: 28rpx; opacity: 0.78; }
.review-state { position: relative; padding: 20rpx 22rpx; margin-bottom: var(--space-lg); border: 1rpx solid #BFDBFE; border-radius: var(--radius-lg); background: #EFF6FF; }
.review-state.warning { border-color: #FCD34D; background: #FFFBEB; }
.review-state.confirmed { border-color: #A7F3D0; background: #ECFDF5; }
.review-state-title { display: block; padding-right: 170rpx; color: var(--color-text-primary); font-size: 27rpx; font-weight: 800; }
.review-state-desc { display: block; margin-top: 7rpx; padding-right: 140rpx; color: var(--color-text-secondary); font-size: 23rpx; line-height: 1.55; }
.review-state-chip { position: absolute; right: 18rpx; top: 20rpx; padding: 7rpx 13rpx; border-radius: var(--radius-full); background: rgba(255,255,255,0.84); color: #92400E; font-size: 21rpx; font-weight: 800; }
.comment, .dim-comment, .error-desc, .q-line, .q-feedback, .point-suggestion { display: block; font-size: 26rpx; color: var(--color-text-secondary); line-height: 1.8; }
.dimension-item, .error-item, .question-row, .point-row { padding-bottom: 20rpx; margin-bottom: 20rpx; border-bottom: 1rpx solid var(--color-divider); }
.dimension-item:last-child, .error-item:last-child, .question-row:last-child, .point-row:last-child { border-bottom: none; margin-bottom: 0; padding-bottom: 0; }
.dim-head, .q-head { display: flex; justify-content: space-between; margin-bottom: 8rpx; }
.dim-name, .q-no, .point-name { font-size: 28rpx; font-weight: 700; color: var(--color-text-primary); }
.dim-score { font-size: 28rpx; font-weight: 800; color: var(--color-primary); }
.q-status, .point-meta { font-size: 24rpx; font-weight: 800; padding: 4rpx 14rpx; border-radius: var(--radius-full); }
.q-status.ok { background: var(--color-success-light); color: var(--color-success); }
.q-status.bad { background: var(--color-danger-light); color: var(--color-danger); }
.q-point { display: inline-block; margin: 8rpx 0; font-size: 22rpx; color: var(--color-primary); background: var(--color-primary-light); border-radius: var(--radius-full); padding: 6rpx 16rpx; }
.point-meta { display: inline-block; margin: 8rpx 0; background: var(--color-warning-light); color: #B45309; }
.error-title { display: block; color: var(--color-danger); font-size: 26rpx; font-weight: 700; }
.error-fix { display: block; color: var(--color-success); font-size: 26rpx; margin: 6rpx 0; }
.annotated-image-wrap { width: 100%; margin-top: 16rpx; }
.image-stage { position: relative; width: 100%; }
.essay-image { width: 100%; border-radius: var(--radius-md); display: block; }
.annotation-layer { position: absolute; left: 0; top: 0; width: 100%; height: 100%; pointer-events: none; }
.image-annotation { position: absolute; min-width: 56rpx; min-height: 42rpx; border: 4rpx solid #EF4444; border-radius: 10rpx; background: rgba(239, 68, 68, 0.04); box-sizing: border-box; }
.image-annotation.ann-pin { width: 46rpx !important; height: 46rpx !important; min-width: 46rpx; min-height: 46rpx; border-radius: 50%; border: 4rpx solid #FFFFFF; background: #EF4444; box-shadow: 0 4rpx 14rpx rgba(239, 68, 68, 0.42); transform: translate(-50%, -50%); }
.image-annotation.ann-warning { border-color: #F59E0B; background: rgba(245, 158, 11, 0.12); }
.image-annotation.ann-info, .image-annotation.ann-note { border-color: #2563EB; background: rgba(37, 99, 235, 0.1); }
.annotation-index { position: absolute; left: -16rpx; top: -20rpx; width: 36rpx; height: 36rpx; line-height: 36rpx; text-align: center; border-radius: 18rpx; background: #EF4444; color: #FFF; font-size: 20rpx; font-weight: 800; }
.image-annotation.ann-pin .annotation-index { position: static; display: block; width: 100%; height: 100%; line-height: 38rpx; border-radius: 50%; background: transparent; font-size: 22rpx; }
.annotation-list { margin-top: 18rpx; padding: 18rpx 20rpx; border-radius: var(--radius-md); background: #F8FAFC; border: 1rpx solid var(--color-border); }
.annotation-legend { display: block; margin: -4rpx 0 16rpx; color: var(--color-text-tertiary); font-size: 22rpx; line-height: 1.5; }
.annotation-note { display: flex; gap: 14rpx; align-items: flex-start; margin-bottom: 12rpx; }
.annotation-note:last-child { margin-bottom: 0; }
.annotation-note-index { flex-shrink: 0; min-width: 48rpx; height: 36rpx; line-height: 36rpx; border-radius: 18rpx; text-align: center; background: #EF4444; color: #FFF; font-size: 20rpx; font-weight: 800; }
.annotation-note-text { flex: 1; color: var(--color-text-secondary); font-size: 24rpx; line-height: 1.55; }
</style>
