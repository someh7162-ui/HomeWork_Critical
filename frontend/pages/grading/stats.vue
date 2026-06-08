<template>
    <view class="page-container">
        <view class="header-bar">
            <text class="section-title">{{ assignmentTitle }}</text>
            <text class="section-sub">班级统计</text>
        </view>

        <view v-if="loading" class="loading-hint">加载中...</view>

        <view v-else class="stats-content">
            <view class="workflow-panel">
                <view class="workflow-head">
                    <view>
                        <text class="workflow-title">批改闭环</text>
                        <text class="workflow-sub">提交、批改、复核状态一屏掌握</text>
                    </view>
                    <view class="score-summary">
                        <text>平均分</text>
                        <strong>{{ stats.averageScore || 0 }}</strong>
                    </view>
                </view>
                <view class="workflow-grid">
                    <view class="flow-card submitted">
                        <text class="flow-label">已提交</text>
                        <text class="flow-num">{{ stats.submittedCount || 0 }}</text>
                    </view>
                    <view class="flow-card processing">
                        <text class="flow-label">批改中</text>
                        <text class="flow-num">{{ stats.processingCount || 0 }}</text>
                    </view>
                    <view class="flow-card review">
                        <text class="flow-label">待复核</text>
                        <text class="flow-num">{{ stats.reviewRequiredCount || 0 }}</text>
                    </view>
                    <view class="flow-card confirmed">
                        <text class="flow-label">已确认</text>
                        <text class="flow-num">{{ stats.confirmedCount || 0 }}</text>
                    </view>
                    <view class="flow-card unsubmitted">
                        <text class="flow-label">未提交</text>
                        <text class="flow-num">{{ stats.unsubmittedCount || 0 }}</text>
                    </view>
                </view>
                <view v-if="stats.failedCount" class="failed-strip">
                    <text>批改失败 {{ stats.failedCount }} 份，建议重新上传更清晰图片。</text>
                </view>
            </view>

            <view class="chart-section" v-if="distributionKeys.length">
                <text class="chart-title">分数段分布</text>
                <view class="bar-chart">
                    <view v-for="(key, idx) in distributionKeys" :key="idx" class="bar-row">
                        <text class="bar-label">{{ key }}</text>
                        <view class="bar-track"><view class="bar-fill" :style="barStyle(key)"></view></view>
                        <text class="bar-count">{{ stats.distribution[key] }}</text>
                    </view>
                </view>
            </view>

            <view class="knowledge-section" v-if="stats.knowledgePoints && stats.knowledgePoints.length">
                <text class="chart-title">知识点薄弱项</text>
                <view v-for="point in stats.knowledgePoints" :key="point.name" class="knowledge-row">
                    <view>
                        <text class="knowledge-name">{{ point.name }}</text>
                        <text class="knowledge-suggestion">{{ point.suggestion || '建议安排针对性讲评。' }}</text>
                    </view>
                    <text class="knowledge-count">错 {{ point.wrongCount }} / {{ point.totalCount }}</text>
                </view>
            </view>

            <view class="score-list" v-if="stats.scores && stats.scores.length">
                <text class="chart-title">学生成绩明细</text>
                <view v-for="(item, idx) in stats.scores" :key="idx" class="score-row">
                    <text class="score-rank">{{ idx + 1 }}</text>
                    <text class="score-name">{{ item.studentName || '未知' }}</text>
                    <view class="score-bar-wrap"><view class="score-bar" :style="{ width: scorePercent(item.score) + '%' }"></view></view>
                    <text class="score-val">{{ item.score }}</text>
                </view>
            </view>
        </view>

        <view v-if="!loading && stats.totalEssays === 0" class="empty-state-card">
            <text>暂无已完成批改记录，提交和批改中数据会先显示在上方。</text>
        </view>
    </view>
</template>

<script>
import { getAssignmentStats } from '@/api/index.js'

export default {
    data() {
        return {
            assignmentId: '',
            assignmentTitle: '',
            loading: true,
            stats: { totalEssays: 0, submittedCount: 0, processingCount: 0, reviewRequiredCount: 0, confirmedCount: 0, unsubmittedCount: 0, failedCount: 0, averageScore: 0, highestScore: 0, lowestScore: 0, distribution: {}, scores: [], knowledgePoints: [] }
        }
    },
    computed: {
        distributionKeys() {
            return Object.keys(this.stats.distribution || {})
        },
        maxDist() {
            const vals = Object.values(this.stats.distribution || {})
            return vals.length ? Math.max(...vals, 1) : 1
        }
    },
    onLoad(options) {
        this.assignmentId = options.assignmentId
        this.assignmentTitle = decodeURIComponent(options.title || '')
        this.loadStats()
    },
    methods: {
        async loadStats() {
            try {
                const res = await getAssignmentStats(this.assignmentId)
                if (res.code === 200) this.stats = res.data
            } catch(e) {}
            this.loading = false
        },
        barStyle(key) {
            const count = this.stats.distribution[key] || 0
            return { width: (count / this.maxDist * 100) + '%' }
        },
        scorePercent(score) {
            const max = this.stats.totalScore || 100
            return Math.min((Number(score || 0) / max) * 100, 100)
        }
    }
}
</script>

<style scoped>
.loading-hint { text-align:center; color:var(--color-text-tertiary); padding:100rpx 0; font-size:28rpx; }
.workflow-panel { background: var(--color-surface); border-radius: var(--radius-lg); padding: 24rpx; margin-bottom: var(--space-xl); border: 1rpx solid var(--color-border); box-shadow: var(--shadow-sm); }
.workflow-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 18rpx; margin-bottom: 20rpx; }
.workflow-title { display: block; color: var(--color-text-primary); font-size: 32rpx; font-weight: 800; }
.workflow-sub { display: block; margin-top: 5rpx; color: var(--color-text-tertiary); font-size: 22rpx; }
.score-summary { flex-shrink: 0; min-width: 142rpx; padding: 12rpx 16rpx; border-radius: var(--radius-md); background: var(--color-primary-light); text-align: center; }
.score-summary text { display: block; color: var(--color-primary); font-size: 21rpx; font-weight: 700; }
.score-summary strong { display: block; color: var(--color-primary); font-size: 38rpx; line-height: 1.1; }
.workflow-grid { display: grid; grid-template-columns: repeat(5, 1fr); gap: 10rpx; }
.flow-card { min-height: 112rpx; border-radius: var(--radius-md); padding: 14rpx 8rpx; text-align: center; box-sizing: border-box; }
.flow-label { display: block; font-size: 21rpx; color: var(--color-text-tertiary); font-weight: 700; white-space: nowrap; }
.flow-num { display: block; margin-top: 8rpx; font-size: 38rpx; font-weight: 900; line-height: 1.1; color: var(--color-text-primary); }
.flow-card.submitted { background: #EFF6FF; }
.flow-card.submitted .flow-num { color: var(--color-primary); }
.flow-card.processing { background: #F0F9FF; }
.flow-card.processing .flow-num { color: #0284C7; }
.flow-card.review { background: #FFFBEB; }
.flow-card.review .flow-num { color: #B45309; }
.flow-card.confirmed { background: #ECFDF5; }
.flow-card.confirmed .flow-num { color: #059669; }
.flow-card.unsubmitted { background: #F8FAFC; }
.flow-card.unsubmitted .flow-num { color: var(--color-text-secondary); }
.failed-strip { margin-top: 14rpx; padding: 12rpx 16rpx; border-radius: var(--radius-md); background: var(--color-danger-light); }
.failed-strip text { color: var(--color-danger); font-size: 22rpx; line-height: 1.5; }
.chart-title { font-size: 28rpx; font-weight: 700; color: var(--color-text-primary); margin-bottom: var(--space-md); display: block; }
.chart-section, .knowledge-section { margin-bottom: var(--space-xl); }
.bar-chart, .score-list, .knowledge-section { background: var(--color-surface); border-radius: var(--radius-md); padding: 24rpx; }
.bar-row { display: flex; align-items: center; margin-bottom: 16rpx; }
.bar-label { width: 130rpx; font-size: 24rpx; color: var(--color-text-secondary); font-weight: 500; }
.bar-track { flex: 1; height: 28rpx; background: var(--color-divider); border-radius: 14rpx; margin: 0 16rpx; overflow: hidden; }
.bar-fill { height: 100%; border-radius: 14rpx; background: var(--color-primary-gradient); transition: width 0.8s var(--ease-out); }
.bar-count { font-size: 24rpx; color: var(--color-text-tertiary); font-weight: 600; width: 50rpx; text-align:right; }
.knowledge-row { display: flex; justify-content: space-between; gap: 18rpx; padding: 18rpx 0; border-bottom: 1rpx solid var(--color-divider); }
.knowledge-row:last-child { border-bottom: none; }
.knowledge-name { display: block; font-size: 28rpx; font-weight: 800; color: var(--color-text-primary); }
.knowledge-suggestion { display: block; font-size: 24rpx; color: var(--color-text-secondary); line-height: 1.6; margin-top: 6rpx; }
.knowledge-count { flex-shrink: 0; height: 48rpx; line-height: 48rpx; border-radius: var(--radius-full); padding: 0 16rpx; background: var(--color-danger-light); color: var(--color-danger); font-size: 22rpx; font-weight: 800; }
.score-row { display: flex; align-items: center; padding: 14rpx 0; border-bottom: 1rpx solid var(--color-divider); }
.score-row:last-child { border-bottom: none; }
.score-rank { width: 48rpx; font-size: 24rpx; color: var(--color-text-tertiary); font-weight: 700; text-align: center; }
.score-name { font-size: 26rpx; font-weight: 600; color: var(--color-text-primary); width: 120rpx; }
.score-bar-wrap { flex: 1; height: 14rpx; background: var(--color-divider); border-radius: 7rpx; margin: 0 16rpx; overflow: hidden; }
.score-bar { height: 100%; border-radius: 7rpx; background: linear-gradient(90deg, var(--color-primary), var(--color-primary-light)); transition: width 0.8s var(--ease-out); }
.score-val { font-size: 26rpx; font-weight: 700; color: var(--color-primary); width: 70rpx; text-align: right; }
</style>
