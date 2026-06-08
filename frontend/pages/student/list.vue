<template>
    <view class="page-container">
        <!-- 顶部：班级下拉 + 新建班级 -->
        <view class="header-bar">
            <picker v-if="isAllClasses" class="class-picker" :range="classOptions" :value="classIndex" @change="onClassChange">
                <view class="picker-display">
                    <text class="picker-text">{{ '全部班级' }}</text>
                    <text class="picker-arrow">▾</text>
                </view>
            </picker>
            <view v-else class="back-btn" @click="backToAll">
                <text class="back-arrow">‹</text>
                <text class="back-text">{{ activeClass }}</text>
                <text class="back-desc">· 返回全部</text>
            </view>
            <button class="add-btn" @click="goCreateClass">新建</button>
        </view>

        <!-- 全部班级：分组视图 -->
        <view v-if="isAllClasses">
            <view v-if="students.length === 0" class="empty-state">
                <text class="empty-state-icon">—</text>
                <text class="empty-state-text">暂无学生，点击右上角新建班级</text>
            </view>
            <view v-for="group in groupedStudents" :key="group.className" class="class-group">
                <view class="group-header card" @click="selectClass(group.className)">
                    <view class="group-left">
                        <view class="group-mark"></view>
                        <text class="group-name">{{ group.className }}</text>
                        <text class="group-count">{{ group.students.length }}人</text>
                    </view>
                    <text class="group-arrow">›</text>
                </view>
            </view>
        </view>

        <!-- 单个班级：管理模式 -->
        <view v-else>
            <view class="class-info-bar card">
                <view class="class-info-top">
                    <text class="class-info-name">{{ activeClass }}</text>
                    <text class="badge badge-primary">{{ filteredStudents.length }}人</text>
                </view>
                <view class="class-info-btns">
                    <button class="mini-btn mini-btn-primary" @click="goAddToClass">+ 添加学生</button>
                    <button class="mini-btn mini-btn-danger" @click="handleDeleteClass">删除班级</button>
                </view>
            </view>

            <view v-if="filteredStudents.length === 0" class="empty-state">
                <text class="empty-state-text">该班级暂无学生</text>
            </view>

            <view v-for="item in filteredStudents" :key="item.id" class="student-card card">
                <view class="avatar" :style="{ background: avatarColor(item.name) }">
                    <text class="avatar-text">{{ item.name.charAt(0) }}</text>
                </view>
                <view class="info">
                    <text class="name">{{ item.name }}</text>
                    <view class="detail-row">
                        <text class="detail">账号：{{ item.username || ('stu' + item.id) }}</text>
                        <text class="detail">{{ item.studentNo || '无学号' }}</text>
                        <text v-if="item.className" class="detail-tag">{{ item.className }}</text>
                    </view>
                </view>
                <view class="delete-btn" @click="handleDelete(item)">
                    <text>删除</text>
                </view>
            </view>
        </view>
    </view>
</template>

<script>
import { getStudents, getStudentClasses, deleteStudent, deleteClass } from '@/api/index.js'

const avatarColors = [
    '#4A6CF7', '#10B981', '#F59E0B', '#EF4444',
    '#8B5CF6', '#EC4899', '#06B6D4', '#F97316'
]

export default {
    data() {
        return {
            students: [],
            classes: [],
            activeClass: '全部班级'
        }
    },
    computed: {
        isAllClasses() {
            return !this.classes.includes(this.activeClass)
        },
        classOptions() {
            return ['全部班级', ...this.classes]
        },
        classIndex() {
            if (this.isAllClasses) return 0
            const idx = this.classes.indexOf(this.activeClass)
            return idx >= 0 ? idx + 1 : 0
        },
        groupedStudents() {
            const groups = {}
            this.students.forEach(s => {
                const key = s.className || '未分班'
                if (!groups[key]) groups[key] = []
                groups[key].push(s)
            })
            return Object.keys(groups).map(key => ({
                className: key,
                students: groups[key]
            }))
        },
        filteredStudents() {
            if (this.isAllClasses) return this.students
            return this.students.filter(s => (s.className || '未分班') === this.activeClass)
        }
    },
    onShow() {
        this.loadData()
    },
    methods: {
        avatarColor(name) {
            let hash = 0
            for (let i = 0; i < name.length; i++) hash = name.charCodeAt(i) + ((hash << 5) - hash)
            return avatarColors[Math.abs(hash) % avatarColors.length]
        },
        async loadData() {
            try {
                const [studentRes, classRes] = await Promise.all([
                    getStudents(),
                    getStudentClasses()
                ])
                if (studentRes.code === 200) {
                    this.students = studentRes.data || []
                }
                if (classRes.code === 200) {
                    this.classes = classRes.data || []
                }
            } catch(e) {}
        },
        onClassChange(e) {
            this.activeClass = this.classOptions[e.detail.value]
        },
        selectClass(className) {
            this.activeClass = className
        },
        backToAll() {
            this.activeClass = '全部班级'
        },
        goCreateClass() {
            uni.navigateTo({ url: '/pages/student/class-create' })
        },
        goAddToClass() {
            uni.navigateTo({ url: '/pages/student/class-create?className=' + encodeURIComponent(this.activeClass) })
        },
        handleDelete(item) {
            uni.showModal({
                title: '确认删除',
                content: '确定删除学生 ' + item.name + ' 吗？',
                success: async (res) => {
                    if (res.confirm) {
                        try {
                            await deleteStudent(item.id)
                            uni.showToast({ title: '已删除', icon: 'success' })
                            this.loadData()
                        } catch(e) {}
                    }
                }
            })
        },
        handleDeleteClass() {
            uni.showModal({
                title: '删除班级',
                content: '确定删除「' + this.activeClass + '」及其全部学生吗？此操作不可撤销。',
                success: async (res) => {
                    if (res.confirm) {
                        try {
                            const resp = await deleteClass(this.activeClass)
                            if (resp.code === 200) {
                                uni.showToast({ title: '已删除 ' + resp.data.deleted + ' 名学生', icon: 'success' })
                                this.activeClass = '全部班级'
                                this.loadData()
                            }
                        } catch(e) {
                            uni.showToast({ title: '删除失败', icon: 'none' })
                        }
                    }
                }
            })
        }
    }
}
</script>

<style scoped>
/* 顶部 */
.header-bar {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: var(--space-lg);
    gap: var(--space-md);
}

.class-picker { flex: 1; }

.picker-display {
    display: flex;
    justify-content: space-between;
    align-items: center;
    background: var(--color-surface);
    border-radius: var(--radius-md);
    padding: 20rpx 24rpx;
    box-shadow: var(--shadow-sm);
    border: 1rpx solid var(--color-border);
}

.picker-text {
    font-size: 28rpx;
    color: var(--color-text-primary);
    font-weight: 600;
}

.picker-arrow {
    font-size: 24rpx;
    color: var(--color-text-tertiary);
}

.back-btn {
    flex: 1;
    display: flex;
    align-items: center;
    gap: 4rpx;
    padding: 18rpx 20rpx;
    background: var(--color-surface);
    border-radius: var(--radius-md);
    border: 1rpx solid var(--color-border);
    transition: all 0.18s var(--ease-out);
}
.back-btn:active { transform: scale(0.97); background: var(--color-primary-light); }
.back-arrow {
    font-size: 40rpx;
    font-weight: 300;
    color: var(--color-primary);
    line-height: 1;
    margin-right: 4rpx;
}
.back-text {
    font-size: 28rpx;
    font-weight: 700;
    color: var(--color-text-primary);
}
.back-desc {
    font-size: 22rpx;
    color: var(--color-text-tertiary);
}

.add-btn {
    background: var(--color-ink);
    color: #FFF;
    padding: 16rpx 30rpx;
    border-radius: var(--radius-md);
    font-size: 26rpx;
    font-weight: 600;
    white-space: nowrap;
    border: none;
    height: auto;
    transition: all 0.2s;
}

.add-btn:active {
    background: var(--color-primary-dark);
    transform: scale(0.96);
}

/* 班级分组 */
.class-group { margin-bottom: var(--space-md); }

.group-header {
    padding: 28rpx 24rpx;
    display: flex;
    justify-content: space-between;
    align-items: center;
    border-left: 6rpx solid var(--color-mint);
}

.group-left {
    display: flex;
    align-items: center;
    gap: var(--space-md);
}

.group-mark {
    width: 18rpx;
    height: 48rpx;
    border-radius: 9rpx;
    background: var(--color-mint);
}

.group-name {
    font-size: 30rpx;
    font-weight: 700;
    color: var(--color-text-primary);
}

.group-count {
    font-size: 24rpx;
    color: var(--color-text-tertiary);
    background: var(--color-divider);
    padding: 6rpx 16rpx;
    border-radius: var(--radius-full);
    font-weight: 500;
}

.group-arrow {
    font-size: 32rpx;
    color: var(--color-text-tertiary);
}

/* 单班管理 */
.class-info-bar {
    padding: 24rpx;
    margin-bottom: var(--space-lg);
    border-left: 6rpx solid var(--color-primary);
}

.class-info-top {
    display: flex;
    align-items: center;
    gap: var(--space-md);
    margin-bottom: 20rpx;
}

.class-info-name {
    font-size: 36rpx;
    font-weight: 700;
    color: var(--color-text-primary);
}

.class-info-btns {
    display: flex;
    gap: var(--space-md);
}

.mini-btn {
    flex: 1;
    height: 72rpx;
    line-height: 72rpx;
    border-radius: var(--radius-md);
    font-size: 26rpx;
    font-weight: 600;
    border: none;
    text-align: center;
    transition: all 0.18s var(--ease-out);
}
.mini-btn:active { transform: scale(0.94); }

.mini-btn-primary {
    background: var(--color-primary);
    color: #FFF;
}

.mini-btn-danger {
    background: var(--color-danger-light);
    color: var(--color-danger);
    border: 2rpx solid var(--color-danger);
}

/* 学生卡片 */
.student-card {
    padding: 20rpx 24rpx;
    display: flex;
    align-items: center;
    margin-bottom: var(--space-sm);
    border: 1rpx solid var(--color-border);
    transition: all 0.18s var(--ease-out);
}
.student-card:active { transform: scale(0.985); background: var(--color-primary-light); border-color: var(--color-primary-soft); }

.avatar {
    width: 76rpx;
    height: 76rpx;
    border-radius: 20rpx;
    display: flex;
    align-items: center;
    justify-content: center;
    margin-right: 20rpx;
}

.avatar-text {
    font-size: 32rpx;
    font-weight: 700;
    color: #FFF;
}

.info { flex: 1; }

.name {
    font-size: 28rpx;
    font-weight: 600;
    color: var(--color-text-primary);
    display: block;
}

.detail {
    font-size: 24rpx;
    color: var(--color-text-tertiary);
    display: block;
}

.detail-row {
    display: flex;
    align-items: center;
    gap: var(--space-sm);
    margin-top: 4rpx;
}

.detail-tag {
    font-size: 20rpx;
    color: var(--color-success);
    background: var(--color-success-light);
    padding: 2rpx 12rpx;
    border-radius: var(--radius-full);
    font-weight: 500;
}

.delete-btn {
    font-size: 24rpx;
    color: var(--color-danger);
    padding: 10rpx 20rpx;
    border-radius: var(--radius-full);
    background: var(--color-danger-light);
    font-weight: 500;
}

.empty-state-text {
    color: var(--color-text-tertiary);
    font-size: 28rpx;
}
</style>
