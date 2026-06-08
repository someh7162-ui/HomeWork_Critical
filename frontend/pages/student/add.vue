<template>
    <view class="page-container">
        <view class="form-group">
            <text class="form-label">姓名 <text class="required">*</text></text>
            <input class="form-input" v-model="form.name" placeholder="学生姓名" />
        </view>
        <view class="form-group">
            <text class="form-label">班级</text>
            <input class="form-input" v-model="form.className" placeholder="例：高三(1)班" />
        </view>
        <view class="form-group">
            <text class="form-label">学号</text>
            <input class="form-input" v-model="form.studentNo" placeholder="学生学号" />
        </view>
        <view class="form-group">
            <text class="form-label">登录账号</text>
            <input class="form-input" v-model="form.username" placeholder="留空自动生成，如 stu12" />
        </view>
        <view class="form-group">
            <text class="form-label">初始密码</text>
            <input class="form-input" v-model="form.password" placeholder="留空默认 123456" password />
        </view>

        <button class="submit-btn" @click="handleSubmit" :disabled="submitting">
            {{ submitting ? '添加中...' : '添加学生' }}
        </button>
    </view>
</template>

<script>
import { addStudent } from '@/api/index.js'

export default {
    data() {
        return {
            form: { name: '', className: '', studentNo: '', username: '', password: '' },
            submitting: false
        }
    },
    methods: {
        async handleSubmit() {
            if (!this.form.name) {
                uni.showToast({ title: '请输入姓名', icon: 'none' })
                return
            }
            this.submitting = true
            try {
                const res = await addStudent(this.form)
                if (res.code === 200) {
                    uni.showToast({ title: '添加成功', icon: 'success' })
                    setTimeout(() => uni.navigateBack(), 1000)
                }
            } catch(e) {}
            this.submitting = false
        }
    }
}
</script>

<style scoped>
.page-container { padding: 30rpx; }
.form-group { margin-bottom: 30rpx; }
.form-label { font-size: 28rpx; font-weight: bold; color: #333; margin-bottom: 12rpx; display: block; }
.required { color: #E74C3C; }
.form-input {
    width: 100%; height: 80rpx; background: #FFF; border-radius: 12rpx;
    padding: 0 20rpx; font-size: 28rpx; box-sizing: border-box;
}
.submit-btn {
    width: 100%; height: 88rpx; line-height: 88rpx;
    background: #4A90D9; color: #FFF; border-radius: 44rpx;
    font-size: 32rpx; font-weight: bold; border: none; margin-top: 40rpx;
}
.submit-btn[disabled] { opacity: 0.6; }
</style>
