<template>
    <view class="page-container">
        <view class="info-bar">
            <view>
                <text class="info-label">当前作业</text>
                <text class="info-title">{{ assignmentTitle }}</text>
            </view>
            <text class="info-badge">{{ isWorkbook ? '练习册多页' : '作文单页' }}</text>
        </view>

        <view class="form-group surface-panel">
            <view class="field-head">
                <text class="input-label">选择学生 <text class="required">*</text></text>
                <text class="field-meta">{{ students.length }} 人</text>
            </view>
            <picker :range="studentNames" @change="onStudentChange">
                <view class="picker-box">
                    <text :class="selectedStudent ? 'picker-val' : 'picker-placeholder'">
                        {{ selectedStudent ? selectedStudent.name : '请选择学生' }}
                    </text>
                    <text class="picker-arrow">›</text>
                </view>
            </picker>
        </view>

        <view class="image-section surface-panel">
            <view v-if="!imagePaths.length" class="upload-area" @click="chooseImage">
                <view class="upload-icon-circle">
                    <text class="upload-icon-text">＋</text>
                </view>
                <text class="upload-title">{{ isWorkbook ? '上传学生练习册页面' : '上传学生作文图片' }}</text>
                <text class="upload-hint">{{ isWorkbook ? '可一次选择多页，按题号范围批改' : '建议使用清晰原图' }}</text>
            </view>
            <view v-else class="preview-area">
                <view class="image-grid" :class="{ single: !isWorkbook }">
                    <view v-for="(path, idx) in imagePaths" :key="path" class="image-cell">
                        <image :src="path" mode="aspectFill" class="preview-image" />
                        <text class="remove" @click="removeImage(idx)">×</text>
                    </view>
                    <view v-if="isWorkbook && imagePaths.length < 9" class="add-cell" @click="chooseImage">
                        <text>＋</text>
                    </view>
                </view>
                <view class="preview-actions">
                    <button class="rechoose-btn" @click="chooseImage">{{ isWorkbook ? '继续添加' : '重新选择' }}</button>
                </view>
            </view>
        </view>

        <button class="btn-primary" @click="handleSubmit" :disabled="submitting || !canSubmit" style="margin-top: var(--space-xl);">
            <text v-if="submitting">上传中...</text>
            <text v-else>开始智能批改</text>
        </button>

        <view v-if="submitting" class="processing-tip">
            <view class="tip-spinner"></view>
            <text class="tip-title">正在上传作业图片</text>
            <text class="tip-desc">上传完成后会进入批改进度页</text>
        </view>
    </view>
</template>

<script>
import { getAssignmentDetail, getStudents, uploadEssay, uploadWorkbook } from '@/api/index.js'

export default {
    data() {
        return {
            assignmentId: '',
            assignmentTitle: '',
            assignmentType: 'ESSAY',
            initialStudentId: '',
            students: [],
            studentNames: [],
            selectedStudent: null,
            imagePaths: [],
            submitting: false
        }
    },
    computed: {
        isWorkbook() {
            return this.assignmentType === 'WORKBOOK'
        },
        canSubmit() {
            return this.selectedStudent && this.imagePaths.length
        }
    },
    onLoad(options) {
        this.assignmentId = options.assignmentId
        this.assignmentTitle = decodeURIComponent(options.title || '')
        this.assignmentType = options.assignmentType || 'ESSAY'
        this.initialStudentId = options.studentId || ''
        this.loadAssignment()
        this.loadStudents()
    },
    methods: {
        async loadAssignment() {
            try {
                const res = await getAssignmentDetail(this.assignmentId)
                if (res.code === 200 && res.data) {
                    this.assignmentType = res.data.assignmentType || 'ESSAY'
                    this.assignmentTitle = res.data.title || this.assignmentTitle
                }
            } catch(e) {}
        },
        async loadStudents() {
            try {
                const res = await getStudents()
                if (res.code === 200) {
                    this.students = res.data || []
                    this.studentNames = this.students.map(s => s.name)
                    if (this.initialStudentId) {
                        const found = this.students.find(s => String(s.id) === String(this.initialStudentId))
                        if (found) this.selectedStudent = found
                    }
                }
            } catch(e) {}
        },
        onStudentChange(e) {
            this.selectedStudent = this.students[e.detail.value]
        },
        chooseImage() {
            uni.chooseImage({
                count: this.isWorkbook ? Math.max(1, 9 - this.imagePaths.length) : 1,
                sizeType: ['original'],
                sourceType: ['camera', 'album'],
                success: (imgRes) => {
                    const paths = imgRes.tempFilePaths || []
                    this.imagePaths = this.isWorkbook ? this.imagePaths.concat(paths).slice(0, 9) : paths.slice(0, 1)
                }
            })
        },
        removeImage(index) {
            this.imagePaths.splice(index, 1)
        },
        async handleSubmit() {
            if (!this.canSubmit) {
                uni.showToast({ title: '请选择学生并上传图片', icon: 'none' })
                return
            }
            this.submitting = true
            uni.showLoading({ title: '正在上传...', mask: true })

            try {
                const res = this.isWorkbook
                    ? await uploadWorkbook(this.imagePaths, this.assignmentId, this.selectedStudent.id)
                    : await uploadEssay(this.imagePaths[0], this.assignmentId, this.selectedStudent.id)
                uni.hideLoading()
                if (res.code === 200) {
                    const data = res.data
                    const studentName = this.selectedStudent.name
                    this.selectedStudent = null
                    this.imagePaths = []
                    this.submitting = false
                    uni.redirectTo({
                        url: '/pages/grading/result?essayId=' + data.essayId
                            + '&studentName=' + encodeURIComponent(studentName)
                    })
                } else {
                    uni.showToast({ title: res.message || '上传失败', icon: 'none' })
                    this.submitting = false
                }
            } catch(e) {
                uni.hideLoading()
                uni.showToast({ title: '上传失败，请重试', icon: 'none' })
                this.submitting = false
            }
        }
    }
}
</script>

<style scoped>
.page-container { padding: var(--space-lg) var(--space-xl); }
.info-bar { background: var(--color-ink); border-radius: var(--radius-xl); padding: 28rpx; margin-bottom: var(--space-lg); display: flex; justify-content: space-between; align-items: flex-start; box-shadow: var(--shadow-md); }
.info-label { display: block; font-size: 22rpx; color: #93C5FD; font-weight: 600; margin-bottom: 6rpx; }
.info-title { font-size: 32rpx; color: #FFF; font-weight: 800; line-height: 1.35; }
.info-badge { font-size: 22rpx; color: #BAE6FD; background: rgba(255, 255, 255, 0.10); border: 1rpx solid rgba(255, 255, 255, 0.18); border-radius: var(--radius-full); padding: 8rpx 18rpx; flex-shrink: 0; }
.form-group { margin-bottom: var(--space-lg); padding: 22rpx; }
.field-head { display: flex; justify-content: space-between; align-items: center; }
.field-meta { font-size: 22rpx; color: var(--color-text-tertiary); }
.required { color: var(--color-danger); font-weight: 600; }
.picker-box { display: flex; justify-content: space-between; align-items: center; background: var(--color-surface-muted); border-radius: var(--radius-md); padding: 20rpx 24rpx; border: 1rpx solid var(--color-border); }
.picker-val { font-size: 28rpx; color: var(--color-text-primary); }
.picker-placeholder { color: var(--color-text-placeholder); font-size: 28rpx; }
.picker-arrow { color: var(--color-text-tertiary); font-size: 34rpx; }
.image-section { margin-bottom: var(--space-sm); padding: 18rpx; }
.upload-area { background: linear-gradient(180deg, #FFFFFF, #F8FAFC); border: 2rpx dashed #93C5FD; border-radius: var(--radius-lg); padding: 80rpx 40rpx; text-align: center; }
.upload-icon-circle { width: 96rpx; height: 96rpx; border-radius: 24rpx; background: var(--color-primary-gradient); display: flex; align-items: center; justify-content: center; margin: 0 auto 24rpx; }
.upload-icon-text { font-size: 48rpx; font-weight: 300; color: #FFF; }
.upload-title { font-size: 32rpx; color: var(--color-text-primary); font-weight: 600; display: block; }
.upload-hint { font-size: 24rpx; color: var(--color-text-tertiary); margin-top: 10rpx; display: block; }
.image-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12rpx; }
.image-grid.single { grid-template-columns: 1fr; }
.image-cell { height: 210rpx; border-radius: var(--radius-md); overflow: hidden; position: relative; background: var(--color-surface-muted); }
.image-grid.single .image-cell { height: 520rpx; }
.preview-image { width: 100%; height: 100%; }
.remove { position: absolute; right: 8rpx; top: 8rpx; width: 44rpx; height: 44rpx; line-height: 40rpx; text-align: center; background: rgba(15,23,42,0.72); color: #FFF; border-radius: 50%; font-size: 32rpx; }
.add-cell { height: 210rpx; border-radius: var(--radius-md); border: 2rpx dashed var(--color-primary-soft); display: flex; align-items: center; justify-content: center; color: var(--color-primary); font-size: 54rpx; background: var(--color-primary-light); }
.preview-actions { text-align: center; margin-top: 16rpx; }
.rechoose-btn { background: var(--color-primary-light); color: var(--color-primary); border: none; font-size: 26rpx; height: 64rpx; line-height: 64rpx; border-radius: var(--radius-full); width: 280rpx; display: inline-block; }
.processing-tip { text-align: center; margin-top: 40rpx; padding: var(--space-xl); background: var(--color-primary-light); border-radius: var(--radius-lg); }
.tip-spinner { width: 56rpx; height: 56rpx; border: 4rpx solid var(--color-primary-soft); border-top-color: var(--color-primary); border-radius: 50%; margin: 0 auto 24rpx; animation: spin 0.8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
.tip-title { font-size: 30rpx; font-weight: 700; color: var(--color-primary); display: block; }
.tip-desc { font-size: 24rpx; color: var(--color-text-tertiary); margin-top: 10rpx; display: block; }
</style>
