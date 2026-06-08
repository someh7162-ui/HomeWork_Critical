<template>
    <view class="page-container">
        <view v-if="!students.length" class="step1">
            <view class="info-tip">
                <text class="tip-icon">📋</text>
                <text class="tip-text">拍照上传班级花名册，AI 将自动识别学生姓名</text>
            </view>

            <view class="image-section">
                <view v-if="!imagePath" class="upload-area" @click="chooseImage">
                    <text class="upload-icon">📷</text>
                    <text class="upload-text">拍摄花名册</text>
                    <text class="upload-hint">支持打印表格或手写名单</text>
                </view>
                <view v-else class="preview-area">
                    <image :src="imagePath" mode="widthFix" class="preview-image" />
                    <button class="rechoose-btn" @click="chooseImage">重新选择</button>
                </view>
            </view>

            <button class="submit-btn" @click="handleOcr" :disabled="!imagePath || ocrLoading">
                {{ ocrLoading ? 'AI 正在识别学生姓名...' : '开始识别' }}
            </button>
        </view>

        <view v-else class="step2">
            <text class="section-title">确认学生名单（{{ students.length }}人）</text>
            <text class="section-hint">可修改、删除或添加学生信息</text>

            <view v-for="(item, idx) in students" :key="idx" class="student-row">
                <view class="row-index">{{ idx + 1 }}</view>
                <view class="row-fields">
                    <input class="row-input name-input" v-model="item.name" placeholder="姓名" />
                    <view class="row-sub">
                        <input class="row-input" v-model="item.className" placeholder="班级" />
                        <input class="row-input" v-model="item.studentNo" placeholder="学号" />
                    </view>
                </view>
                <view class="row-delete" @click="removeRow(idx)">
                    <text>×</text>
                </view>
            </view>

            <view class="add-row-btn" @click="addRow">
                <text>+ 手动添加一行</text>
            </view>

            <view class="bottom-btns">
                <button class="btn-back" @click="resetAll">重新拍照</button>
                <button class="btn-confirm" @click="handleBatchSave" :disabled="saving">
                    {{ saving ? '导入中...' : '确认导入 (' + students.length + '人)' }}
                </button>
            </view>
        </view>
    </view>
</template>

<script>
import { rosterOcr, batchAddStudents } from '@/api/index.js'

export default {
    data() {
        return {
            imagePath: '',
            students: [],
            ocrLoading: false,
            saving: false
        }
    },
    methods: {
        chooseImage() {
            uni.showActionSheet({
                itemList: ['拍照', '从相册选择'],
                success: (res) => {
                    const sourceType = res.tapIndex === 0 ? ['camera'] : ['album']
                    uni.chooseImage({
                        count: 1,
                        sizeType: ['original'],
                        sourceType: sourceType,
                        success: (imgRes) => {
                            this.imagePath = imgRes.tempFilePaths[0]
                        }
                    })
                }
            })
        },
        async handleOcr() {
            this.ocrLoading = true
            uni.showLoading({ title: '识别中...', mask: true })
            try {
                const res = await rosterOcr(this.imagePath)
                uni.hideLoading()
                if (res.code === 200 && res.data && res.data.length) {
                    this.students = res.data.map(s => ({
                        name: s.name || '',
                        className: s.className || '',
                        studentNo: s.studentNo || ''
                    }))
                } else {
                    uni.showToast({ title: res.message || '未识别到学生', icon: 'none' })
                }
            } catch (e) {
                uni.hideLoading()
                uni.showToast({ title: '识别失败，请重试', icon: 'none' })
            }
            this.ocrLoading = false
        },
        addRow() {
            this.students.push({ name: '', className: '', studentNo: '' })
        },
        removeRow(idx) {
            this.students.splice(idx, 1)
        },
        resetAll() {
            this.imagePath = ''
            this.students = []
        },
        async handleBatchSave() {
            const valid = this.students.filter(s => s.name && s.name.trim())
            if (valid.length === 0) {
                uni.showToast({ title: '请至少填写一个学生姓名', icon: 'none' })
                return
            }
            this.saving = true
            try {
                const res = await batchAddStudents(valid)
                if (res.code === 200) {
                    uni.showToast({ title: '成功导入 ' + res.data.count + ' 名学生', icon: 'success' })
                    setTimeout(() => uni.navigateBack(), 1200)
                } else {
                    uni.showToast({ title: res.message || '导入失败', icon: 'none' })
                }
            } catch (e) {
                uni.showToast({ title: '导入失败，请重试', icon: 'none' })
            }
            this.saving = false
        }
    }
}
</script>

<style scoped>
.page-container { padding: var(--space-lg) var(--space-xl); min-height: 100vh; background: var(--color-bg); }

.info-tip {
    background: var(--color-primary-light); border-radius: var(--radius-md);
    padding: var(--space-lg); display: flex; align-items: center; margin-bottom: var(--space-lg);
}
.tip-icon { font-size: 40rpx; margin-right: 16rpx; }
.tip-text { font-size: 26rpx; color: var(--color-primary); flex: 1; font-weight: 500; }

.image-section { margin-bottom: 40rpx; }
.upload-area {
    background: var(--color-surface); border: 2rpx dashed var(--color-primary);
    border-radius: var(--radius-lg); padding: 80rpx 40rpx; text-align: center;
    transition: all 0.18s;
}
.upload-area:active { background: var(--color-primary-light); }
.upload-icon { font-size: 72rpx; display: block; margin-bottom: 20rpx; }
.upload-text { font-size: 30rpx; color: var(--color-text-primary); font-weight: 700; display: block; }
.upload-hint { font-size: 24rpx; color: var(--color-text-tertiary); margin-top: 10rpx; display: block; }

.preview-image { width: 100%; border-radius: var(--radius-lg); }
.rechoose-btn {
    text-align: center; margin-top: 16rpx; background: var(--color-bg); color: var(--color-text-secondary);
    border: none; font-size: 26rpx; height: 60rpx; line-height: 60rpx;
    border-radius: var(--radius-full); width: 260rpx;
}

.submit-btn {
    width: 100%; height: 88rpx; line-height: 88rpx;
    background: var(--color-primary-gradient); color: #FFF; border-radius: var(--radius-full);
    font-size: 32rpx; font-weight: 700; border: none;
    box-shadow: 0 10rpx 24rpx rgba(37, 99, 235, 0.20);
}
.submit-btn[disabled] { opacity: 0.5; }

.section-title { font-size: 32rpx; font-weight: 700; color: var(--color-text-primary); display: block; }
.section-hint { font-size: 24rpx; color: var(--color-text-tertiary); margin-top: 8rpx; margin-bottom: var(--space-lg); display: block; }

.student-row {
    background: var(--color-surface); border-radius: var(--radius-md); padding: 20rpx;
    margin-bottom: 16rpx; display: flex; align-items: center;
}
.row-index {
    width: 50rpx; height: 50rpx; border-radius: 25rpx;
    background: var(--color-primary-light); color: var(--color-primary); font-size: 24rpx; font-weight: 700;
    display: flex; align-items: center; justify-content: center; margin-right: 16rpx;
    flex-shrink: 0;
}
.row-fields { flex: 1; }
.row-input {
    height: 64rpx; background: var(--color-bg); border-radius: var(--radius-sm);
    padding: 0 16rpx; font-size: 26rpx; box-sizing: border-box; border: 2rpx solid var(--color-border);
}
.name-input { margin-bottom: 10rpx; }
.row-sub { display: flex; gap: 10rpx; }
.row-sub .row-input { flex: 1; }
.row-delete {
    width: 50rpx; height: 50rpx; display: flex; align-items: center;
    justify-content: center; font-size: 32rpx; color: var(--color-danger); margin-left: 12rpx;
}

.add-row-btn {
    text-align: center; padding: 20rpx; color: var(--color-primary); font-size: 28rpx; font-weight: 500;
}

.bottom-btns {
    margin-top: 40rpx; display: flex; gap: 20rpx;
}
.btn-back {
    flex: 1; height: 80rpx; line-height: 80rpx; background: var(--color-bg);
    color: var(--color-text-secondary); border-radius: var(--radius-full); font-size: 28rpx; border: none;
}
.btn-confirm {
    flex: 2; height: 80rpx; line-height: 80rpx; background: var(--color-success);
    color: #FFF; border-radius: var(--radius-full); font-size: 28rpx; font-weight: 700; border: none;
}
.btn-confirm[disabled] { opacity: 0.6; }
</style>
