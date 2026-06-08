<template>
    <view class="page-container">
        <view class="section">
            <text class="section-title">个人设置</text>

            <view class="setting-card">
                <text class="setting-label">姓名</text>
                <input class="setting-input" v-model="teacherName" placeholder="请输入姓名" />
            </view>

            <view class="setting-card">
                <text class="setting-label">当前科目</text>
                <view class="subject-picker">
                    <view class="subject-opt" :class="{ active: subject === '英语' }" @click="subject = '英语'">
                        <text>英语</text>
                    </view>
                    <view class="subject-opt" :class="{ active: subject === '语文' }" @click="subject = '语文'">
                        <text>语文</text>
                    </view>
                    <view class="subject-opt" :class="{ active: subject === '数学' }" @click="subject = '数学'">
                        <text>数学</text>
                    </view>
                    <view class="subject-opt" :class="{ active: subject === '全科' }" @click="subject = '全科'">
                        <text>全科</text>
                    </view>
                </view>
            </view>

            <button class="save-btn" @click="handleSave" :disabled="saving">
                {{ saving ? '保存中...' : '保存设置' }}
            </button>
        </view>

        <view class="section">
            <text class="section-title">兑换卡密</text>
            <view class="setting-card">
                <text class="setting-label">输入卡密</text>
                <input class="setting-input" v-model="cardKey" placeholder="请输入16位卡密" />
                <button class="redeem-btn" @click="handleRedeem" :disabled="redeeming || !cardKey.trim()">
                    {{ redeeming ? '兑换中...' : '兑换额度' }}
                </button>
            </view>
        </view>

        <view class="section">
            <text class="section-title">关于</text>
            <view class="about-card">
                <text class="about-item">墨知智评 v1.0</text>
                <text class="about-desc">AI 智能作业批改平台</text>
            </view>
            <button class="logout-btn" @click="handleLogout">退出登录</button>
        </view>
    </view>
</template>

<script>
import { getTeacherInfo, setTeacherInfo, updateProfile, getToken } from '@/api/index.js'
import { redeemCard } from '@/api/index.js'

export default {
    data() {
        const info = getTeacherInfo() || {}
        return {
            teacherName: info.name || '',
            subject: info.subject || '英语',
            saving: false,
            cardKey: '',
            redeeming: false
        }
    },
    methods: {
        async handleSave() {
            if (!this.teacherName.trim()) {
                uni.showToast({ title: '请输入姓名', icon: 'none' })
                return
            }
            this.saving = true
            try {
                const res = await updateProfile({ name: this.teacherName.trim(), subject: this.subject })
                if (res.code === 200) {
                    setTeacherInfo(res.data)
                    uni.showToast({ title: '设置已保存', icon: 'success' })
                    setTimeout(() => uni.navigateBack(), 800)
                } else {
                    uni.showToast({ title: res.message || '保存失败', icon: 'none' })
                }
            } catch (e) {
                uni.showToast({ title: '保存失败', icon: 'none' })
            }
            this.saving = false
        },
        handleLogout() {
            uni.removeStorageSync('token')
            uni.removeStorageSync('teacherInfo')
            uni.reLaunch({ url: '/pages/login/login' })
        },
        async handleRedeem() {
            if (!this.cardKey.trim()) return
            this.redeeming = true
            try {
                const res = await redeemCard(this.cardKey.trim())
                if (res.code === 200) {
                    const data = res.data
                    uni.showToast({ title: '兑换成功！+' + data.addedQuota + '次额度', icon: 'success' })
                    this.cardKey = ''
                } else {
                    uni.showToast({ title: res.message || '兑换失败', icon: 'none' })
                }
            } catch (e) {
                uni.showToast({ title: '兑换失败', icon: 'none' })
            }
            this.redeeming = false
        }
    }
}
</script>

<style scoped>
.page-container { padding: var(--space-lg) var(--space-xl); min-height: 100vh; background: var(--color-bg); }

.section { margin-bottom: var(--space-2xl); }
.section-title { font-size: 34rpx; font-weight: 700; color: var(--color-text-primary); margin-bottom: var(--space-lg); }

.setting-card {
    background: var(--color-surface);
    border-radius: var(--radius-lg);
    padding: 24rpx;
    margin-bottom: var(--space-md);
    border: 1rpx solid var(--color-border);
}
.setting-label { font-size: 26rpx; color: var(--color-text-secondary); font-weight: 600; margin-bottom: 12rpx; display: block; }
.setting-input { width: 100%; height: 80rpx; background: var(--color-bg); border-radius: var(--radius-md); padding: 0 var(--space-lg); font-size: 30rpx; font-weight: 600; box-sizing: border-box; border: 2rpx solid var(--color-border); }

.subject-picker { display: grid; grid-template-columns: 1fr 1fr 1fr 1fr; gap: 12rpx; }
.subject-opt {
    background: var(--color-bg);
    border-radius: var(--radius-md);
    padding: 18rpx 0;
    text-align: center;
    font-size: 26rpx;
    font-weight: 600;
    color: var(--color-text-secondary);
    border: 2rpx solid var(--color-border);
    transition: all 0.18s var(--ease-out);
}
.subject-opt.active { border-color: var(--color-primary); background: var(--color-primary-light); color: var(--color-primary); }
.subject-opt:active { transform: scale(0.95); }

.save-btn {
    width: 100%; height: 88rpx; line-height: 88rpx;
    background: var(--color-primary-gradient); color: #FFF;
    border-radius: var(--radius-full); font-size: 30rpx; font-weight: 700;
    border: none; margin-top: var(--space-lg);
    box-shadow: 0 10rpx 24rpx rgba(37,99,235,0.20);
}
.save-btn:active { transform: scale(0.96); }
.save-btn[disabled] { opacity: 0.5; transform: none; }

.about-card {
    background: var(--color-surface);
    border-radius: var(--radius-lg);
    padding: 32rpx 24rpx;
    border: 1rpx solid var(--color-border);
    text-align: center;
}
.about-item { font-size: 32rpx; font-weight: 700; color: var(--color-text-primary); display: block; }
.about-desc { font-size: 24rpx; color: var(--color-text-tertiary); margin-top: 8rpx; display: block; }

.logout-btn {
    width: 100%; height: 80rpx; line-height: 80rpx;
    background: var(--color-danger-light); color: var(--color-danger);
    border-radius: var(--radius-full); font-size: 28rpx; font-weight: 600;
    border: none; margin-top: var(--space-lg);
}
.logout-btn:active { transform: scale(0.96); }

.redeem-btn {
    width: 100%; height: 80rpx; line-height: 80rpx;
    background: var(--color-success-gradient); color: #FFF;
    border-radius: var(--radius-full); font-size: 28rpx; font-weight: 700;
    border: none; margin-top: var(--space-lg);
    box-shadow: 0 10rpx 24rpx rgba(5,150,105,0.20);
    transition: all 0.18s var(--ease-out);
}
.redeem-btn:active { transform: scale(0.96); }
.redeem-btn[disabled] { opacity: 0.5; transform: none; }
</style>
