<template>
    <view class="page-container">
        <text class="section-title">全部作业</text>
        <view v-if="assignments.length === 0" class="empty-state-card">
            <text class="empty-state-icon">—</text>
            <text style="display:block;margin-top:16rpx;color:var(--color-text-tertiary);font-size:28rpx;">暂无作业，去创建第一个吧</text>
        </view>
        <view v-for="item in assignments" :key="item.id" class="card essay-card" @click="goDetail(item)">
            <view class="card-info">
                <view class="card-title-row">
                    <text class="card-title">{{ item.title }}</text>
                    <text v-if="item.subject" class="badge-subject" :class="'badge-subject-' + subjectClass(item)">{{ item.subject }}</text>
                </view>
                <view class="card-meta tag-row">
                    <text v-if="item.className" class="badge badge-primary">{{ item.className }}</text>
                    <text class="card-score-badge">满分 {{ item.totalScore }}</text>
                    <text class="card-date">{{ item.createdAt }}</text>
                </view>
            </view>
            <view class="card-actions">
                <view class="delete-btn" @click.stop="handleDelete(item)">
                    <text>删除</text>
                </view>
                <text class="card-arrow">›</text>
            </view>
        </view>
    </view>
</template>

<script>
import { getAssignments, deleteAssignment } from '@/api/index.js'

export default {
    data() {
        return { assignments: [] }
    },
    onShow() {
        this.loadData()
    },
    methods: {
        subjectClass(item) {
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
        goDetail(item) {
            uni.navigateTo({ url: '/pages/assignment/detail?id=' + item.id })
        },
        handleDelete(item) {
            uni.showModal({
                title: '确认删除',
                content: '确定删除作业「' + item.title + '」吗？该作业下的批改记录也将一并删除。',
                success: async (res) => {
                    if (res.confirm) {
                        try {
                            await deleteAssignment(item.id)
                            uni.showToast({ title: '已删除', icon: 'success' })
                            this.loadData()
                        } catch(e) {}
                    }
                }
            })
        }
    }
}
</script>

<style scoped>
.card {
    padding: 24rpx;
    display: flex;
    justify-content: space-between;
    align-items: center;
}

.card-info {
    flex: 1;
    min-width: 0;
}

.card-title-row {
    display: flex;
    align-items: center;
    gap: 12rpx;
    margin-bottom: 10rpx;
}

.card-title {
    font-size: 30rpx;
    font-weight: 700;
    color: var(--color-text-primary);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.card-score-badge {
    font-size: 22rpx;
    color: var(--color-primary);
    background: var(--color-primary-light);
    padding: 4rpx 12rpx;
    border-radius: var(--radius-full);
    font-weight: 600;
}

.card-date {
    font-size: 24rpx;
    color: var(--color-text-tertiary);
}

.card-actions {
    display: flex;
    align-items: center;
    gap: var(--space-md);
    flex-shrink: 0;
    margin-left: var(--space-sm);
}

.delete-btn {
    font-size: 24rpx;
    color: var(--color-danger);
    padding: 8rpx 20rpx;
    border-radius: var(--radius-full);
    background: var(--color-danger-light);
    font-weight: 500;
}

.card-arrow {
    color: var(--color-text-tertiary);
    font-size: 36rpx;
    font-weight: 300;
}
</style>
