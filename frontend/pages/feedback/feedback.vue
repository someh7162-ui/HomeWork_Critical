<template>
    <view class="feedback-page">
        <view class="hero">
            <view>
                <text class="eyebrow">MVP Agent 反馈闭环</text>
                <text class="title">体验反馈区</text>
                <text class="desc">把真实使用感受留下来，后续会变成我们的产品迭代依据。</text>
            </view>
            <view class="score-badge">
                <text class="score-value">{{ summary.averageRating || 0 }}</text>
                <text class="score-label">平均评分</text>
            </view>
        </view>

        <view class="summary-row">
            <view class="summary-card">
                <text class="summary-num">{{ summary.count || 0 }}</text>
                <text class="summary-label">反馈条数</text>
            </view>
            <view class="summary-card wide">
                <text class="summary-label">高频场景</text>
                <view class="scene-tags">
                    <text v-for="item in topScenes" :key="item.name" class="scene-chip">{{ item.name }} {{ item.count }}</text>
                    <text v-if="!topScenes.length" class="scene-chip muted">等待第一条反馈</text>
                </view>
            </view>
        </view>

        <view class="form-card">
            <text class="section-title">写下你的使用体验</text>
            <view class="rating-row">
                <text class="rating-label">评分</text>
                <view class="stars">
                    <text
                        v-for="n in 5"
                        :key="n"
                        :class="['star', n <= form.rating ? 'active' : '']"
                        @click="form.rating = n"
                    >★</text>
                </view>
            </view>

            <view class="scene-grid">
                <text
                    v-for="item in scenes"
                    :key="item"
                    :class="['scene-option', form.scene === item ? 'active' : '']"
                    @click="form.scene = item"
                >{{ item }}</text>
            </view>

            <textarea
                class="feedback-input"
                v-model="form.content"
                maxlength="500"
                placeholder="例如：学生上传作业是否方便、批改结果是否清楚、红框批注是否好理解、哪里还需要改进..."
            />
            <view class="form-foot">
                <text class="count">{{ form.content.length }}/500</text>
                <button class="submit-btn" :disabled="submitting || form.content.trim().length < 4" @click="handleSubmit">
                    {{ submitting ? '提交中...' : '提交反馈' }}
                </button>
            </view>
        </view>

        <view class="list-head">
            <text class="section-title">大家的反馈</text>
            <button class="refresh-btn" @click="loadData">刷新</button>
        </view>

        <view v-if="feedbacks.length === 0" class="empty-card">
            <text class="empty-title">还没有反馈</text>
            <text class="empty-desc">可以先写一条演示反馈，用来展示项目的用户体验收集能力。</text>
        </view>

        <view v-for="item in feedbacks" :key="item.id" class="feedback-card">
            <view class="feedback-top">
                <view>
                    <text class="user-name">{{ item.userName || '匿名用户' }}</text>
                    <text class="user-meta">{{ roleText(item) }} {{ item.className || '' }}</text>
                </view>
                <view class="rating-mini">
                    <text v-for="n in 5" :key="n" :class="['mini-star', n <= item.rating ? 'active' : '']">★</text>
                </view>
            </view>
            <text class="scene-label">{{ item.scene || '整体体验' }}</text>
            <text class="content">{{ item.content }}</text>
            <text class="time">{{ formatTime(item.createdAt) }}</text>
        </view>
    </view>
</template>

<script>
import {
    getFeedbackList,
    getFeedbackSummary,
    getStudentFeedbackList,
    getStudentFeedbackSummary,
    getUserRole,
    submitFeedback,
    submitStudentFeedback
} from '@/api/index.js'

export default {
    data() {
        return {
            role: getUserRole(),
            form: {
                rating: 5,
                scene: '整体体验',
                content: ''
            },
            scenes: ['整体体验', '作业上传', 'AI批改', '红框批注', '统计看板', '改进建议'],
            feedbacks: [],
            summary: {
                count: 0,
                averageRating: 0,
                sceneCounts: {}
            },
            submitting: false
        }
    },
    computed: {
        isStudent() {
            return this.role === 'STUDENT'
        },
        topScenes() {
            const counts = this.summary.sceneCounts || {}
            return Object.keys(counts)
                .map(name => ({ name, count: counts[name] }))
                .sort((a, b) => b.count - a.count)
                .slice(0, 3)
        }
    },
    onShow() {
        this.role = getUserRole()
        this.loadData()
    },
    methods: {
        async loadData() {
            try {
                const [listRes, summaryRes] = await Promise.all([
                    this.isStudent ? getStudentFeedbackList() : getFeedbackList(),
                    this.isStudent ? getStudentFeedbackSummary() : getFeedbackSummary()
                ])
                if (listRes.code === 200) this.feedbacks = listRes.data || []
                if (summaryRes.code === 200) this.summary = summaryRes.data || this.summary
            } catch (e) {}
        },
        async handleSubmit() {
            const content = this.form.content.trim()
            if (content.length < 4) {
                uni.showToast({ title: '请至少填写4个字', icon: 'none' })
                return
            }
            this.submitting = true
            try {
                const payload = {
                    rating: this.form.rating,
                    scene: this.form.scene,
                    content
                }
                const res = this.isStudent ? await submitStudentFeedback(payload) : await submitFeedback(payload)
                if (res.code === 200) {
                    uni.showToast({ title: '反馈已提交', icon: 'success' })
                    this.form.content = ''
                    this.form.rating = 5
                    this.form.scene = '整体体验'
                    this.loadData()
                } else {
                    uni.showToast({ title: res.message || '提交失败', icon: 'none' })
                }
            } catch (e) {
                uni.showToast({ title: '提交失败', icon: 'none' })
            }
            this.submitting = false
        },
        roleText(item) {
            return item.userRole === 'STUDENT' ? '学生' : '教师'
        },
        formatTime(value) {
            if (!value) return ''
            return String(value).replace('T', ' ').slice(0, 16)
        }
    }
}
</script>

<style scoped>
.feedback-page {
    min-height: 100vh;
    padding: 24rpx;
    background: #F5F7FB;
}

.hero {
    display: flex;
    justify-content: space-between;
    gap: 24rpx;
    padding: 32rpx;
    border-radius: 20rpx;
    background: linear-gradient(135deg, #2457D6, #15A37A);
    color: #FFF;
    box-shadow: 0 16rpx 36rpx rgba(36, 87, 214, 0.22);
}

.eyebrow {
    display: block;
    font-size: 22rpx;
    color: rgba(255,255,255,0.78);
    font-weight: 700;
}

.title {
    display: block;
    margin-top: 8rpx;
    font-size: 44rpx;
    font-weight: 900;
    line-height: 1.25;
}

.desc {
    display: block;
    margin-top: 12rpx;
    font-size: 25rpx;
    color: rgba(255,255,255,0.82);
    line-height: 1.55;
}

.score-badge {
    width: 148rpx;
    height: 148rpx;
    border-radius: 18rpx;
    background: rgba(255,255,255,0.16);
    border: 1rpx solid rgba(255,255,255,0.28);
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
}

.score-value {
    font-size: 44rpx;
    font-weight: 900;
}

.score-label {
    font-size: 22rpx;
    color: rgba(255,255,255,0.78);
}

.summary-row {
    display: grid;
    grid-template-columns: 180rpx 1fr;
    gap: 16rpx;
    margin: 20rpx 0;
}

.summary-card, .form-card, .feedback-card, .empty-card {
    background: #FFF;
    border: 1rpx solid #E5EAF2;
    border-radius: 16rpx;
    box-shadow: 0 8rpx 24rpx rgba(15, 23, 42, 0.06);
}

.summary-card {
    padding: 22rpx;
}

.summary-num {
    display: block;
    font-size: 42rpx;
    font-weight: 900;
    color: #2457D6;
}

.summary-label {
    display: block;
    font-size: 23rpx;
    color: #64748B;
}

.scene-tags {
    display: flex;
    flex-wrap: wrap;
    gap: 10rpx;
    margin-top: 12rpx;
}

.scene-chip {
    padding: 8rpx 14rpx;
    border-radius: 999rpx;
    background: #E9F8F2;
    color: #087A5A;
    font-size: 22rpx;
    font-weight: 700;
}

.scene-chip.muted {
    background: #F1F5F9;
    color: #64748B;
}

.form-card {
    padding: 28rpx;
}

.section-title {
    font-size: 32rpx;
    font-weight: 900;
    color: #14213D;
}

.rating-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-top: 24rpx;
}

.rating-label {
    font-size: 26rpx;
    color: #334155;
    font-weight: 700;
}

.stars {
    display: flex;
    gap: 10rpx;
}

.star {
    font-size: 46rpx;
    color: #CBD5E1;
}

.star.active {
    color: #F59E0B;
}

.scene-grid {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 12rpx;
    margin-top: 24rpx;
}

.scene-option {
    height: 64rpx;
    line-height: 64rpx;
    text-align: center;
    border-radius: 999rpx;
    background: #F1F5F9;
    color: #475569;
    font-size: 24rpx;
    font-weight: 700;
}

.scene-option.active {
    background: #2457D6;
    color: #FFF;
}

.feedback-input {
    box-sizing: border-box;
    width: 100%;
    min-height: 210rpx;
    margin-top: 24rpx;
    padding: 22rpx;
    border-radius: 16rpx;
    background: #F8FAFC;
    border: 1rpx solid #E2E8F0;
    color: #14213D;
    font-size: 27rpx;
    line-height: 1.6;
}

.form-foot {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-top: 18rpx;
}

.count {
    color: #94A3B8;
    font-size: 22rpx;
}

.submit-btn {
    width: 210rpx;
    height: 72rpx;
    line-height: 72rpx;
    border-radius: 999rpx;
    border: none;
    background: #15A37A;
    color: #FFF;
    font-size: 26rpx;
    font-weight: 800;
}

.submit-btn[disabled] {
    background: #CBD5E1;
}

.list-head {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin: 30rpx 0 16rpx;
}

.refresh-btn {
    width: 120rpx;
    height: 56rpx;
    line-height: 56rpx;
    border-radius: 999rpx;
    background: #EAF1FF;
    color: #2457D6;
    border: none;
    font-size: 24rpx;
    font-weight: 800;
}

.empty-card {
    padding: 36rpx;
    text-align: center;
}

.empty-title {
    display: block;
    font-size: 30rpx;
    color: #14213D;
    font-weight: 900;
}

.empty-desc {
    display: block;
    margin-top: 10rpx;
    color: #64748B;
    font-size: 24rpx;
    line-height: 1.6;
}

.feedback-card {
    padding: 24rpx;
    margin-bottom: 16rpx;
}

.feedback-top {
    display: flex;
    justify-content: space-between;
    gap: 16rpx;
}

.user-name {
    display: block;
    font-size: 28rpx;
    color: #14213D;
    font-weight: 900;
}

.user-meta {
    display: block;
    margin-top: 4rpx;
    font-size: 22rpx;
    color: #64748B;
}

.rating-mini {
    white-space: nowrap;
}

.mini-star {
    font-size: 24rpx;
    color: #CBD5E1;
}

.mini-star.active {
    color: #F59E0B;
}

.scene-label {
    display: inline-block;
    margin-top: 18rpx;
    padding: 6rpx 14rpx;
    border-radius: 999rpx;
    background: #EAF1FF;
    color: #2457D6;
    font-size: 22rpx;
    font-weight: 800;
}

.content {
    display: block;
    margin-top: 14rpx;
    color: #334155;
    font-size: 27rpx;
    line-height: 1.65;
}

.time {
    display: block;
    margin-top: 14rpx;
    color: #94A3B8;
    font-size: 22rpx;
}
</style>
