<template>
    <view class="page-container">
        <view class="type-switch">
            <view class="type-item" :class="{ active: form.assignmentType === 'ESSAY' }" @click="setType('ESSAY')">
                <text class="type-title">作文作业</text>
                <text class="type-sub">题目、范文、作文批改</text>
            </view>
            <view class="type-item" :class="{ active: form.assignmentType === 'WORKBOOK' }" @click="setType('WORKBOOK')">
                <text class="type-title">练习册作业</text>
                <text class="type-sub">答案页、逐题、知识点</text>
            </view>
        </view>

        <view class="form-group" v-if="showSubjectPicker">
            <text class="input-label">科目 <text class="required">*</text></text>
            <view class="subject-selector">
                <view v-for="item in subjects" :key="item" class="subject-item" :class="{ active: form.subject === item }" @click="selectSubject(item)">
                    <text class="subject-text">{{ item }}</text>
                </view>
            </view>
        </view>

        <view class="form-group">
            <text class="input-label">班级 <text class="required">*</text></text>
            <picker :range="classes" @change="onClassChange">
                <view class="picker-box">
                    <text :class="form.className ? 'picker-val' : 'picker-placeholder'">
                        {{ form.className || '请选择班级' }}
                    </text>
                    <text class="picker-arrow">›</text>
                </view>
            </picker>
        </view>

        <view class="form-group">
            <text class="input-label">作业标题 <text class="required">*</text></text>
            <input class="input-field" v-model="form.title" :placeholder="form.assignmentType === 'WORKBOOK' ? '例：数学练习册 P23 1-8题' : '例：高考英语作文模拟题'" />
        </view>

        <view v-if="isWorkbook" class="workbook-panel">
            <view class="form-group">
                <text class="input-label">题号范围 <text class="required">*</text></text>
                <input class="input-field" v-model="form.questionRange" placeholder="例：P23 1-8题 / 1-10 / A组 1-6" />
            </view>

            <view class="form-group">
                <text class="input-label">作业说明</text>
                <textarea class="form-textarea small" v-model="form.question" placeholder="可选：告诉学生提交哪几页、是否需要拍过程等" />
            </view>

            <view class="form-group">
                <view class="label-row">
                    <text class="input-label">答案页图片 <text class="required">*</text></text>
                    <button class="inline-btn" @click="chooseAnswerImages">添加图片</button>
                </view>
                <view v-if="answerImages.length" class="image-grid">
                    <view v-for="(path, idx) in answerImages" :key="path" class="image-cell">
                        <image :src="path" mode="aspectFill" class="thumb" />
                        <text class="remove" @click="removeAnswerImage(idx)">×</text>
                    </view>
                </view>
                <view v-else class="upload-empty" @click="chooseAnswerImages">
                    <text>拍摄或选择答案页，AI 会提取标准答案、解析和知识点</text>
                </view>
            </view>
        </view>

        <view v-else>
            <view class="form-group">
                <view class="label-row">
                    <text class="input-label">题目要求 <text class="required">*</text></text>
                    <view class="ocr-btn" @click="captureOCR('question')">
                        <text class="ocr-text">拍照识别</text>
                    </view>
                </view>
                <textarea class="form-textarea" v-model="form.question" :placeholder="ocrLoading === 'question' ? 'OCR识别中...' : '请输入作文题目要求和写作要点...'" />
            </view>

            <view class="form-group">
                <view class="label-row">
                    <text class="input-label">范文/参考答案 <text class="required">*</text></text>
                    <view class="ocr-btn" @click="captureOCR('modelEssay')">
                        <text class="ocr-text">拍照识别</text>
                    </view>
                </view>
                <textarea class="form-textarea" v-model="form.modelEssay" :placeholder="ocrLoading === 'modelEssay' ? 'OCR识别中...' : '请输入参考范文，或点击拍照识别...'" />
            </view>

            <view class="form-row">
                <view class="form-group half">
                    <text class="input-label">最少字数</text>
                    <input class="input-field" v-model.number="form.wordLimitMin" type="number" placeholder="0" />
                </view>
                <view class="form-group half">
                    <text class="input-label">最多字数</text>
                    <input class="input-field" v-model.number="form.wordLimitMax" type="number" placeholder="0" />
                </view>
            </view>
        </view>

        <view class="form-group">
            <text class="input-label">满分 <text class="required">*</text></text>
            <input class="input-field" v-model.number="form.totalScore" type="number" :placeholder="isWorkbook ? '100' : '15'" />
        </view>

        <button class="btn-primary" @click="handleSubmit" :disabled="submitting" style="margin-top: 40rpx;">
            {{ submitting ? (isWorkbook ? '创建并解析答案中...' : '创建中...') : '创建作业' }}
        </button>
    </view>
</template>

<script>
import { createAssignment, getStudentClasses, getTeacherInfo, ocrRecognize, uploadAssignmentAnswerKey } from '@/api/index.js'

export default {
    data() {
        return {
            form: {
                assignmentType: 'ESSAY',
                title: '',
                question: '',
                modelEssay: '',
                questionRange: '',
                wordLimitMin: 0,
                wordLimitMax: 0,
                totalScore: 15,
                scoringCriteria: '',
                className: '',
                subject: '英语'
            },
            subjects: ['英语', '语文', '数学', '物理', '化学', '生物'],
            classes: [],
            answerImages: [],
            submitting: false,
            ocrLoading: '',
            showSubjectPicker: true
        }
    },
    computed: {
        isWorkbook() {
            return this.form.assignmentType === 'WORKBOOK'
        }
    },
    onLoad() {
        try {
            const info = getTeacherInfo()
            if (info && info.subject && info.subject !== '全科') {
                this.form.subject = info.subject
                this.showSubjectPicker = false
            }
        } catch(e) {}
        this.loadClasses()
    },
    methods: {
        async loadClasses() {
            try {
                const res = await getStudentClasses()
                if (res.code === 200 && res.data) this.classes = res.data
            } catch(e) {}
        },
        setType(type) {
            this.form.assignmentType = type
            this.form.totalScore = type === 'WORKBOOK' ? 100 : 15
        },
        onClassChange(e) {
            this.form.className = this.classes[e.detail.value]
        },
        selectSubject(subject) {
            this.form.subject = subject
        },
        chooseAnswerImages() {
            uni.chooseImage({
                count: Math.max(1, 9 - this.answerImages.length),
                sizeType: ['original'],
                sourceType: ['camera', 'album'],
                success: (res) => {
                    this.answerImages = this.answerImages.concat(res.tempFilePaths || []).slice(0, 9)
                }
            })
        },
        removeAnswerImage(index) {
            this.answerImages.splice(index, 1)
        },
        captureOCR(field) {
            uni.chooseImage({
                count: 1,
                sizeType: ['compressed'],
                sourceType: ['camera', 'album'],
                success: async (imgRes) => {
                    this.ocrLoading = field
                    uni.showLoading({ title: 'OCR识别中...', mask: true })
                    try {
                        const result = await ocrRecognize(imgRes.tempFilePaths[0])
                        uni.hideLoading()
                        if (result.code === 200) {
                            const text = result.data.text
                            this.form[field] = this.form[field] ? this.form[field] + '\n' + text : text
                            uni.showToast({ title: '识别成功', icon: 'success' })
                        }
                    } catch(e) {
                        uni.hideLoading()
                    }
                    this.ocrLoading = ''
                }
            })
        },
        validate() {
            if (!this.form.title || !this.form.className || !this.form.subject || !this.form.totalScore) {
                return '请填写标题、班级、科目和满分'
            }
            if (this.isWorkbook) {
                if (!this.form.questionRange) return '请填写题号范围'
                if (!this.answerImages.length) return '请上传答案页图片'
                return ''
            }
            if (!this.form.question || !this.form.modelEssay) return '请填写题目要求和参考范文'
            return ''
        },
        async handleSubmit() {
            const message = this.validate()
            if (message) { uni.showToast({ title: message, icon: 'none' }); return }
            this.submitting = true
            try {
                const res = await createAssignment(this.form)
                if (res.code === 200) {
                    const assignmentId = res.data.id
                    if (this.isWorkbook) {
                        await uploadAssignmentAnswerKey(assignmentId, this.answerImages)
                        uni.showToast({ title: '已创建，答案解析中', icon: 'success' })
                        setTimeout(() => { uni.redirectTo({ url: '/pages/assignment/detail?id=' + assignmentId }) }, 800)
                    } else {
                        uni.showToast({ title: '创建成功', icon: 'success' })
                        setTimeout(() => uni.navigateBack(), 800)
                    }
                } else {
                    uni.showToast({ title: res.message || '创建失败', icon: 'none' })
                    this.submitting = false
                }
            } catch(e) {
                uni.showToast({ title: '网络请求失败', icon: 'none' })
                this.submitting = false
            }
        }
    }
}
</script>

<style scoped>
.type-switch { display: grid; grid-template-columns: 1fr 1fr; gap: 14rpx; margin-bottom: 28rpx; }
.type-item { background: var(--color-surface); border: 2rpx solid var(--color-border); border-radius: var(--radius-md); padding: 22rpx; }
.type-item.active { border-color: var(--color-primary); background: var(--color-primary-light); }
.type-title { display: block; font-size: 30rpx; font-weight: 800; color: var(--color-text-primary); }
.type-sub { display: block; font-size: 22rpx; color: var(--color-text-tertiary); margin-top: 6rpx; }
.subject-selector { display: flex; flex-wrap: wrap; gap: 12rpx; }
.subject-item { min-width: 120rpx; text-align: center; background: var(--color-surface); border-radius: var(--radius-full); padding: 16rpx 20rpx; border: 2rpx solid var(--color-border); }
.subject-item.active { border-color: var(--color-primary); background: var(--color-primary-light); }
.subject-text { font-size: 26rpx; font-weight: 700; color: var(--color-text-primary); }
.form-group { margin-bottom: 28rpx; }
.form-row { display: flex; gap: var(--space-md); }
.half { flex: 1; }
.label-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12rpx; }
.required { color: var(--color-danger); font-weight: 600; }
.picker-box { display: flex; justify-content: space-between; align-items: center; background: var(--color-surface); border-radius: var(--radius-md); padding: 20rpx 24rpx; box-shadow: var(--shadow-sm); }
.picker-val { font-size: 28rpx; color: var(--color-text-primary); }
.picker-placeholder { color: var(--color-text-placeholder); font-size: 28rpx; }
.picker-arrow { color: var(--color-text-tertiary); font-size: 34rpx; }
.form-textarea { width: 100%; min-height: 200rpx; background: var(--color-surface); border-radius: var(--radius-md); padding: 20rpx; font-size: 28rpx; color: var(--color-text-primary); box-sizing: border-box; border: 2rpx solid transparent; }
.form-textarea.small { min-height: 140rpx; }
.ocr-btn, .inline-btn { background: var(--color-primary-light); color: var(--color-primary); border-radius: var(--radius-full); padding: 8rpx 20rpx; font-size: 22rpx; border: none; line-height: 1.4; }
.image-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12rpx; }
.image-cell { position: relative; height: 180rpx; border-radius: var(--radius-md); overflow: hidden; background: var(--color-surface); }
.thumb { width: 100%; height: 100%; }
.remove { position: absolute; right: 8rpx; top: 8rpx; width: 44rpx; height: 44rpx; line-height: 40rpx; text-align: center; background: rgba(15,23,42,0.72); color: #FFF; border-radius: 50%; font-size: 32rpx; }
.upload-empty { border: 2rpx dashed var(--color-primary-soft); background: var(--color-primary-light); border-radius: var(--radius-md); padding: 44rpx 24rpx; text-align: center; color: var(--color-primary); font-size: 26rpx; line-height: 1.6; }
.workbook-panel { border-left: 6rpx solid var(--color-primary); padding-left: 18rpx; }
</style>
