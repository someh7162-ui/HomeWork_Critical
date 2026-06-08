<template>
    <view class="page-container">
        <!-- 批改进度 -->
        <view v-if="!isReady" class="progress-card" :class="{ failed: isFailed }">
            <view class="progress-head">
                <text class="progress-title">{{ isFailed ? '批改失败' : '正在批改' }}</text>
                <text class="progress-chip">{{ statusText }}</text>
            </view>
            <view class="progress-bar-wrap">
                <view class="progress-bar-bg">
                    <view class="progress-bar-fill"
                          :style="{ width: displayPercent + '%' }"
                          :class="isFailed ? 'fill-failed' : 'fill-active'">
                    </view>
                    <view class="progress-bar-shimmer" v-if="!isFailed"></view>
                </view>
            </view>
            <text class="progress-percent">{{ displayPercent }}%</text>
            <text v-if="isFailed" class="failure-message">
                {{ failureMessage || '请稍后重试，或检查图片是否清晰后重新上传。' }}
            </text>
            <view class="stage-row">
                <text class="stage-label" :class="{ active: displayPercent >= 10 }">上传</text>
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

        <!-- 分数大卡片 -->
        <view v-if="isReady" class="score-hero" :class="scoreLevel">
            <view class="score-topline">
                <text class="score-label">AI 评分</text>
                <text class="score-status">{{ status === 'TEACHER_REVIEWED' ? '已确认' : '待确认' }}</text>
            </view>
            <view class="score-row">
                <text class="score-number">{{ displayScore }}</text>
                <text class="score-divider">/</text>
                <text class="score-max">满分{{ maxScore }}</text>
            </view>
            <text class="score-level-tag">{{ scoreLevelText }}</text>
        </view>

        <view v-if="isReady" class="review-banner" :class="{ warning: essayDetail?.reviewRequired, confirmed: status === 'TEACHER_REVIEWED' }">
            <view>
                <text class="review-banner-title">{{ reviewBannerTitle }}</text>
                <text class="review-banner-desc">{{ reviewBannerDesc }}</text>
            </view>
            <text class="confidence-chip">{{ confidenceLabel }} {{ confidencePercent }}%</text>
        </view>

        <view v-if="isReady" class="feedback-overview">
            <view v-for="item in summaryItems" :key="item.label" class="overview-tile" :class="'overview-' + item.tone">
                <text class="overview-value">{{ item.value }}</text>
                <text class="overview-label">{{ item.label }}</text>
            </view>
        </view>

        <!-- 教师最终得分 -->
        <view v-if="isReady" class="card final-card">
            <text class="section-title">教师复核确认</text>
            <view class="final-score-row">
                <input
                    class="final-score-input"
                    type="digit"
                    v-model="finalScoreInput"
                    placeholder="请输入最终分数"
                />
                <button class="save-score-btn" @click="saveFinalScore" :disabled="savingScore">
                    {{ savingScore ? '保存中...' : '确认并保存' }}
                </button>
            </view>
            <textarea class="review-note-input" v-model="teacherReviewNote" auto-height placeholder="可填写复核说明，例如：已核对原图和第 3 题答案。" />
            <text class="final-score-hint">确认后，最终分、批注修订和复核状态会同步到学生端。</text>
            <text v-if="essayDetail?.finalScore !== null && essayDetail?.finalScore !== undefined" class="final-score-value">
                当前最终分：{{ essayDetail.finalScore }}
            </text>
        </view>

        <!-- 学生信息 -->
        <view v-if="isReady" class="student-bar surface-panel">
            <text class="student-bar-label">学生</text>
            <text class="student-bar-name">{{ studentName }}</text>
        </view>

        <!-- 总评 -->
        <view v-if="isReady" class="card comment-card">
            <text class="section-title">总体评价</text>
            <text class="overall-comment">{{ workbookResult?.overallComment || gradingResult?.overallComment || '暂无评价' }}</text>
        </view>

        <view class="card" v-if="isReady && workbookResult?.questions?.length">
            <text class="section-title">逐题结果</text>
            <view v-for="item in workbookResult.questions" :key="item.questionNo" class="workbook-question">
                <view class="workbook-head">
                    <text class="workbook-no">第 {{ item.questionNo }} 题</text>
                    <text :class="['workbook-score', item.isCorrect ? 'ok' : 'bad']">{{ item.score }} / {{ item.maxScore }}</text>
                </view>
                <text class="workbook-line">学生答案：{{ item.studentAnswer || '未识别/空白' }}</text>
                <text class="workbook-line">参考答案：{{ item.correctAnswer || '-' }}</text>
                <text class="workbook-point">{{ item.knowledgePoint || '知识点未分类' }}</text>
                <text class="workbook-feedback">{{ item.feedback || '' }}</text>
            </view>
        </view>

        <view class="card" v-if="isReady && workbookResult?.knowledgePoints?.length">
            <text class="section-title">知识点统计</text>
            <view v-for="point in workbookResult.knowledgePoints" :key="point.name" class="knowledge-row">
                <text class="knowledge-name">{{ point.name }}</text>
                <text class="knowledge-meta">错 {{ point.wrongCount || 0 }} / {{ point.totalCount || 0 }}</text>
                <text class="knowledge-suggestion">{{ point.suggestion || '建议回看对应例题并整理错因。' }}</text>
            </view>
        </view>

        <!-- 各维度分数 -->
        <view class="card" v-if="isReady && gradingResult?.dimensions?.length">
            <text class="section-title">评分明细</text>
            <view v-for="(dim, idx) in gradingResult.dimensions" :key="idx" class="dimension-item">
                <view class="dim-header">
                    <text class="dim-name">{{ dim.name }}</text>
                    <text class="dim-score">{{ dim.score }} / {{ dim.maxScore }}</text>
                </view>
                <view class="progress-bar">
                    <view class="progress-fill" :class="'fill-' + (idx % 4)"
                          :style="{ width: (dim.score / dim.maxScore * 100) + '%' }">
                    </view>
                </view>
                <text class="dim-comment">{{ dim.comment }}</text>
            </view>
        </view>

        <!-- 批注详情（有痕批改） -->
        <view class="card" v-if="isReady && gradingResult?.grammarErrors?.length">
            <text class="section-title">批注详情（{{ gradingResult.grammarErrors.length }}处）</text>
            <view v-for="(err, idx) in gradingResult.grammarErrors" :key="idx" class="grammar-item">
                <view class="grammar-header">
                    <text class="grammar-index">#{{ idx + 1 }}</text>
                    <text v-if="err.errorType" class="error-type-tag" :class="errorTypeClass(err.errorType)">{{ err.errorType }}</text>
                    <text v-if="err.position" class="error-position">📍 {{ err.position }}</text>
                </view>
                <view class="grammar-row">
                    <text class="grammar-label">❌</text>
                    <view class="grammar-original-wrap"><text class="grammar-original">{{ err.original }}</text></view>
                </view>
                <view class="grammar-row">
                    <text class="grammar-label">✅</text>
                    <view class="grammar-correction-wrap"><text class="grammar-correction">{{ err.correction }}</text></view>
                </view>
                <text class="grammar-explanation">{{ err.explanation }}</text>
                <text v-if="err.knowledgePoint" class="kp-chip">📚 {{ err.knowledgePoint }}</text>
            </view>
        </view>

        <!-- 知识点巩固 -->
        <view class="card kp-card" v-if="isReady && gradingResult?.knowledgePoints?.length">
            <text class="section-title">📖 知识点巩固</text>
            <text class="kp-desc">以下知识点建议重点复习，点击展开详情</text>
            <view v-for="(kp, idx) in gradingResult.knowledgePoints" :key="'kp'+idx" class="kp-item"
                  @click="toggleKp(idx)">
                <view class="kp-header">
                    <text class="kp-topic">{{ kp.topic }}</text>
                    <text class="kp-arrow">{{ expandedKps[idx] ? '▾' : '▸' }}</text>
                </view>
                <view v-if="expandedKps[idx]" class="kp-body">
                    <text class="kp-text">{{ kp.description }}</text>
                    <view v-if="kp.suggestion" class="kp-suggestion">
                        <text class="kp-suggest-label">💡 巩固建议</text>
                        <text class="kp-suggest-text">{{ kp.suggestion }}</text>
                    </view>
                </view>
            </view>
        </view>

        <!-- 作文原图 -->
        <view v-if="isReady" class="card">
            <text class="section-title">作文原图</text>
            <text v-if="hasVisualAnnotations || hasAnnotatedImages" class="annotation-legend">{{ annotationLegendText }}</text>
            <view v-if="imagePages.length">
                <view v-for="(page, pageIndex) in imagePages" :key="pageIndex" class="annotated-image-wrap">
                    <view class="image-stage" :id="'image-stage-' + pageIndex">
                        <image
                            :src="page.url"
                            mode="widthFix"
                            class="essay-image"
                            @click="previewImage(pageIndex)"
                            @error="handleImageError(pageIndex)"
                        />
                        <view class="annotation-layer" v-if="annotationDraftsForPage(pageIndex).length">
                            <view
                                v-for="(ann, idx) in annotationDraftsForPage(pageIndex)"
                                :key="ann._draftKey"
                                class="image-annotation"
                                :class="['ann-' + (ann.severity || 'error'), ann.type === 'pin' ? 'ann-pin' : '', selectedAnnotationKey === ann._draftKey ? 'selected' : '']"
                                :style="annotationStyle(ann)"
                                @click.stop="focusAnnotation(ann)"
                                @touchstart.stop.prevent="startDragAnnotation(ann, pageIndex, $event)"
                                @touchmove.stop.prevent="dragAnnotation($event)"
                                @touchend.stop.prevent="endDragAnnotation"
                                @touchcancel.stop.prevent="endDragAnnotation"
                            >
                                <text class="annotation-index">{{ idx + 1 }}</text>
                            </view>
                        </view>
                    </view>
                    <view class="annotation-list" v-if="annotationDraftsForPage(pageIndex).length">
                        <text class="annotation-edit-hint">可拖动红框或圆点修正位置，也可以直接修订说明。</text>
                        <view v-for="(ann, idx) in annotationDraftsForPage(pageIndex)" :key="'note-' + ann._draftKey" class="annotation-note" :class="{ selected: selectedAnnotationKey === ann._draftKey }">
                            <text class="annotation-note-index">#{{ idx + 1 }}</text>
                            <textarea class="annotation-note-input" v-model="ann.comment" auto-height placeholder="请输入批注说明" @focus="focusAnnotation(ann)" />
                        </view>
                    </view>
                </view>
            </view>
            <text v-else class="image-fail-text">图片加载失败</text>
        </view>

        <!-- 底部操作栏 -->
        <view class="bottom-bar">
            <button class="bottom-btn btn-retry" @click="handleRegrade" :disabled="regrading || (!isReady && !isFailed)">
                {{ regrading ? '评分中...' : (isFailed ? '重试评分' : '重新评分') }}
            </button>
            <button class="bottom-btn btn-back" @click="goBack">继续批改</button>
            <button class="bottom-btn btn-records" @click="goHistory">批改记录</button>
        </view>
    </view>
</template>

<script>
import { getEssayDetail, getEssayProgress, getServerOrigin, regradeEssay, reviewEssay } from '@/api/index.js'

export default {
    data() {
        return {
            essayId: '',
            studentName: '',
            gradingResult: null,
            workbookResult: null,
            maxScore: 15,
            ocrText: '',
            imageUrl: '',
            essayImageCandidates: [],
            imagePages: [],
            essayDetail: null,
            finalScoreInput: '',
            teacherReviewNote: '',
            annotationDrafts: [],
            selectedAnnotationKey: '',
            dragState: null,
            regrading: false,
            savingScore: false,
            status: 'UPLOADED',
            statusText: '作文已上传',
            progressPercent: 10,
            displayPercent: 10,
            animTimer: null,
            failureMessage: '',
            pollTimer: null,
            expandedKps: {}
        }
    },
    computed: {
        isReady() {
            return this.status === 'GRADED' || this.status === 'TEACHER_REVIEWED'
        },
        isFailed() {
            return this.status === 'FAILED'
        },
        scoreLevel() {
            const s = this.displayScore || 0
            const m = this.maxScore || 15
            const pct = s / m
            if (pct >= 0.9) return 'level-a'
            if (pct >= 0.75) return 'level-b'
            if (pct >= 0.6) return 'level-c'
            return 'level-d'
        },
        scoreLevelText() {
            const s = this.displayScore || 0
            const m = this.maxScore || 15
            const pct = s / m
            if (pct >= 0.9) return '优秀'
            if (pct >= 0.75) return '良好'
            if (pct >= 0.6) return '及格'
            return '需努力'
        },
        displayScore() {
            if (this.essayDetail?.finalScore !== null && this.essayDetail?.finalScore !== undefined) return this.essayDetail.finalScore
            return this.workbookResult?.totalScore || this.gradingResult?.totalScore || this.essayDetail?.aiScore || 0
        },
        visualAnnotations() {
            return this.collectAnnotations(this.workbookResult || this.gradingResult)
        },
        hasVisualAnnotations() {
            return this.annotationDrafts.length > 0 || this.visualAnnotations.length > 0
        },
        hasAnnotatedImages() {
            return this.imagePages.some(page => page.annotatedUrl)
        },
        annotationLegendText() {
            if (this.hasAnnotatedImages) return '已生成可放大的批注成品图；当前页仍可拖动红框修正位置。'
            return '红框为原图定位批注，圆点为批改要点提示。'
        },
        confidencePercent() {
            return Math.round(Number(this.essayDetail?.confidenceScore || 0.78) * 100)
        },
        confidenceLabel() {
            const map = { HIGH: '高置信度', MEDIUM: '中置信度', LOW: '低置信度' }
            return map[this.essayDetail?.confidenceLevel] || '中置信度'
        },
        reviewBannerTitle() {
            if (this.status === 'TEACHER_REVIEWED') return '教师已完成复核'
            if (this.essayDetail?.reviewRequired) return '建议优先复核'
            return '等待教师确认'
        },
        reviewBannerDesc() {
            if (this.status === 'TEACHER_REVIEWED') return '最终分和批注说明已同步至学生端。'
            if (this.essayDetail?.reviewRequired) return '图片清晰度或部分答案判断存在不确定性，请核对原图后确认。'
            return 'AI 已完成初步批改，请检查分数和批注说明。'
        },
        issueCount() {
            if (this.workbookResult?.questions?.length) {
                return this.workbookResult.questions.filter(item => {
                    return item?.isCorrect === false || Number(item?.score || 0) < Number(item?.maxScore || 0)
                }).length
            }
            return this.gradingResult?.grammarErrors?.length || this.annotationDrafts.length || 0
        },
        weakPointCount() {
            return this.workbookResult?.knowledgePoints?.length || this.gradingResult?.knowledgePoints?.length || 0
        },
        dimensionCount() {
            return this.gradingResult?.dimensions?.length || this.workbookResult?.questions?.length || 0
        },
        summaryItems() {
            return [
                { label: '待关注问题', value: this.issueCount, tone: this.issueCount > 0 ? 'danger' : 'success' },
                { label: '知识点', value: this.weakPointCount, tone: 'primary' },
                { label: '评分项', value: this.dimensionCount, tone: 'mint' }
            ]
        }
    },
    onLoad(options) {
        this.essayId = options.essayId
        this.studentName = decodeURIComponent(options.studentName || '')
        this.loadResult()
    },
    onUnload() {
        this.stopPolling()
        this.stopAnim()
    },
    methods: {
        smoothProgress(target) {
            this.stopAnim()
            if (target <= this.displayPercent) {
                this.displayPercent = target
                return
            }
            this.animTimer = setInterval(() => {
                if (this.displayPercent >= target) {
                    this.displayPercent = target
                    this.stopAnim()
                } else {
                    this.displayPercent = Math.min(this.displayPercent + 2, target)
                }
            }, 80)
        },
        stopAnim() {
            if (this.animTimer) {
                clearInterval(this.animTimer)
                this.animTimer = null
            }
        },
        async loadResult() {
            try {
                const res = await getEssayDetail(this.essayId)
                if (res.code === 200) {
                    const essay = res.data
                    this.essayDetail = essay
                    this.status = essay.status || 'UPLOADED'
                    this.statusText = this.statusLabel(this.status)
                    this.progressPercent = this.statusProgress(this.status)
                    this.smoothProgress(this.progressPercent)
                    this.failureMessage = essay.failureMessage || ''
                    this.ocrText = essay.ocrText
                    this.teacherReviewNote = essay.teacherReviewNote || ''
                    this.finalScoreInput = essay.finalScore !== null && essay.finalScore !== undefined
                        ? String(essay.finalScore)
                        : ''
                    if (essay.imageUrl || essay.annotatedImageUrl) {
                        this.imagePages = this.buildImagePages(essay)
                        this.imageUrl = this.imagePages[0]?.url || ''
                        this.essayImageCandidates = this.imagePages[0]?.candidates || []
                    }
                    if (this.isReady) {
                        try {
                            const feedback = JSON.parse(essay.workbookResultJson || essay.aiFeedback)
                            if (feedback?.questions) {
                                this.workbookResult = feedback
                                this.gradingResult = { totalScore: feedback.totalScore || essay.aiScore, overallComment: feedback.overallComment, dimensions: [] }
                                if (feedback.questions?.length) {
                                    const total = feedback.questions.reduce((sum, item) => sum + Number(item.maxScore || 0), 0)
                                    if (total > 0) this.maxScore = total
                                }
                            } else {
                                this.workbookResult = null
                                this.gradingResult = feedback
                            }
                            if (feedback?.dimensions?.length) {
                                const total = feedback.dimensions.reduce((sum, item) => sum + Number(item.maxScore || 0), 0)
                                if (total > 0) this.maxScore = total
                            }
                            this.prepareAnnotationDrafts(feedback)
                        } catch(e) {
                            this.gradingResult = { totalScore: essay.aiScore, overallComment: essay.aiFeedback, dimensions: [] }
                            this.prepareAnnotationDrafts(this.gradingResult)
                        }
                        this.stopPolling()
                    } else if (!this.isFailed) {
                        this.startPolling()
                    }
                }
            } catch(e) {}
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
                const res = await getEssayProgress(this.essayId)
                if (res.code !== 200 || !res.data) return
                const data = res.data
                this.status = data.status || this.status
                this.statusText = data.statusText || this.statusLabel(this.status)
                this.progressPercent = data.progressPercent || this.statusProgress(this.status)
                this.smoothProgress(this.progressPercent)
                this.failureMessage = data.failureMessage || ''
                if (this.isReady) {
                    this.stopPolling()
                    await this.loadResult()
                } else if (this.isFailed) {
                    this.stopPolling()
                }
            } catch (e) {}
        },
        async handleRegrade() {
            if (this.regrading) return
            this.regrading = true
            uni.showLoading({ title: '启动评分...', mask: true })
            try {
                const res = await regradeEssay(this.essayId)
                uni.hideLoading()
                if (res.code === 200) {
                    this.gradingResult = null
                    this.workbookResult = null
                    this.annotationDrafts = []
                    this.status = res.data?.status || 'OCR_PROCESSING'
                    this.statusText = res.data?.message || this.statusLabel(this.status)
                    this.progressPercent = res.data?.progressPercent || 30
                    this.smoothProgress(this.progressPercent)
                    this.failureMessage = ''
                    uni.showToast({ title: '已开始评分', icon: 'success' })
                    this.startPolling()
                } else {
                    uni.showToast({ title: res.message || '重新评分失败', icon: 'none' })
                }
            } catch (e) {
                uni.hideLoading()
                uni.showToast({ title: '重新评分失败', icon: 'none' })
            }
            this.regrading = false
        },
        async saveFinalScore() {
            const score = Number(this.finalScoreInput)
            if (this.finalScoreInput === '' || Number.isNaN(score)) {
                uni.showToast({ title: '请输入有效分数', icon: 'none' })
                return
            }
            if (score < 0 || score > this.maxScore) {
                uni.showToast({ title: '分数需在 0 到满分之间', icon: 'none' })
                return
            }
            this.savingScore = true
            try {
                const res = await reviewEssay(this.essayId, {
                    finalScore: score,
                    teacherReviewNote: this.teacherReviewNote,
                    feedback: this.buildReviewFeedback()
                })
                if (res.code === 200) {
                    this.essayDetail = res.data
                    this.finalScoreInput = String(res.data.finalScore)
                    this.status = res.data.status || 'TEACHER_REVIEWED'
                    uni.showToast({ title: '复核结果已同步', icon: 'success' })
                } else {
                    uni.showToast({ title: res.message || '保存失败', icon: 'none' })
                }
            } catch (e) {
                uni.showToast({ title: '保存失败', icon: 'none' })
            }
            this.savingScore = false
        },
        statusLabel(status) {
            const map = {
                UPLOADED: '作文已上传',
                OCR_PROCESSING: '正在识别作文文字',
                OCR_DONE: '文字识别完成',
                AI_PROCESSING: '正在进行AI评分',
                GRADED: 'AI评分完成，等待老师确认',
                TEACHER_REVIEWED: '老师已确认最终成绩',
                FAILED: '批改失败'
            }
            return map[status] || '等待处理'
        },
        statusProgress(status) {
            const map = {
                UPLOADED: 10,
                OCR_PROCESSING: 30,
                OCR_DONE: 55,
                AI_PROCESSING: 75,
                GRADED: 100,
                TEACHER_REVIEWED: 100,
                FAILED: 100
            }
            return map[status] || 0
        },
        handleImageError(pageIndex = 0) {
            const page = this.imagePages[pageIndex]
            const currentIndex = page?.candidates?.indexOf(page.url) ?? -1
            if (page?.candidates?.length > currentIndex + 1) {
                page.url = page.candidates[currentIndex + 1]
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
            const urls = this.imagePages.map(page => page.previewUrl || page.url).filter(Boolean)
            if (urls.length) {
                uni.previewImage({ urls, current: urls[Math.min(pageIndex, urls.length - 1)] })
            } else if (this.imageUrl) {
                uni.previewImage({ urls: [this.imageUrl] })
            }
        },
        toggleKp(idx) {
            this.expandedKps[idx] = !this.expandedKps[idx]
            this.$forceUpdate()
        },
        errorTypeClass(type) {
            const t = (type || '').trim()
            if (t === '语法' || t === '拼写') return 'err-grammar'
            if (t === '逻辑') return 'err-logic'
            if (t === '计算') return 'err-calc'
            return 'err-other'
        },
        buildEssayImageCandidates(imageUrl) {
            if (!imageUrl) return []
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
                const originalCandidates = original ? this.buildEssayImageCandidates(original) : []
                const annotatedCandidates = annotated ? this.buildEssayImageCandidates(annotated) : []
                return {
                    raw: original,
                    annotatedRaw: annotated,
                    annotatedUrl: annotatedCandidates[0] || '',
                    previewUrl: annotatedCandidates[0] || originalCandidates[0] || '',
                    url: originalCandidates[0] || annotatedCandidates[0] || '',
                    candidates: originalCandidates,
                    annotatedCandidates
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
            return this.visualAnnotations.filter(item => Number(item.pageIndex || 0) === pageIndex)
        },
        prepareAnnotationDrafts(source) {
            this.annotationDrafts = this.collectAnnotations(source).map((item, index) => ({
                ...item,
                comment: item.comment || item.feedback || item.questionNo || '请查看标注位置',
                _draftKey: 'ann-' + index
            }))
        },
        annotationDraftsForPage(pageIndex) {
            return this.annotationDrafts.filter(item => Number(item.pageIndex || 0) === pageIndex)
        },
        focusAnnotation(ann) {
            this.selectedAnnotationKey = ann?._draftKey || ''
        },
        startDragAnnotation(ann, pageIndex, event) {
            const touch = event?.touches?.[0] || event?.changedTouches?.[0]
            if (!touch) return
            this.focusAnnotation(ann)
            this.dragState = {
                ann,
                pageIndex,
                startClientX: touch.clientX,
                startClientY: touch.clientY,
                startX: Number(ann.x || 0),
                startY: Number(ann.y || 0),
                stageWidth: 0,
                stageHeight: 0
            }
            uni.createSelectorQuery().in(this)
                .select('#image-stage-' + pageIndex)
                .boundingClientRect(rect => {
                    if (!this.dragState || this.dragState.ann !== ann || !rect) return
                    this.dragState.stageWidth = rect.width || 1
                    this.dragState.stageHeight = rect.height || 1
                })
                .exec()
        },
        dragAnnotation(event) {
            if (!this.dragState?.ann) return
            const touch = event?.touches?.[0] || event?.changedTouches?.[0]
            if (!touch || !this.dragState.stageWidth || !this.dragState.stageHeight) return
            const ann = this.dragState.ann
            const dx = (touch.clientX - this.dragState.startClientX) / this.dragState.stageWidth
            const dy = (touch.clientY - this.dragState.startClientY) / this.dragState.stageHeight
            const isPin = ann.type === 'pin'
            const maxX = isPin ? 0.98 : Math.max(0, 1 - Number(ann.width || 0.18))
            const maxY = isPin ? 0.98 : Math.max(0, 1 - Number(ann.height || 0.08))
            ann.x = this.clampNumber(this.dragState.startX + dx, isPin ? 0.02 : 0, maxX)
            ann.y = this.clampNumber(this.dragState.startY + dy, isPin ? 0.02 : 0, maxY)
            this.$forceUpdate()
        },
        endDragAnnotation() {
            this.dragState = null
        },
        buildReviewFeedback() {
            const source = this.workbookResult || this.gradingResult || {}
            const feedback = JSON.parse(JSON.stringify(source))
            feedback.annotations = this.annotationDrafts.map(({ _draftKey, ...item }) => item)
            if (Array.isArray(feedback.questions)) {
                feedback.questions = feedback.questions.map(item => ({ ...item, annotations: [] }))
            }
            return feedback
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
        }
    }
}
</script>

<style scoped>
.page-container {
    padding: var(--space-lg) var(--space-lg);
    padding-bottom: 156rpx;
    background:
        linear-gradient(180deg, rgba(219, 234, 254, 0.68), rgba(245, 247, 251, 0) 340rpx),
        var(--color-bg);
}

/* 批改进度 */
.progress-card {
    background: linear-gradient(180deg, #FFFFFF, #F8FAFC);
    border-radius: var(--radius-2xl);
    padding: 48rpx 32rpx;
    margin-bottom: var(--space-lg);
    box-shadow: var(--shadow-md);
    border: 1rpx solid rgba(221, 229, 240, 0.86);
    text-align: center;
}

.progress-card.failed { border: 2rpx solid var(--color-danger); }

.progress-title {
    font-size: 34rpx;
    font-weight: 800;
    color: var(--color-text-primary);
    display: block;
    margin-bottom: 12rpx;
}

.progress-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 12rpx;
}

.progress-chip {
    max-width: 280rpx;
    padding: 8rpx 16rpx;
    border-radius: var(--radius-full);
    background: var(--color-primary-light);
    color: var(--color-primary);
    font-size: 20rpx;
    font-weight: 700;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.progress-status {
    font-size: 26rpx;
    color: var(--color-text-secondary);
    line-height: 1.6;
    display: block;
    margin-bottom: 28rpx;
}

.progress-bar-wrap {
    margin-bottom: 14rpx;
    padding: 0 4rpx;
}

.progress-bar-bg {
    height: 14rpx;
    background: #E5E7EB;
    border-radius: 7rpx;
    overflow: hidden;
    position: relative;
}

.progress-bar-fill {
    height: 100%;
    border-radius: 7rpx;
    transition: width 0.5s ease-out;
    position: relative;
}

.fill-active { background: linear-gradient(90deg, #2563EB, #38BDF8); }
.fill-failed { background: var(--color-danger); }

.progress-bar-shimmer {
    position: absolute;
    top: 0;
    left: -100%;
    width: 100%;
    height: 100%;
    background: linear-gradient(90deg, transparent, rgba(255,255,255,0.5), transparent);
    animation: shimmer 2s infinite;
}

@keyframes shimmer {
    0% { left: -100%; }
    100% { left: 200%; }
}

.progress-percent {
    font-size: 40rpx;
    font-weight: 800;
    color: var(--color-primary);
    display: block;
    margin-bottom: 20rpx;
}

.progress-card.failed .progress-percent { color: var(--color-danger); }

.failure-message {
    display: block;
    margin: 0 0 22rpx;
    padding: 16rpx 18rpx;
    border-radius: var(--radius-md);
    background: #FEF2F2;
    color: var(--color-danger);
    font-size: 24rpx;
    line-height: 1.6;
    text-align: left;
}

.stage-row {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 4rpx;
}

.stage-label {
    font-size: 20rpx;
    color: var(--color-text-tertiary);
    font-weight: 500;
    transition: color 0.4s;
}
.stage-label.active { color: var(--color-primary); }

.stage-dot {
    width: 18rpx;
    height: 18rpx;
    border-radius: 9rpx;
    background: var(--color-border);
}

.stage-dot.active { background: var(--color-primary); }

.stage-line {
    width: 120rpx;
    height: 4rpx;
    background: var(--color-border);
}

.stage-line.active { background: var(--color-primary); }

.progress-card.failed .stage-dot.active,
.progress-card.failed .stage-line.active {
    background: var(--color-danger);
}

/* 分数大卡片 */
.score-hero {
    border-radius: var(--radius-2xl);
    padding: 34rpx 32rpx 40rpx;
    text-align: center;
    margin-bottom: var(--space-lg);
    color: #FFF;
    position: relative;
    overflow: hidden;
    box-shadow: var(--shadow-lg);
    animation: fadeInUp 0.42s var(--ease-out) backwards;
}

.score-hero::before {
    content: '';
    position: absolute;
    left: -80rpx;
    top: -100rpx;
    width: 260rpx;
    height: 260rpx;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.14);
}

.score-hero::after {
    content: '';
    position: absolute;
    right: -48rpx;
    bottom: -88rpx;
    width: 220rpx;
    height: 220rpx;
    border-radius: 50%;
    border: 2rpx solid rgba(255, 255, 255, 0.18);
}

.score-topline {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 18rpx;
    position: relative;
    z-index: 1;
}

.score-hero.level-a { background: linear-gradient(135deg, #065F46, #10B981); }
.score-hero.level-b { background: linear-gradient(135deg, #1E40AF, #3B82F6); }
.score-hero.level-c { background: linear-gradient(135deg, #92400E, #F59E0B); }
.score-hero.level-d { background: linear-gradient(135deg, #991B1B, #EF4444); }

.score-label { font-size: 26rpx; opacity: 0.85; display: block; }

.score-status {
    font-size: 22rpx;
    color: rgba(255, 255, 255, 0.9);
    background: rgba(255, 255, 255, 0.16);
    border: 1rpx solid rgba(255, 255, 255, 0.22);
    border-radius: var(--radius-full);
    padding: 6rpx 18rpx;
}

.score-row {
    display: flex;
    align-items: baseline;
    justify-content: center;
    gap: 8rpx;
    position: relative;
    z-index: 1;
}

.score-number { font-size: 96rpx; font-weight: 800; line-height: 1; }
.score-divider { font-size: 36rpx; opacity: 0.5; }
.score-max { font-size: 28rpx; opacity: 0.75; }

.score-level-tag {
    display: inline-block;
    background: rgba(255, 255, 255, 0.22);
    padding: 8rpx 32rpx;
    border-radius: var(--radius-full);
    font-size: 24rpx;
    margin-top: 16rpx;
    font-weight: 500;
    position: relative;
    z-index: 1;
}

.final-card {
    border-left: 6rpx solid var(--color-success);
    box-shadow: var(--shadow-sm);
}

.review-banner {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16rpx;
    padding: 22rpx 24rpx;
    margin-bottom: var(--space-md);
    border: 1rpx solid #BFDBFE;
    border-radius: var(--radius-xl);
    background: rgba(239, 246, 255, 0.94);
    box-shadow: var(--shadow-xs);
    animation: fadeInUp 0.42s 0.06s var(--ease-out) backwards;
}
.review-banner.warning { border-color: #FCD34D; background: #FFFBEB; }
.review-banner.confirmed { border-color: #A7F3D0; background: #ECFDF5; }
.review-banner-title { display: block; color: var(--color-text-primary); font-size: 27rpx; font-weight: 800; }
.review-banner-desc { display: block; margin-top: 5rpx; color: var(--color-text-secondary); font-size: 22rpx; line-height: 1.5; }
.confidence-chip { flex-shrink: 0; padding: 7rpx 13rpx; border-radius: var(--radius-full); background: rgba(255,255,255,0.82); color: #92400E; font-size: 21rpx; font-weight: 800; }

.feedback-overview {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 12rpx;
    margin-bottom: var(--space-lg);
    animation: fadeInUp 0.42s 0.12s var(--ease-out) backwards;
}

.overview-tile {
    min-height: 112rpx;
    padding: 18rpx 12rpx;
    border-radius: var(--radius-lg);
    background: #FFFFFF;
    border: 1rpx solid rgba(221, 229, 240, 0.9);
    box-shadow: var(--shadow-xs);
    text-align: center;
    box-sizing: border-box;
}

.overview-value {
    display: block;
    font-size: 38rpx;
    line-height: 1.1;
    font-weight: 900;
    color: var(--color-text-primary);
}

.overview-label {
    display: block;
    margin-top: 8rpx;
    font-size: 21rpx;
    color: var(--color-text-secondary);
    font-weight: 700;
}

.overview-danger .overview-value { color: var(--color-danger); }
.overview-success .overview-value { color: var(--color-success); }
.overview-primary .overview-value { color: var(--color-primary); }
.overview-mint .overview-value { color: var(--color-mint); }

.comment-card {
    border-left: 6rpx solid var(--color-primary);
}

.workbook-question, .knowledge-row {
    padding: 20rpx;
    margin-bottom: 16rpx;
    border: 1rpx solid var(--color-divider);
    border-radius: var(--radius-lg);
    background: linear-gradient(180deg, #FFFFFF, #F8FAFC);
}

.workbook-question:last-child, .knowledge-row:last-child {
    margin-bottom: 0;
}

.workbook-head {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 10rpx;
}

.workbook-no, .knowledge-name {
    font-size: 28rpx;
    font-weight: 700;
    color: var(--color-text-primary);
}

.workbook-score, .knowledge-meta {
    font-size: 24rpx;
    font-weight: 800;
    border-radius: var(--radius-full);
    padding: 4rpx 14rpx;
}

.workbook-score.ok {
    background: var(--color-success-light);
    color: var(--color-success);
}

.workbook-score.bad, .knowledge-meta {
    background: var(--color-danger-light);
    color: var(--color-danger);
}

.workbook-line, .workbook-feedback, .knowledge-suggestion {
    display: block;
    font-size: 25rpx;
    color: var(--color-text-secondary);
    line-height: 1.7;
}

.workbook-point {
    display: inline-block;
    margin: 8rpx 0;
    padding: 5rpx 16rpx;
    border-radius: var(--radius-full);
    color: var(--color-primary);
    background: var(--color-primary-light);
    font-size: 22rpx;
    font-weight: 700;
}

/* 学生信息 */
.student-bar {
    display: flex;
    align-items: center;
    gap: var(--space-sm);
    background: var(--color-surface);
    border-radius: var(--radius-lg);
    padding: 20rpx 24rpx;
    margin-bottom: var(--space-md);
    box-shadow: var(--shadow-sm);
    border: 1rpx solid rgba(221, 229, 240, 0.9);
}

.student-bar-label {
    font-size: 24rpx;
    color: var(--color-text-tertiary);
    background: var(--color-divider);
    padding: 4rpx 16rpx;
    border-radius: var(--radius-full);
}

.student-bar-name {
    font-size: 28rpx;
    font-weight: 600;
    color: var(--color-text-primary);
}

/* 总评 */
.overall-comment {
    font-size: 28rpx;
    line-height: 1.8;
    color: var(--color-text-secondary);
}

/* 教师最终得分 */
.final-score-row { display: flex; gap: var(--space-sm); align-items: center; }

.final-score-input {
    flex: 1;
    height: 80rpx;
    background: var(--color-bg);
    border-radius: var(--radius-md);
    padding: 0 var(--space-lg);
    font-size: 30rpx;
    box-sizing: border-box;
    border: 1rpx solid var(--color-border);
}

.save-score-btn {
    min-width: 180rpx;
    height: 80rpx;
    line-height: 80rpx;
    background: var(--color-success-gradient);
    color: #FFF;
    border-radius: var(--radius-full);
    font-size: 26rpx;
    font-weight: 600;
    border: none;
    padding: 0 24rpx;
    box-shadow: 0 8rpx 18rpx rgba(5, 150, 105, 0.18);
}

.save-score-btn[disabled] { opacity: 0.5; }

.final-score-hint {
    font-size: 22rpx;
    color: var(--color-text-tertiary);
    margin-top: 10rpx;
    display: block;
}

.final-score-value {
    font-size: 26rpx;
    color: var(--color-success);
    margin-top: 8rpx;
    display: block;
    font-weight: 600;
}
.review-note-input { box-sizing: border-box; width: 100%; min-height: 76rpx; margin-top: 16rpx; padding: 14rpx 16rpx; border: 1rpx solid var(--color-border); border-radius: var(--radius-md); background: #F8FAFC; color: var(--color-text-primary); font-size: 24rpx; line-height: 1.55; }

/* 评分维度 */
.dimension-item { margin-bottom: 28rpx; }
.dimension-item:last-child { margin-bottom: 0; }

.dim-header {
    display: flex;
    justify-content: space-between;
    margin-bottom: 10rpx;
}

.dim-name { font-size: 28rpx; font-weight: 600; color: var(--color-text-primary); }
.dim-score { font-size: 28rpx; color: var(--color-primary); font-weight: 700; }

.progress-bar {
    height: 10rpx;
    background: var(--color-divider);
    border-radius: 5rpx;
    margin-bottom: 8rpx;
    overflow: hidden;
}

.progress-fill { height: 100%; border-radius: 5rpx; transition: width 0.6s ease-out; }

.fill-0 { background: var(--color-primary); }
.fill-1 { background: var(--color-success); }
.fill-2 { background: var(--color-warning); }
.fill-3 { background: #8B5CF6; }

.dim-comment {
    font-size: 24rpx;
    color: var(--color-text-tertiary);
    line-height: 1.6;
}

/* 语法错误 */
.grammar-item {
    margin-bottom: 18rpx;
    padding: 18rpx;
    border: 1rpx solid var(--color-divider);
    border-radius: var(--radius-lg);
    background: #FFFFFF;
}
.grammar-item:last-child { margin-bottom: 0; }

.grammar-header { margin-bottom: 10rpx; }

.grammar-index {
    font-size: 22rpx;
    color: #FFF;
    background: var(--color-danger);
    padding: 4rpx 16rpx;
    border-radius: var(--radius-sm);
    font-weight: 600;
}

.grammar-row {
    display: flex;
    align-items: flex-start;
    margin-bottom: 6rpx;
    gap: var(--space-sm);
}

.grammar-label {
    font-size: 22rpx;
    color: var(--color-text-tertiary);
    min-width: 64rpx;
    flex-shrink: 0;
    padding-top: 2rpx;
}

.grammar-original {
    font-size: 26rpx;
    color: var(--color-danger);
    text-decoration: line-through;
    font-style: italic;
    line-height: 1.6;
    word-break: break-all;
    flex: 1;
}

.grammar-correction {
    font-size: 26rpx;
    color: var(--color-success);
    font-weight: 600;
    line-height: 1.6;
    word-break: break-all;
    flex: 1;
}

.grammar-explanation {
    font-size: 24rpx;
    color: var(--color-text-secondary);
    line-height: 1.7;
    background: var(--color-bg);
    padding: 12rpx 16rpx;
    border-radius: var(--radius-sm);
    display: block;
    margin-top: 10rpx;
}

/* 作文原图 */
.annotated-image-wrap {
    width: 100%;
    margin-top: var(--space-sm);
}

.image-stage {
    position: relative;
    width: 100%;
}

.essay-image {
    width: 100%;
    border-radius: var(--radius-lg);
    display: block;
    box-shadow: var(--shadow-sm);
    border: 1rpx solid var(--color-border);
}

.annotation-layer {
    position: absolute;
    left: 0;
    top: 0;
    width: 100%;
    height: 100%;
    pointer-events: none;
}

.image-annotation {
    position: absolute;
    min-width: 56rpx;
    min-height: 42rpx;
    border: 4rpx solid #EF4444;
    border-radius: 10rpx;
    background: rgba(239, 68, 68, 0.04);
    box-sizing: border-box;
    pointer-events: auto;
}
.image-annotation.selected { box-shadow: 0 0 0 6rpx rgba(239, 68, 68, 0.18); }

.image-annotation.ann-pin {
    width: 46rpx !important;
    height: 46rpx !important;
    min-width: 46rpx;
    min-height: 46rpx;
    border-radius: 50%;
    border: 4rpx solid #FFFFFF;
    background: #EF4444;
    box-shadow: 0 4rpx 14rpx rgba(239, 68, 68, 0.42);
    transform: translate(-50%, -50%);
}

.image-annotation.ann-warning {
    border-color: #F59E0B;
    background: rgba(245, 158, 11, 0.12);
}

.image-annotation.ann-info,
.image-annotation.ann-note {
    border-color: #2563EB;
    background: rgba(37, 99, 235, 0.1);
}

.annotation-index {
    position: absolute;
    left: -16rpx;
    top: -20rpx;
    width: 36rpx;
    height: 36rpx;
    line-height: 36rpx;
    text-align: center;
    border-radius: 18rpx;
    background: #EF4444;
    color: #FFF;
    font-size: 20rpx;
    font-weight: 800;
}
.image-annotation.ann-pin .annotation-index {
    position: static;
    display: block;
    width: 100%;
    height: 100%;
    line-height: 38rpx;
    border-radius: 50%;
    background: transparent;
    font-size: 22rpx;
}

.annotation-list {
    margin-top: 18rpx;
    padding: 18rpx 20rpx;
    border-radius: var(--radius-lg);
    background: #F8FAFC;
    border: 1rpx solid var(--color-border);
}
.annotation-legend { display: block; margin: -4rpx 0 16rpx; color: var(--color-text-tertiary); font-size: 22rpx; line-height: 1.5; }

.annotation-note {
    display: flex;
    gap: 14rpx;
    align-items: flex-start;
    margin-bottom: 12rpx;
}
.annotation-note.selected { padding: 10rpx; margin-left: -10rpx; margin-right: -10rpx; border-radius: var(--radius-sm); background: #FEF2F2; }

.annotation-note:last-child { margin-bottom: 0; }

.annotation-note-index {
    flex-shrink: 0;
    min-width: 48rpx;
    height: 36rpx;
    line-height: 36rpx;
    border-radius: 18rpx;
    text-align: center;
    background: #EF4444;
    color: #FFF;
    font-size: 20rpx;
    font-weight: 800;
}

.annotation-note-text, .annotation-note-input {
    flex: 1;
    color: var(--color-text-secondary);
    font-size: 24rpx;
    line-height: 1.55;
}
.annotation-note-input { box-sizing: border-box; width: 100%; min-height: 58rpx; padding: 8rpx 10rpx; border: 1rpx solid var(--color-border); border-radius: var(--radius-sm); background: #FFFFFF; }
.annotation-edit-hint { display: block; margin-bottom: 14rpx; color: var(--color-text-tertiary); font-size: 22rpx; line-height: 1.5; }

.image-fail-text {
    font-size: 26rpx;
    color: var(--color-text-tertiary);
    text-align: center;
    display: block;
    padding: 40rpx 0;
}

/* 底部操作栏 */
.bottom-bar {
    position: fixed;
    bottom: 0;
    left: 0;
    right: 0;
    padding: 16rpx var(--space-lg);
    padding-bottom: calc(16rpx + env(safe-area-inset-bottom));
    background: rgba(255, 255, 255, 0.96);
    display: flex;
    gap: var(--space-sm);
    box-shadow: 0 -10rpx 32rpx rgba(15, 23, 42, 0.08);
    border-top: 1rpx solid rgba(221, 229, 240, 0.9);
}

.bottom-btn {
    flex: 1;
    height: 80rpx;
    line-height: 80rpx;
    border-radius: var(--radius-lg);
    font-size: 25rpx;
    font-weight: 800;
    border: none;
    text-align: center;
    transition: all 0.2s;
}

.bottom-btn:active { transform: scale(0.97); }

.btn-retry {
    background: var(--color-warning-light);
    color: #B45309;
}

.btn-back {
    background: var(--color-divider);
    color: var(--color-text-secondary);
}

.btn-records {
    background: var(--color-primary);
    color: #FFF;
}

/* 错误批注样式 */
.error-type-tag {
    font-size: 20rpx;
    padding: 4rpx 12rpx;
    border-radius: var(--radius-full);
    font-weight: 600;
}
.err-grammar { background: #FEE2E2; color: #DC2626; }
.err-logic { background: #FEF3C7; color: #D97706; }
.err-calc { background: #DBEAFE; color: #2563EB; }
.err-other { background: var(--color-divider); color: var(--color-text-secondary); }

.error-position {
    font-size: 22rpx;
    color: var(--color-text-tertiary);
    margin-left: auto;
}

.grammar-row {
    display: flex;
    align-items: flex-start;
    gap: 12rpx;
    margin-bottom: 8rpx;
}
.grammar-label { font-size: 26rpx; flex-shrink: 0; width: 48rpx; text-align: center; }
.grammar-original-wrap {
    flex: 1;
    background: #FEF2F2;
    border-radius: var(--radius-sm);
    padding: 12rpx 16rpx;
    border-left: 4rpx solid var(--color-danger);
}
.grammar-original { font-size: 26rpx; color: #991B1B; text-decoration: line-through; }
.grammar-correction-wrap {
    flex: 1;
    background: #ECFDF5;
    border-radius: var(--radius-sm);
    padding: 12rpx 16rpx;
    border-left: 4rpx solid var(--color-success);
}
.grammar-correction { font-size: 26rpx; color: #065F46; font-weight: 600; }

.kp-chip {
    display: inline-block;
    font-size: 22rpx;
    color: var(--color-primary);
    background: var(--color-primary-light);
    padding: 6rpx 16rpx;
    border-radius: var(--radius-full);
    margin-top: 10rpx;
    font-weight: 500;
}

/* 知识点巩固 */
.kp-card { border: 2rpx solid var(--color-primary-soft); }
.kp-desc { font-size: 24rpx; color: var(--color-text-tertiary); margin-bottom: var(--space-lg); display: block; }
.kp-item {
    background: var(--color-bg);
    border-radius: var(--radius-md);
    padding: 20rpx 24rpx;
    margin-bottom: var(--space-sm);
    border: 1rpx solid var(--color-border);
    transition: all 0.18s var(--ease-out);
}
.kp-item:active { transform: scale(0.98); }
.kp-header { display: flex; align-items: center; justify-content: space-between; }
.kp-topic { font-size: 28rpx; font-weight: 700; color: var(--color-text-primary); }
.kp-arrow { font-size: 24rpx; color: var(--color-text-tertiary); }
.kp-body { margin-top: 16rpx; }
.kp-text { font-size: 26rpx; color: var(--color-text-secondary); line-height: 1.7; display: block; }
.kp-suggestion {
    background: var(--color-success-light);
    border-radius: var(--radius-md);
    padding: 16rpx 20rpx;
    margin-top: 16rpx;
}
.kp-suggest-label { font-size: 24rpx; color: var(--color-success); font-weight: 700; display: block; margin-bottom: 6rpx; }
.kp-suggest-text { font-size: 26rpx; color: #065F46; line-height: 1.6; }
</style>
