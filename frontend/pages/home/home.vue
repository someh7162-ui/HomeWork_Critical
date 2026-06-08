<template>
    <view class="page-container">
        <!-- 顶部信息栏 -->
        <view class="top-bar">
            <view class="teacher-avatar">
                <text class="avatar-text">{{ teacherName.charAt(0) }}</text>
            </view>
            <view class="teacher-info" @click="goSettings">
                <text class="teacher-name">{{ teacherName }}</text>
                <text class="teacher-subject">{{ teacherSubject }}教师</text>
            </view>
            <view class="logout-btn" @click="handleLogout">
                <text class="logout-text">退出</text>
            </view>
        </view>

        <!-- 统计卡片 -->
        <view class="stats-row">
            <view class="stat-card stat-assignments">
                <text class="stat-kicker">任务</text>
                <text class="stat-num">{{ assignments.length }}</text>
                <text class="stat-label">作业数</text>
            </view>
            <view class="stat-card stat-graded">
                <text class="stat-kicker">余额</text>
                <text class="stat-num">{{ quotaSummary.remainingTotal || 0 }}</text>
                <text class="stat-label">可用额度</text>
            </view>
        </view>

        <!-- 额度卡片 -->
        <view class="quota-card">
            <view class="quota-card-top">
                <view class="quota-info">
                    <text class="quota-title">批改额度</text>
                    <view class="quota-total-row">
                        <text class="quota-total">{{ quotaSummary.remainingTotal || 0 }}</text>
                        <text class="quota-unit">篇</text>
                    </view>
                </view>
                <view class="app-badge">局域网版</view>
            </view>
            <view class="quota-detail-row">
                <view class="quota-chip free-chip">
                    <view class="chip-dot chip-dot-free"></view>
                    <text>免费可用 {{ quotaSummary.freeRemaining || 0 }}</text>
                </view>
                <view class="quota-chip paid-chip">
                    <view class="chip-dot chip-dot-paid"></view>
                    <text>付费可用 {{ quotaSummary.paidRemaining || 0 }}</text>
                </view>
            </view>
            <view class="quota-actions">
                <button class="redeem-btn" @click="showRedeemPopup">兑换额度</button>
                <button class="buy-btn" @click="openBuyPage">在线购买</button>
            </view>
        </view>

        <!-- 快捷操作 -->
        <view class="quick-actions">
            <view class="action-item" @click="goCreateAssignment">
                <view class="action-icon action-create">+</view>
                <text class="action-text">创建作业</text>
            </view>
            <view class="action-item" @click="goStudentList">
                <view class="action-icon action-student">☰</view>
                <text class="action-text">学生管理</text>
            </view>
            <view class="action-item" @click="goFeedback">
                <view class="action-icon action-feedback">☆</view>
                <text class="action-text">体验反馈</text>
            </view>
        </view>

        <!-- 作业列表 -->
        <view class="section-row">
            <text class="section-title">我的作业</text>
            <text class="section-meta">{{ assignments.length }} 个</text>
        </view>
        <view v-if="assignments.length === 0" class="empty-state-card">
            <text class="empty-state-icon">—</text>
            <text class="empty-state-text" style="display:block;margin-top:16rpx;">暂无作业，点击上方创建</text>
        </view>
        <view v-for="item in assignments" :key="item.id" class="assignment-card card"
              :class="'card-accent-' + getSubjectClass(item)" @click="goCapture(item)">
            <view class="card-top">
                <view class="card-title-row">
                    <text class="card-title">{{ item.title }}</text>
                    <text v-if="item.subject" class="badge-subject" :class="'badge-subject-' + getSubjectClass(item)">{{ item.subject }}</text>
                </view>
                <text class="card-score">满分 {{ item.totalScore }}</text>
            </view>
            <view class="card-mid">
                <text v-if="item.className" class="card-class">{{ item.className }}</text>
                <text class="card-date">{{ item.createdAt }}</text>
            </view>
            <view class="card-bottom">
                <button class="card-btn btn-grade" @click.stop="goCapture(item)">单篇批改</button>
                <button class="card-btn btn-batch" @click.stop="goBatch(item)">批量批改</button>
                <button class="card-btn btn-records" @click.stop="goDetail(item)">详情</button>
            </view>
        </view>

        <view v-if="redeemVisible" class="redeem-mask" @click="redeemVisible = false">
            <view class="redeem-popup" @click.stop>
                <text class="redeem-title">兑换额度</text>
                <text class="redeem-desc">输入卡密，兑换批改额度</text>
                <input class="redeem-input" v-model="redeemKey" placeholder="请输入16位卡密" maxlength="20" />
                <view class="redeem-btns">
                    <button class="redeem-cancel" @click="redeemVisible = false">取消</button>
                    <button class="redeem-confirm" @click="handleRedeem" :disabled="!redeemKey.trim() || redeeming">
                        {{ redeeming ? '兑换中...' : '确认兑换' }}
                    </button>
                </view>
            </view>
        </view>
    </view>
</template>

<script>
import { getAssignments, getQuotaSummary, getTeacherInfo, logout, redeemCard, saveQuotaSummary } from '@/api/index.js'

export default {
    data() {
        return {
            teacherName: '张',
            teacherSubject: '英语',
            assignments: [],
            quotaSummary: {
                freeRemaining: 0,
                paidRemaining: 0,
                remainingTotal: 0
            },
            redeemVisible: false,
            redeemKey: '',
            redeeming: false
        }
    },
    onShow() {
        this.loadData()
        const info = getTeacherInfo()
        if (info) {
            this.teacherName = info.name || '张'
            this.teacherSubject = info.subject || '英语'
            if (info.quotaSummary) {
                this.quotaSummary = info.quotaSummary
            }
        }
        this.loadQuota()
    },
    computed: {
        avatarGradient() {
            const map = {
                '全科': 'linear-gradient(135deg, #059669, #34D399)',
                '语文': 'linear-gradient(135deg, #7C3AED, #A78BFA)',
                '数学': 'linear-gradient(135deg, #D97706, #FBBF24)',
                '英语': 'linear-gradient(135deg, #1D4ED8, #38BDF8)'
            }
            return map[this.teacherSubject] || 'var(--color-primary-gradient)'
        }
    },
    methods: {
        getSubjectClass(item) {
            const s = item.subject || ''
            if (s === '语文') return 'cn'
            if (s === '数学') return 'math'
            return 'en'
        },
        async loadData() {
            try {
                const res = await getAssignments()
                if (res.code === 200) {
                    this.assignments = res.data || []
                }
            } catch(e) {}
        },
        async loadQuota() {
            try {
                const res = await getQuotaSummary()
                if (res.code === 200 && res.data) {
                    this.quotaSummary = res.data
                    saveQuotaSummary(res.data)
                }
            } catch (e) {}
        },
        goCreateAssignment() {
            uni.navigateTo({ url: '/pages/assignment/create', fail: (e) => {
                uni.showToast({ title: '打开失败: ' + JSON.stringify(e), icon: 'none' })
            }})
        },
        goStudentList() {
            uni.switchTab({ url: '/pages/student/list' })
        },
        goFeedback() {
            uni.navigateTo({ url: '/pages/feedback/feedback' })
        },
        goDetail(item) {
            uni.navigateTo({ url: '/pages/assignment/detail?id=' + item.id })
        },
        goCapture(item) {
            uni.navigateTo({ url: '/pages/grading/capture?assignmentId=' + item.id + '&title=' + encodeURIComponent(item.title) })
        },
        goBatch(item) {
            uni.navigateTo({ url: '/pages/grading/batch?assignmentId=' + item.id + '&title=' + encodeURIComponent(item.title) + '&className=' + encodeURIComponent(item.className || '') })
        },
        goRecords(item) {
            uni.navigateTo({ url: '/pages/grading/list?assignmentId=' + item.id + '&title=' + encodeURIComponent(item.title) })
        },
        goSettings() {
            uni.navigateTo({ url: '/pages/user/settings' })
        },
        showRedeemPopup() {
            this.redeemKey = ''
            this.redeemVisible = true
        },
        async handleRedeem() {
            if (!this.redeemKey.trim()) return
            this.redeeming = true
            try {
                const res = await redeemCard(this.redeemKey.trim())
                if (res.code === 200) {
                    uni.showToast({ title: '兑换成功！+' + res.data.addedQuota + '次额度', icon: 'success' })
                    this.redeemVisible = false
                    this.loadQuota()
                } else {
                    uni.showToast({ title: res.message || '兑换失败', icon: 'none' })
                }
            } catch(e) {
                uni.showToast({ title: '兑换失败', icon: 'none' })
            }
            this.redeeming = false
        },
        openBuyPage() {
            uni.showModal({
                title: '购买额度',
                content: '即将打开发卡网购买页面\nhttps://vb3.cn/dgcrg6\n\n购买后获得卡密，在"设置-兑换卡密"中兑换',
                confirmText: '去购买',
                success: (res) => {
                    if (res.confirm) {
                        // #ifdef H5
                        window.open('https://vb3.cn/dgcrg6')
                        // #endif
                        // #ifdef APP-PLUS
                        plus.runtime.openURL('https://vb3.cn/dgcrg6')
                        // #endif
                        // #ifdef MP-WEIXIN
                        uni.setClipboardData({ data: 'https://vb3.cn/dgcrg6' })
                        uni.showToast({ title: '链接已复制到剪贴板', icon: 'success' })
                        // #endif
                    }
                }
            })
        },
        handleLogout() {
            uni.showModal({
                title: '提示',
                content: '确定要退出登录吗？',
                success: (res) => {
                    if (res.confirm) {
                        logout()
                        uni.reLaunch({ url: '/pages/login/login' })
                    }
                }
            })
        }
    }
}
</script>

<style scoped>
.top-bar {
    display: flex;
    align-items: center;
    padding: 8rpx 0 26rpx;
    animation: fadeIn 0.5s var(--ease-out);
}

.teacher-avatar {
    width: 80rpx;
    height: 80rpx;
    border-radius: 24rpx;
    background: var(--color-primary-gradient);
    display: flex;
    align-items: center;
    justify-content: center;
    margin-right: var(--space-md);
    transition: transform 0.22s var(--ease-out);
}
.teacher-avatar:active { transform: scale(0.92); }

.avatar-text { font-size: 36rpx; font-weight: 700; color: #FFF; }
.teacher-info { flex: 1; }

.teacher-name {
    display: block;
    font-size: 34rpx;
    font-weight: 700;
    color: var(--color-text-primary);
}

.teacher-subject {
    font-size: 24rpx;
    color: var(--color-text-secondary);
}

.logout-btn {
    padding: 12rpx 24rpx;
    border-radius: var(--radius-full);
    background: var(--color-surface);
    border: 1rpx solid var(--color-border);
    transition: all 0.22s var(--ease-out);
}
.logout-btn:active { transform: scale(0.94); background: var(--color-border); }
.logout-text { font-size: 26rpx; color: var(--color-text-secondary); }

.stats-row {
    display: flex;
    gap: var(--space-md);
    margin-bottom: var(--space-lg);
}

.stat-card {
    flex: 1;
    border-radius: var(--radius-lg);
    padding: 24rpx;
    position: relative;
    overflow: hidden;
    box-shadow: var(--shadow-sm);
    transition: all 0.25s var(--ease-out);
    animation: fadeInUp 0.4s var(--ease-out) backwards;
}
.stat-card:nth-child(1) { animation-delay: 0.1s; }
.stat-card:nth-child(2) { animation-delay: 0.2s; }
.stat-card:active { transform: scale(0.96); box-shadow: var(--shadow-md); }

.stat-assignments { background: linear-gradient(135deg, #EFF6FF, #DBEAFE); }
.stat-graded { background: linear-gradient(135deg, #ECFDF5, #CCFBF1); }

.stat-kicker {
    display: block;
    font-size: 22rpx;
    color: var(--color-text-secondary);
    font-weight: 700;
    margin-bottom: 10rpx;
}

.stat-num {
    font-size: 56rpx;
    font-weight: 800;
    display: block;
    line-height: 1;
}
.stat-assignments .stat-num { color: var(--color-primary); }
.stat-graded .stat-num { color: var(--color-success); }

.stat-label {
    font-size: 24rpx;
    color: var(--color-text-secondary);
    font-weight: 500;
    margin-top: 8rpx;
    display: block;
}

.quota-card {
    background: linear-gradient(180deg, #FFFFFF, #F8FAFC);
    border-radius: var(--radius-lg);
    padding: 28rpx;
    margin-bottom: var(--space-lg);
    box-shadow: var(--shadow-sm);
    border: 1rpx solid var(--color-border);
    transition: all 0.25s var(--ease-out);
    animation: fadeInUp 0.4s 0.3s var(--ease-out) backwards;
}

.quota-card-top {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
}

.quota-title { display: block; font-size: 26rpx; color: var(--color-text-secondary); font-weight: 500; }

.quota-total-row { display: flex; align-items: baseline; gap: 6rpx; }
.quota-total { font-size: 64rpx; font-weight: 800; color: var(--color-text-primary); line-height: 1; }
.quota-unit { font-size: 28rpx; color: var(--color-text-secondary); font-weight: 500; }

.app-badge {
    margin: 0; min-width: 144rpx; height: 56rpx; line-height: 56rpx;
    background: var(--color-ink); color: #BAE6FD;
    border-radius: var(--radius-full); font-size: 24rpx; font-weight: 600; text-align: center;
}

.quota-detail-row { display: flex; gap: var(--space-sm); margin-top: 20rpx; }

.quota-chip {
    display: flex; align-items: center; gap: 8rpx;
    font-size: 22rpx; padding: 8rpx 18rpx;
    border-radius: var(--radius-full); font-weight: 500;
}
.free-chip { background: var(--color-primary-light); color: var(--color-primary); }
.paid-chip { background: var(--color-success-light); color: #065F46; }
.chip-dot { width: 12rpx; height: 12rpx; border-radius: 6rpx; }
.chip-dot-free { background: var(--color-primary); }
.chip-dot-paid { background: var(--color-success); }
.quota-hint { display: block; margin-top: 16rpx; font-size: 22rpx; color: var(--color-text-tertiary); }

.quota-actions {
    display: flex;
    gap: 12rpx;
    justify-content: space-between;
    align-items: center;
    margin-top: 20rpx;
    padding-top: 16rpx;
    border-top: 1rpx solid var(--color-divider);
}
.quota-actions .quota-hint { margin-top: 0; font-size: 24rpx; color: var(--color-text-secondary); }

.buy-btn {
    flex: 1;
    height: 64rpx;
    line-height: 64rpx;
    padding: 0 28rpx;
    background: linear-gradient(135deg, #D97706, #F59E0B);
    color: #FFF;
    border-radius: var(--radius-full);
    font-size: 26rpx;
    font-weight: 700;
    border: none;
    box-shadow: 0 6rpx 16rpx rgba(217,119,6,0.20);
    transition: all 0.18s var(--ease-out);
}
.buy-btn:active { transform: scale(0.94); }

.redeem-btn {
    height: 64rpx;
    line-height: 64rpx;
    padding: 0 28rpx;
    background: linear-gradient(135deg, #7C3AED, #A78BFA);
    color: #FFF;
    border-radius: var(--radius-full);
    font-size: 26rpx;
    font-weight: 700;
    border: none;
    box-shadow: 0 6rpx 16rpx rgba(124,58,237,0.18);
    transition: all 0.18s var(--ease-out);
    flex: 1;
}
.redeem-btn:active { transform: scale(0.94); }

.redeem-mask {
    position: fixed;
    inset: 0;
    background: rgba(15,23,42,0.5);
    z-index: 999;
    display: flex;
    align-items: center;
    justify-content: center;
    animation: fadeIn 0.2s var(--ease-out);
}
.redeem-popup {
    width: 640rpx;
    background: #FFF;
    border-radius: var(--radius-xl);
    padding: 48rpx 40rpx 36rpx;
    animation: scaleIn 0.25s var(--ease-spring);
}
.redeem-title { font-size: 36rpx; font-weight: 800; color: var(--color-text-primary); display: block; text-align: center; }
.redeem-desc { font-size: 26rpx; color: var(--color-text-tertiary); display: block; text-align: center; margin: 10rpx 0 32rpx; }
.redeem-input {
    width: 100%; height: 88rpx;
    background: var(--color-bg);
    border-radius: var(--radius-md);
    padding: 0 var(--space-lg);
    font-size: 32rpx;
    font-weight: 700;
    text-align: center;
    letter-spacing: 4rpx;
    text-transform: uppercase;
    box-sizing: border-box;
    border: 2rpx solid var(--color-border);
}
.redeem-btns { display: flex; gap: 12rpx; margin-top: 32rpx; }
.redeem-cancel {
    flex: 1; height: 80rpx; line-height: 80rpx;
    background: var(--color-bg); color: var(--color-text-secondary);
    border-radius: var(--radius-full); font-size: 28rpx; font-weight: 600; border: none;
}
.redeem-cancel:active { transform: scale(0.96); }
.redeem-confirm {
    flex: 2; height: 80rpx; line-height: 80rpx;
    background: var(--color-success-gradient); color: #FFF;
    border-radius: var(--radius-full); font-size: 28rpx; font-weight: 700; border: none;
    box-shadow: 0 8rpx 20rpx rgba(5,150,105,0.18);
}
.redeem-confirm:active { transform: scale(0.96); }
.redeem-confirm[disabled] { opacity: 0.5; transform: none; }

.quick-actions {
    display: flex;
    gap: var(--space-md);
    margin-bottom: 36rpx;
}

.action-item {
    flex: 1;
    background: var(--color-surface);
    border-radius: var(--radius-lg);
    padding: 28rpx 20rpx;
    display: flex;
    flex-direction: column;
    align-items: center;
    box-shadow: var(--shadow-xs);
    border: 1rpx solid var(--color-border);
    transition: all 0.22s var(--ease-out);
    animation: fadeInUp 0.4s 0.35s var(--ease-out) backwards;
}
.action-item:nth-child(2) { animation-delay: 0.45s; }
.action-item:nth-child(3) { animation-delay: 0.55s; }
.action-item:active { transform: scale(0.94); box-shadow: var(--shadow-md); border-color: var(--color-primary-soft); }

.action-icon {
    width: 72rpx; height: 72rpx; border-radius: 18rpx;
    display: flex; align-items: center; justify-content: center;
    font-size: 36rpx; font-weight: 700; margin-bottom: 12rpx; color: #FFF;
    transition: transform 0.22s var(--ease-out);
}
.action-item:active .action-icon { transform: rotate(180deg); }
.action-create { background: linear-gradient(135deg, var(--color-primary), #6C8CFF); }
.action-student { background: linear-gradient(135deg, #10B981, #34D399); }
.action-feedback { background: linear-gradient(135deg, #F59E0B, #F97316); }
.action-text { font-size: 26rpx; font-weight: 600; color: var(--color-text-primary); }

.section-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
}
.section-meta { font-size: 24rpx; color: var(--color-text-tertiary); margin-bottom: var(--space-lg); }

.assignment-card {
    padding: 24rpx;
}

.card-top {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    margin-bottom: 12rpx;
}

.card-title { font-size: 30rpx; font-weight: 700; color: var(--color-text-primary); }
.card-title-row { display: flex; align-items: center; gap: 12rpx; flex: 1; min-width: 0; }

.card-score {
    font-size: 24rpx; font-weight: 600; color: var(--color-primary);
    background: var(--color-primary-light); padding: 6rpx 16rpx;
    border-radius: var(--radius-full); transition: all 0.2s;
}

.card-mid { display: flex; align-items: center; gap: var(--space-sm); margin-bottom: 20rpx; }
.card-class {
    font-size: 22rpx; color: var(--color-success); background: var(--color-success-light);
    padding: 4rpx 14rpx; border-radius: var(--radius-full); font-weight: 500;
}
.card-date { font-size: 24rpx; color: var(--color-text-tertiary); }
.card-bottom { display: flex; gap: var(--space-sm); }

.card-btn {
    flex: 1;
    height: 64rpx;
    line-height: 64rpx;
    font-size: 26rpx;
    font-weight: 600;
    border-radius: var(--radius-md);
    border: none;
    text-align: center;
    transition: all 0.18s var(--ease-out);
}
.card-btn:active { transform: scale(0.94); }
.btn-grade { background: var(--color-primary-gradient); color: #FFF; box-shadow: 0 4rpx 12rpx rgba(37,99,235,0.15); }
.btn-grade:active { box-shadow: 0 2rpx 6rpx rgba(37,99,235,0.1); }
.btn-batch { background: var(--color-mint); color: #FFF; }
.btn-batch:active { opacity: 0.85; transform: scale(0.94); }
.btn-records { background: var(--color-divider); color: var(--color-text-secondary); }
.btn-records:active { background: var(--color-border); transform: scale(0.94); }

.assignment-card:nth-child(1) { animation-delay: 0.4s; }
.assignment-card:nth-child(2) { animation-delay: 0.45s; }
.assignment-card:nth-child(3) { animation-delay: 0.5s; }
</style>
