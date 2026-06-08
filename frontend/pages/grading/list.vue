<template>
    <view class="page-container">
        <view class="header-bar">
            <text class="section-title">{{ assignmentTitle }}</text>
        </view>

        <view v-if="essays.length === 0" class="empty-state">
            <text class="empty-state-icon">—</text>
            <text class="empty-state-text">暂无批改记录</text>
            <button class="btn-primary" style="width: 320rpx; margin-top: 32rpx;" @click="goCapture">开始批改</button>
        </view>

        <view v-for="item in essays" :key="item.id" class="card essay-card" @click="goResult(item)">
            <view class="essay-left">
                <view class="score-circle" :class="getScoreClass(item)">
                    <text v-if="item.status === 'GRADED' || item.status === 'TEACHER_REVIEWED'" class="score-val">{{ item.aiScore }}</text>
                    <view v-else class="score-pending"></view>
                </view>
            </view>
            <view class="essay-mid">
                <text class="student-name">{{ item.studentName || '学生 #' + item.studentId }}</text>
                <text class="grading-date">{{ item.createdAt }}</text>
                <text v-if="item.finalScore !== null && item.finalScore !== undefined" class="final-score">
                    最终分：{{ item.finalScore }}
                </text>
                <text v-if="isProcessing(item)" class="grading-status">{{ statusLabel(item.status) }}</text>
            </view>
            <view class="essay-right">
                <text class="badge" :class="getStatusBadge(item)">
                    {{ statusLabel(item.status) }}
                </text>
                <text class="arrow">›</text>
            </view>
        </view>
    </view>
</template>

<script>
import { getEssaysByAssignment } from '@/api/index.js'

export default {
    data() {
        return {
            assignmentId: '',
            assignmentTitle: '',
            essays: [],
            refreshTimer: null
        }
    },
    onLoad(options) {
        this.assignmentId = options.assignmentId
        this.assignmentTitle = decodeURIComponent(options.title || '')
    },
    onShow() {
        this.loadData()
        this.startAutoRefresh()
    },
    onHide() {
        this.stopAutoRefresh()
    },
    onUnload() {
        this.stopAutoRefresh()
    },
    methods: {
        startAutoRefresh() {
            this.stopAutoRefresh()
            this.refreshTimer = setInterval(() => {
                const hasProcessing = this.essays.some(e => this.isProcessing(e))
                if (hasProcessing) this.loadData()
            }, 3000)
        },
        stopAutoRefresh() {
            if (this.refreshTimer) {
                clearInterval(this.refreshTimer)
                this.refreshTimer = null
            }
        },
        isProcessing(item) {
            const s = item.status || ''
            return s !== 'GRADED' && s !== 'TEACHER_REVIEWED' && s !== 'FAILED'
        },
        statusLabel(status) {
            const map = {
                UPLOADED: '已上传',
                OCR_PROCESSING: 'OCR识别中',
                OCR_DONE: '识别完成',
                AI_PROCESSING: 'AI评分中',
                GRADED: '已批改',
                TEACHER_REVIEWED: '已确认',
                FAILED: '失败'
            }
            return map[status] || '处理中'
        },
        getStatusBadge(item) {
            const s = item.status || ''
            if (s === 'GRADED' || s === 'TEACHER_REVIEWED') return 'badge-success'
            if (s === 'FAILED') return 'badge-danger'
            return 'badge-warning'
        },
        async loadData() {
            try {
                const res = await getEssaysByAssignment(this.assignmentId)
                if (res.code === 200) {
                    this.essays = res.data || []
                }
            } catch(e) {}
        },
        getScoreClass(item) {
            const s = item.status || ''
            if (s !== 'GRADED' && s !== 'TEACHER_REVIEWED') return 'score-pending-state'
            const score = Number(item.aiScore || 0)
            const maxScore = Number(item.totalScore || item.assignmentTotalScore || 15)
            const ratio = maxScore > 0 ? score / maxScore : 0
            if (ratio >= 0.9) return 'score-a'
            if (ratio >= 0.75) return 'score-b'
            if (ratio >= 0.6) return 'score-c'
            return 'score-d'
        },
        goResult(item) {
            uni.navigateTo({
                url: '/pages/grading/result?essayId=' + item.id + '&studentName=' + encodeURIComponent(item.studentName || '学生')
            })
        },
        goCapture() {
            uni.navigateTo({
                url: '/pages/grading/capture?assignmentId=' + this.assignmentId + '&title=' + encodeURIComponent(this.assignmentTitle)
            })
        }
    }
}
</script>

<style scoped>
.essay-card {
    padding: 24rpx;
    display: flex;
    align-items: center;
    transition: all 0.2s var(--ease-out);
}
.essay-card:active { transform: scale(0.985); background: var(--color-primary-light); }

.essay-left {
    margin-right: 20rpx;
}

.score-circle {
    width: 88rpx;
    height: 88rpx;
    border-radius: 44rpx;
    display: flex;
    align-items: center;
    justify-content: center;
}

.score-a { background: var(--color-success-light); }
.score-b { background: var(--color-primary-light); }
.score-c { background: var(--color-warning-light); }
.score-d { background: var(--color-danger-light); }
.score-d .score-val { color: var(--color-danger); }
.score-pending-state { background: var(--color-divider); }

.score-pending {
    width: 36rpx;
    height: 36rpx;
    border: 4rpx solid var(--color-primary-soft);
    border-top-color: var(--color-primary);
    border-radius: 50%;
    animation: spin 0.8s linear infinite;
}

@keyframes spin {
    to { transform: rotate(360deg); }
}

.grading-status {
    font-size: 22rpx;
    color: var(--color-primary);
    font-weight: 500;
    margin-top: 4rpx;
    display: block;
}

@keyframes spin {
    to { transform: rotate(360deg); }
}

.score-val {
    font-size: 30rpx;
    font-weight: 700;
}

.score-a .score-val { color: var(--color-success); }
.score-b .score-val { color: var(--color-primary); }
.score-c .score-val { color: var(--color-warning); }
.score-d .score-val { color: var(--color-danger); }

.essay-mid { flex: 1; }

.student-name {
    font-size: 28rpx;
    font-weight: 600;
    color: var(--color-text-primary);
    display: block;
}

.grading-date {
    font-size: 24rpx;
    color: var(--color-text-tertiary);
    margin-top: 4rpx;
    display: block;
}

.final-score {
    font-size: 24rpx;
    color: var(--color-success);
    font-weight: 600;
    margin-top: 4rpx;
    display: block;
}

.essay-right {
    display: flex;
    flex-direction: column;
    align-items: flex-end;
    gap: 8rpx;
}

.arrow {
    color: var(--color-text-tertiary);
    font-size: 28rpx;
}

.empty-state-text {
    color: var(--color-text-tertiary);
    font-size: 28rpx;
}
</style>
