<template>
    <view class="page-container">
        <view class="info-bar">
            <view>
                <text class="info-label">批量批改</text>
                <text class="info-title">{{ assignmentTitle }}</text>
            </view>
            <text class="info-count">{{ queuedList.length }} 已选</text>
        </view>

        <view v-if="!grading && !allDone">
            <!-- 已排队区域 -->
            <view v-if="queuedList.length" class="queued-section">
                <text class="section-label">已排队（{{ queuedList.length }}人）</text>
                <scroll-view scroll-x class="queued-scroll">
                    <view v-for="(item, idx) in queuedList" :key="item.id" class="queued-chip"
                          @click="rephotoStudent(item)">
                        <image v-if="item.imagePath" :src="item.imagePath" class="queued-thumb" />
                        <text class="queued-name">{{ item.name }}</text>
                        <text class="queued-retake">重拍</text>
                    </view>
                </scroll-view>
            </view>

            <!-- 搜索栏 -->
            <view class="search-bar surface-panel">
                <input class="search-input" v-model="keyword" placeholder="搜索学生姓名..."
                       @input="onSearch" confirm-type="search" />
            </view>

            <!-- 班级 Tab -->
            <scroll-view scroll-x class="class-tabs" v-if="classes.length > 1">
                <view v-for="cls in classOptions" :key="cls"
                      :class="['class-tab', activeClass === cls ? 'class-tab-active' : '']"
                      @click="activeClass = cls">
                    {{ cls === '__all__' ? '全部（' + students.length + '）' : cls }}
                </view>
            </scroll-view>

            <!-- 学生列表 -->
            <text class="section-label">
                {{ keyword ? '搜索结果' : '全部学生' }}（{{ filteredStudents.length }}人）
            </text>

            <view v-for="item in filteredStudents" :key="item.id" class="student-card"
                  @click="onStudentClick(item)">
                <view class="student-left">
                    <text class="student-name">{{ item.name }}</text>
                    <text class="student-class">{{ item.className || '未分班' }} · {{ item.studentNo || '无学号' }}</text>
                </view>
                <view class="student-status">
                    <text v-if="item.graded" class="tag tag-graded">已批改 {{ item.graded.aiScore }}分</text>
                    <text v-else-if="item.queued" class="tag tag-queued">已排队</text>
                    <text v-else class="tag tag-pending">点击拍照</text>
                </view>
            </view>

            <view v-if="filteredStudents.length === 0" class="empty-state">
                <text>未找到匹配的学生</text>
            </view>
        </view>

        <!-- 进度条 -->
        <view v-if="grading" class="progress-section surface-panel">
            <text class="progress-kicker">批量任务</text>
            <text class="progress-title">正在批改</text>
            <view class="progress-bar-wrap">
                <progress :percent="progressPercent" stroke-width="12" activeColor="#4A6CF7" backgroundColor="#E2E8F0" border-radius="6" />
            </view>
            <text class="progress-text">{{ progress.current }} / {{ progress.total }}</text>
            <text class="progress-student">{{ progress.name ? '最新完成：' + progress.name : '等待首个结果' }}</text>
        </view>

        <!-- 结果汇总 -->
        <view v-if="allDone" class="result-section">
            <text class="section-title">批改完成（{{ results.length }}人）</text>
            <view v-for="(item, idx) in results" :key="idx" class="result-card"
                  @click="viewResult(item)">
                <view class="result-left">
                    <text class="result-name">{{ item.studentName }}</text>
                    <text :class="item.success ? 'result-score' : 'result-fail'">
                        {{ item.success ? (item.aiScore + '分') : '失败' }}
                    </text>
                </view>
                <text class="result-arrow">查看详情 →</text>
            </view>
            <button class="btn-secondary" @click="goBack" style="margin-top: var(--space-2xl);">返回作业</button>
        </view>

        <!-- 底部按钮 -->
        <view v-if="!grading && !allDone" class="bottom-bar">
            <button class="btn-primary" @click="startBatchGrade" :disabled="queuedList.length === 0">
                开始批改 {{ queuedList.length }} 人
            </button>
        </view>
    </view>
</template>

<script>
import { getStudents, getStudentClasses, getEssaysByAssignment, uploadEssay, getEssayProgress } from '@/api/index.js'

export default {
    data() {
        return {
            assignmentId: '',
            assignmentTitle: '',
            students: [],
            classes: [],
            activeClass: '__all__',
            keyword: '',
            gradedMap: {},
            grading: false,
            allDone: false,
            progress: { current: 0, total: 0, name: '' },
            results: []
        }
    },
    computed: {
        classOptions() {
            return ['__all__', ...this.classes]
        },
        queuedList() {
            return this.students.filter(s => s.queued && !s.graded)
        },
        filteredStudents() {
            let list = this.students
            if (this.activeClass !== '__all__') {
                list = list.filter(s => (s.className || '未分班') === this.activeClass)
            }
            if (this.keyword.trim()) {
                const kw = this.keyword.trim().toLowerCase()
                list = list.filter(s =>
                    s.name.toLowerCase().includes(kw) ||
                    (s.studentNo && s.studentNo.includes(kw)) ||
                    (s.className && s.className.includes(kw))
                )
            }
            return list
        },
        progressPercent() {
            if (this.progress.total === 0) return 0
            return Math.round(this.progress.current / this.progress.total * 100)
        }
    },
    onLoad(options) {
        this.assignmentId = options.assignmentId
        this.assignmentTitle = decodeURIComponent(options.title || '')
        const className = decodeURIComponent(options.className || '')
        if (className) {
            this.activeClass = className
        }
        this.loadData()
    },
    methods: {
        async loadData() {
            try {
                const [studentRes, essayRes, classRes] = await Promise.all([
                    getStudents(),
                    getEssaysByAssignment(this.assignmentId),
                    getStudentClasses()
                ])
                const gradedMap = {}
                if (essayRes.code === 200 && essayRes.data) {
                    essayRes.data.forEach(e => {
                        if (e.status === 'GRADED' || e.status === 'TEACHER_REVIEWED') {
                            gradedMap[e.studentId] = {
                                essayId: e.id,
                                aiScore: e.aiScore,
                                finalScore: e.finalScore
                            }
                        }
                    })
                }
                this.gradedMap = gradedMap

                if (classRes.code === 200 && classRes.data) {
                    this.classes = classRes.data
                }

                if (studentRes.code === 200 && studentRes.data) {
                    this.students = studentRes.data.map(s => ({
                        ...s,
                        queued: false,
                        imagePath: '',
                        graded: gradedMap[s.id] || null
                    }))
                }
            } catch (e) {}
        },
        onSearch() {},
        onStudentClick(item) {
            if (item.graded) {
                uni.showToast({ title: '该学生已批改', icon: 'none' })
                return
            }
            if (item.queued) {
                uni.showToast({ title: '已排队，可点击已排队区域重拍', icon: 'none' })
                return
            }
            this.takePhoto(item)
        },
        rephotoStudent(item) {
            this.takePhoto(item)
        },
        takePhoto(item) {
            uni.showActionSheet({
                itemList: ['拍照', '从相册选择'],
                success: (res) => {
                    const sourceType = res.tapIndex === 0 ? ['camera'] : ['album']
                    uni.chooseImage({
                        count: 1,
                        sizeType: ['original'],
                        sourceType: sourceType,
                        success: (imgRes) => {
                            item.imagePath = imgRes.tempFilePaths[0]
                            item.queued = true
                            this.keyword = ''
                            this.$forceUpdate()
                        }
                    })
                }
            })
        },
        async startBatchGrade() {
            const queue = this.queuedList
            if (queue.length === 0) return

            this.grading = true
            this.progress = { current: 0, total: queue.length, name: '' }
            this.results = []

            const C = 3
            for (let i = 0; i < queue.length; i += C) {
                const chunk = queue.slice(i, i + C)
                await Promise.all(chunk.map((student) => {
                    return uploadEssay(student.imagePath, this.assignmentId, student.id)
                        .then(async res => {
                            if (res.code === 200 && res.data) {
                                const done = await this.waitEssayDone(res.data.essayId)
                                this.progress.current = Math.min(this.progress.current + 1, queue.length)
                                this.progress.name = student.name
                                this.results.push({
                                    studentName: student.name,
                                    success: done.success,
                                    aiScore: done.aiScore || '-',
                                    essayId: res.data.essayId
                                })
                            } else {
                                this.progress.current = Math.min(this.progress.current + 1, queue.length)
                                this.results.push({ studentName: student.name, success: false })
                            }
                        })
                        .catch(() => {
                            this.progress.current = Math.min(this.progress.current + 1, queue.length)
                            this.results.push({ studentName: student.name, success: false })
                        })
                }))
            }

            this.grading = false
            this.allDone = true
        },
        async waitEssayDone(essayId) {
            const maxAttempts = 150
            for (let i = 0; i < maxAttempts; i++) {
                try {
                    const res = await getEssayProgress(essayId)
                    const data = res.data || {}
                    if (data.status === 'GRADED' || data.status === 'TEACHER_REVIEWED') {
                        return {
                            success: true,
                            aiScore: data.gradingResult?.totalScore
                        }
                    }
                    if (data.status === 'FAILED') {
                        return { success: false }
                    }
                } catch (e) {}
                await new Promise(resolve => setTimeout(resolve, 2000))
            }
            return { success: false }
        },
        viewResult(item) {
            if (item.success && item.essayId) {
                uni.navigateTo({
                    url: '/pages/grading/result?essayId=' + item.essayId
                        + '&studentName=' + encodeURIComponent(item.studentName)
                })
            }
        },
        goBack() {
            uni.navigateBack()
        }
    }
}
</script>

<style scoped>
.page-container { padding: var(--space-lg) var(--space-lg); padding-bottom: 140rpx; }

.info-bar {
    background: var(--color-ink);
    border-radius: var(--radius-xl);
    padding: 28rpx;
    margin-bottom: var(--space-lg);
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    box-shadow: var(--shadow-md);
}

.info-label {
    font-size: 22rpx;
    color: #99F6E4;
    font-weight: 700;
    display: block;
    margin-bottom: 6rpx;
}

.info-title {
    font-size: 32rpx;
    color: #FFF;
    font-weight: 800;
    line-height: 1.35;
}

.info-count {
    font-size: 22rpx;
    color: #BAE6FD;
    background: rgba(255, 255, 255, 0.10);
    border: 1rpx solid rgba(255, 255, 255, 0.18);
    border-radius: var(--radius-full);
    padding: 8rpx 18rpx;
    flex-shrink: 0;
}

/* 搜索 */
.search-bar {
    margin-bottom: var(--space-md);
    padding: 4rpx;
}

.search-input {
    width: 100%;
    height: 80rpx;
    background: var(--color-surface-muted);
    border-radius: var(--radius-full);
    padding: 0 var(--space-lg);
    font-size: 28rpx;
    box-sizing: border-box;
    box-shadow: none;
}

/* 班级 Tab */
.class-tabs { white-space: nowrap; margin-bottom: var(--space-md); }

.class-tab {
    display: inline-block;
    padding: 14rpx 28rpx;
    margin-right: 12rpx;
    border-radius: var(--radius-full);
    font-size: 24rpx;
    color: var(--color-text-secondary);
    background: var(--color-surface);
    border: 1rpx solid var(--color-border);
    font-weight: 500;
    transition: all 0.18s;
}

.class-tab-active {
    background: var(--color-primary);
    color: #FFF;
    border-color: var(--color-primary);
    box-shadow: 0 4rpx 12rpx rgba(37, 99, 235, 0.18);
}

/* 排队区域 */
.queued-section { margin-bottom: var(--space-md); }

.section-label {
    font-size: 26rpx;
    color: var(--color-text-secondary);
    margin-bottom: var(--space-sm);
    display: block;
    font-weight: 500;
}

.queued-scroll { white-space: nowrap; padding-bottom: 4rpx; }

.queued-chip {
    display: inline-flex;
    flex-direction: column;
    align-items: center;
    background: var(--color-surface);
    border-radius: var(--radius-md);
    padding: 12rpx;
    margin-right: 14rpx;
    border: 2rpx solid var(--color-primary-soft);
    width: 120rpx;
    box-shadow: var(--shadow-sm);
}

.queued-thumb { width: 96rpx; height: 96rpx; border-radius: var(--radius-sm); margin-bottom: 6rpx; }

.queued-name {
    font-size: 22rpx;
    color: var(--color-text-primary);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    max-width: 100rpx;
}

.queued-retake { font-size: 20rpx; color: var(--color-primary); margin-top: 2rpx; }

/* 学生卡片 */
.student-card {
    background: var(--color-surface);
    border-radius: var(--radius-md);
    padding: 22rpx 24rpx;
    margin-bottom: var(--space-sm);
    display: flex;
    justify-content: space-between;
    align-items: center;
    box-shadow: var(--shadow-sm);
    border: 1rpx solid var(--color-border);
}

.student-left { flex: 1; }

.student-name { font-size: 30rpx; font-weight: 600; color: var(--color-text-primary); display: block; }

.student-class {
    font-size: 24rpx;
    color: var(--color-text-tertiary);
    margin-top: 4rpx;
    display: block;
}

.tag {
    font-size: 22rpx;
    padding: 6rpx 16rpx;
    border-radius: var(--radius-full);
    white-space: nowrap;
    font-weight: 500;
}

.tag-graded { background: var(--color-success-light); color: var(--color-success); }
.tag-queued { background: var(--color-primary-light); color: var(--color-primary); }
.tag-pending { background: var(--color-divider); color: var(--color-text-tertiary); }

/* 底部按钮 */
.bottom-bar {
    position: fixed;
    bottom: 0;
    left: 0;
    right: 0;
    padding: 16rpx var(--space-lg);
    padding-bottom: calc(16rpx + env(safe-area-inset-bottom));
    background: var(--color-surface);
    box-shadow: 0 -2rpx 12rpx rgba(0, 0, 0, 0.06);
}

/* 进度 */
.progress-section {
    text-align: center;
    padding: 60rpx 40rpx;
}

.progress-kicker {
    color: var(--color-primary);
    font-size: 22rpx;
    font-weight: 800;
    display: block;
    margin-bottom: 8rpx;
}

.progress-title {
    font-size: 32rpx;
    font-weight: 700;
    color: var(--color-text-primary);
    display: block;
    margin-bottom: 40rpx;
}

.progress-bar-wrap { margin-bottom: 24rpx; }

.progress-text {
    font-size: 56rpx;
    font-weight: 800;
    color: var(--color-primary);
    display: block;
}

.progress-student {
    font-size: 28rpx;
    color: var(--color-text-tertiary);
    margin-top: 12rpx;
    display: block;
}

/* 结果 */
.result-section { padding-top: var(--space-lg); }

.result-card {
    background: var(--color-surface);
    border-radius: var(--radius-md);
    padding: 22rpx 24rpx;
    margin-bottom: var(--space-sm);
    display: flex;
    justify-content: space-between;
    align-items: center;
    box-shadow: var(--shadow-sm);
    border: 1rpx solid var(--color-border);
}

.result-name { font-size: 28rpx; font-weight: 600; color: var(--color-text-primary); }

.result-score {
    font-size: 28rpx;
    color: var(--color-success);
    margin-left: 12rpx;
    font-weight: 700;
}

.result-fail { font-size: 28rpx; color: var(--color-danger); margin-left: 12rpx; }

.result-arrow { font-size: 26rpx; color: var(--color-primary); }
</style>
