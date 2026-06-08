<template>
    <view class="page-container">
        <!-- 班级名 -->
        <view class="form-group">
            <text class="form-label">{{ isEdit ? '班级' : '班级名称' }} <text v-if="!isEdit" class="required">*</text></text>
            <view class="class-picker-row" v-if="!isEdit">
                <picker class="picker-grade" mode="selector" :range="gradeOptions" :value="gradeIdx" :disabled="isEdit" @change="onGradeChange">
                    <view class="picker-display">{{ gradeOptions[gradeIdx] }}</view>
                </picker>
                <text class="picker-join">(</text>
                <picker class="picker-num" mode="selector" :range="numOptions" :value="numIdx" :disabled="isEdit" @change="onNumChange">
                    <view class="picker-display">{{ numOptions[numIdx] }}</view>
                </picker>
                <text class="picker-join">)班</text>
            </view>
            <view v-else class="form-input disabled-text">{{ className }}</view>
        </view>

        <!-- 已添加学生列表 -->
        <view v-if="students.length" class="student-list">
            <text class="section-label">{{ isEdit ? '本次新增' : '已有' }} {{ students.length }} 人</text>
            <view v-for="(item, idx) in students" :key="idx" class="student-row">
                <text class="row-idx">{{ idx + 1 }}</text>
                <text class="row-name">{{ item.name }}</text>
                <text class="row-account">{{ item.username || '自动账号' }}</text>
                <text class="row-no">{{ item.studentNo || '' }}</text>
                <view class="row-del" @click="students.splice(idx, 1)"><text>×</text></view>
            </view>
        </view>

        <!-- 底部操作 -->
        <view class="bottom-section">
            <!-- 手动添加行 -->
            <view class="manual-add">
                <input class="mini-input" v-model="manualName" placeholder="姓名" />
                <input class="mini-input" v-model="manualNo" placeholder="学号(选填)" />
                <input class="mini-input" v-model="manualUsername" placeholder="账号(选填)" />
                <button class="mini-btn" @click="addManual" :disabled="!manualName.trim()">添加</button>
            </view>

            <view class="action-btns">
                <button class="action-btn roster-btn" @click="importRoster" :disabled="isEdit ? false : false">
                    📷 拍照导入花名册
                </button>
            </view>

            <button class="done-btn" @click="handleDone"
                    :disabled="!displayClassName.trim() || saving">
                {{ saving ? '保存中...' : (isEdit ? '添加新增学生到「' + className + '」（' + students.length + '人）' : '完成创建「' + displayClassName + '」（' + students.length + '人）') }}
            </button>
        </view>
    </view>
</template>

<script>
import { rosterOcr, batchAddStudents } from '@/api/index.js'

export default {
    data() {
        const numOptions = []
        for (let i = 1; i <= 20; i++) numOptions.push(String(i))
        return {
            className: '',
            gradeIdx: 2,
            numIdx: 0,
            gradeOptions: ['高一', '高二', '高三'],
            numOptions,
            students: [],
            manualName: '',
            manualNo: '',
            manualUsername: '',
            saving: false,
            isEdit: false
        }
    },
    onLoad(options) {
        if (options.className) {
            this.isEdit = true
            this.className = decodeURIComponent(options.className)
            uni.setNavigationBarTitle({ title: '添加学生' })
        }
    },
    computed: {
        displayClassName() {
            return this.gradeOptions[this.gradeIdx] + '(' + this.numOptions[this.numIdx] + ')班'
        }
    },
    methods: {
        onGradeChange(e) {
            this.gradeIdx = e.detail.value
        },
        onNumChange(e) {
            this.numIdx = e.detail.value
        },
        addManual() {
            if (!this.manualName.trim()) return
            this.students.push({
                name: this.manualName.trim(),
                studentNo: this.manualNo.trim(),
                username: this.manualUsername.trim(),
                className: ''
            })
            this.manualName = ''
            this.manualNo = ''
            this.manualUsername = ''
        },
        importRoster() {
            uni.showActionSheet({
                itemList: ['拍照', '从相册选择'],
                success: (res) => {
                    const sourceType = res.tapIndex === 0 ? ['camera'] : ['album']
                    uni.chooseImage({
                        count: 1,
                        sizeType: ['original'],
                        sourceType: sourceType,
                        success: async (imgRes) => {
                            uni.showLoading({ title: 'AI识别中...', mask: true })
                            try {
                                const res = await rosterOcr(imgRes.tempFilePaths[0])
                                uni.hideLoading()
                                if (res.code === 200 && res.data && res.data.length) {
                                    const parsed = res.data.map(s => ({
                                        name: s.name || '',
                                        studentNo: s.studentNo || '',
                                        username: '',
                                        className: ''
                                    }))
                                    this.students = [...this.students, ...parsed]
                                    uni.showToast({ title: '识别到 ' + parsed.length + ' 人', icon: 'success' })
                                } else {
                                    uni.showToast({ title: res.message || '未识别到学生', icon: 'none' })
                                }
                            } catch (e) {
                                uni.hideLoading()
                                uni.showToast({ title: '识别失败', icon: 'none' })
                            }
                        }
                    })
                }
            })
        },
        async handleDone() {
            const effectiveClassName = this.isEdit ? this.className : this.displayClassName
            if (!effectiveClassName.trim()) return
            const valid = this.students.filter(s => s.name && s.name.trim())
            if (valid.length === 0) {
                uni.showToast({ title: '请至少添加一名学生', icon: 'none' })
                return
            }
            valid.forEach(s => { s.className = effectiveClassName.trim() })
            this.saving = true
            try {
                const res = await batchAddStudents(valid)
                if (res.code === 200) {
                    const msg = this.isEdit
                        ? '已添加 ' + res.data.count + ' 人'
                        : '班级创建成功，' + res.data.count + '人已导入'
                    uni.showToast({ title: msg, icon: 'success' })
                    setTimeout(() => uni.navigateBack(), 1200)
                } else {
                    uni.showToast({ title: res.message || '操作失败', icon: 'none' })
                }
            } catch (e) {
                uni.showToast({ title: '操作失败，请重试', icon: 'none' })
            }
            this.saving = false
        }
    }
}
</script>

<style scoped>
.page-container { padding: var(--space-lg) var(--space-xl); min-height: 100vh; background: var(--color-bg); padding-bottom: 60rpx; }

.form-group { margin-bottom: var(--space-lg); }
.form-label { font-size: 28rpx; font-weight: 700; color: var(--color-text-primary); margin-bottom: 12rpx; display: block; }
.required { color: var(--color-danger); }
.form-input {
    width: 100%; height: 88rpx; background: var(--color-surface);
    border-radius: var(--radius-md); padding: 0 var(--space-lg);
    font-size: 28rpx; box-sizing: border-box; border: 2rpx solid var(--color-border);
}
.form-input:disabled { background: var(--color-surface-muted); color: var(--color-text-tertiary); }
.disabled-text { display: flex; align-items: center; color: var(--color-text-secondary); font-weight: 600; }

.class-picker-row {
    display: flex;
    align-items: center;
    gap: 4rpx;
}

.picker-grade, .picker-num {
    flex: 1;
}

.picker-display {
    height: 88rpx;
    line-height: 88rpx;
    background: var(--color-surface);
    border-radius: var(--radius-md);
    padding: 0 var(--space-lg);
    font-size: 30rpx;
    font-weight: 700;
    color: var(--color-primary);
    text-align: center;
    border: 2rpx solid var(--color-primary-soft);
    box-sizing: border-box;
    transition: all 0.18s var(--ease-out);
}

.picker-display:active {
    border-color: var(--color-primary);
    background: var(--color-primary-light);
}

.picker-join {
    font-size: 30rpx;
    color: var(--color-text-secondary);
    font-weight: 600;
}

.section-label { font-size: 26rpx; color: var(--color-text-secondary); margin-bottom: 12rpx; display: block; font-weight: 600; }

.student-row {
    display: flex; align-items: center; background: var(--color-surface);
    border-radius: var(--radius-md); padding: 16rpx 20rpx; margin-bottom: 10rpx;
    box-shadow: var(--shadow-sm); border: 1rpx solid var(--color-border);
}
.row-idx { width: 44rpx; height: 44rpx; line-height: 44rpx; text-align: center; font-size: 24rpx; color: var(--color-text-primary); background: var(--color-primary-light); border-radius: 22rpx; font-weight: 700; }
.row-name { flex: 1; font-size: 28rpx; font-weight: 700; color: var(--color-text-primary); margin-left: 16rpx; }
.row-account { font-size: 22rpx; color: var(--color-primary); margin-right: 12rpx; }
.row-no { font-size: 24rpx; color: var(--color-text-tertiary); margin-right: 16rpx; }
.row-del { font-size: 28rpx; color: var(--color-danger); padding: 0 10rpx; font-weight: 700; }

.bottom-section { margin-top: 40rpx; }

.manual-add { display: flex; gap: 12rpx; margin-bottom: 24rpx; align-items: center; }
.mini-input {
    flex: 1; height: 70rpx; background: var(--color-surface);
    border-radius: var(--radius-md); padding: 0 16rpx;
    font-size: 26rpx; box-sizing: border-box; border: 2rpx solid var(--color-border);
}
.mini-btn {
    width: 120rpx; height: 70rpx; line-height: 70rpx; background: var(--color-primary);
    color: #FFF; border-radius: var(--radius-md); font-size: 24rpx; font-weight: 600; border: none;
}
.mini-btn[disabled] { opacity: 0.5; }

.action-btns { margin-bottom: 24rpx; }
.action-btn {
    width: 100%; height: 80rpx; line-height: 80rpx; border-radius: var(--radius-md);
    font-size: 28rpx; border: none; text-align: center;
}
.roster-btn { background: var(--color-primary-light); color: var(--color-primary); border: 2rpx dashed var(--color-primary); }
.action-btn[disabled] { opacity: 0.5; }

.done-btn {
    width: 100%; height: 88rpx; line-height: 88rpx; background: var(--color-success);
    color: #FFF; border-radius: var(--radius-full); font-size: 30rpx; font-weight: 700; border: none;
    box-shadow: 0 8rpx 20rpx rgba(5, 150, 105, 0.20);
}
.done-btn[disabled] { opacity: 0.5; }
</style>
