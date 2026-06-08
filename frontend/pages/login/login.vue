<template>
    <view class="login-container">
        <view class="login-header">
            <view class="logo-icon">
                <view class="logo-inner">
                    <image class="logo-image" src="/static/logo.png" mode="aspectFit" />
                </view>
            </view>
            <text class="app-name">墨知智评</text>
            <text class="app-desc">AI 智能作业批改平台</text>
        </view>

        <view class="login-form card">
            <view class="mode-switch">
                <view :class="['mode-option', pageMode === 'LOGIN' ? 'active' : '']" @click="setPageMode('LOGIN')">
                    登录
                </view>
                <view :class="['mode-option', pageMode === 'REGISTER' ? 'active' : '']" @click="setPageMode('REGISTER')">
                    注册
                </view>
            </view>

            <view v-if="pageMode === 'LOGIN'" class="role-switch">
                <view :class="['role-option', loginRole === 'TEACHER' ? 'active' : '']" @click="loginRole = 'TEACHER'">
                    教师
                </view>
                <view :class="['role-option', loginRole === 'STUDENT' ? 'active' : '']" @click="loginRole = 'STUDENT'">
                    学生
                </view>
            </view>

            <view v-if="pageMode === 'REGISTER'" class="register-note">
                <text>注册教师账号后可创建班级、学生账号和批改任务。</text>
            </view>

            <template v-if="pageMode === 'LOGIN' && loginRole === 'TEACHER'">
                <view class="input-group">
                    <text class="input-label">用户名</text>
                    <input
                        class="input-field"
                        v-model="form.username"
                        placeholder="请输入教师用户名"
                        placeholder-style="color: #CBD5E1"
                    />
                </view>

                <view class="input-group">
                    <text class="input-label">密码</text>
                    <input
                        class="input-field"
                        v-model="form.password"
                        type="password"
                        placeholder="请输入密码"
                        placeholder-style="color: #CBD5E1"
                    />
                </view>
            </template>

            <template v-if="pageMode === 'LOGIN' && loginRole === 'STUDENT'">
                <view class="input-group">
                    <text class="input-label">学生账号</text>
                    <input
                        class="input-field"
                        v-model="studentForm.username"
                        placeholder="请输入学生账号"
                        placeholder-style="color: #CBD5E1"
                    />
                </view>

                <view class="input-group">
                    <text class="input-label">密码</text>
                    <input
                        class="input-field"
                        v-model="studentForm.password"
                        type="password"
                        placeholder="请输入密码"
                        placeholder-style="color: #CBD5E1"
                    />
                </view>
            </template>

            <template v-if="pageMode === 'REGISTER'">
                <view class="input-group">
                    <text class="input-label">教师姓名</text>
                    <input
                        class="input-field"
                        v-model="registerForm.name"
                        placeholder="请输入真实姓名"
                        placeholder-style="color: #CBD5E1"
                    />
                </view>

                <view class="input-group">
                    <text class="input-label">学科</text>
                    <input
                        class="input-field"
                        v-model="registerForm.subject"
                        placeholder="例如：英语"
                        placeholder-style="color: #CBD5E1"
                    />
                </view>

                <view class="input-group">
                    <text class="input-label">用户名</text>
                    <input
                        class="input-field"
                        v-model="registerForm.username"
                        placeholder="3-30 位字母、数字或下划线"
                        placeholder-style="color: #CBD5E1"
                    />
                </view>

                <view class="input-group">
                    <text class="input-label">密码</text>
                    <input
                        class="input-field"
                        v-model="registerForm.password"
                        type="password"
                        placeholder="至少 6 位"
                        placeholder-style="color: #CBD5E1"
                    />
                </view>

                <view class="input-group">
                    <text class="input-label">确认密码</text>
                    <input
                        class="input-field"
                        v-model="registerForm.confirmPassword"
                        type="password"
                        placeholder="请再次输入密码"
                        placeholder-style="color: #CBD5E1"
                    />
                </view>
            </template>

            <button class="btn-primary login-action" @click="handleSubmit" :disabled="loading">
                {{ actionText }}
            </button>

            <view v-if="pageMode === 'REGISTER'" class="form-link" @click="setPageMode('LOGIN')">
                已有账号，返回登录
            </view>

            <!-- #ifndef APP-PLUS -->
            <template v-if="pageMode === 'LOGIN' && loginRole === 'TEACHER'">
                <view class="divider">
                    <view class="divider-line"></view>
                    <text class="divider-text">其他方式</text>
                    <view class="divider-line"></view>
                </view>

                <button class="wechat-btn" @click="handleWechatLogin" :disabled="loading">
                    <text class="wechat-dot">●</text>
                    {{ loading ? '登录中...' : '微信一键登录' }}
                </button>
            </template>
            <!-- #endif -->
        </view>

        <text class="footer-text">扫码拍照，即时批改，高效教学</text>
    </view>
</template>

<script>
import {
    getToken,
    getUserRole,
    login,
    registerTeacher,
    setStudentInfo,
    setTeacherInfo,
    setToken,
    studentLogin,
    wechatLogin
} from '@/api/index.js'

export default {
    data() {
        return {
            pageMode: 'LOGIN',
            loginRole: 'TEACHER',
            form: {
                username: '',
                password: ''
            },
            studentForm: {
                username: '',
                password: ''
            },
            registerForm: {
                name: '',
                subject: '英语',
                username: '',
                password: '',
                confirmPassword: ''
            },
            loading: false
        }
    },
    computed: {
        actionText() {
            if (this.loading) {
                return this.pageMode === 'REGISTER' ? '注册中...' : '登录中...'
            }
            if (this.pageMode === 'REGISTER') {
                return '注册并进入教师端'
            }
            return this.loginRole === 'TEACHER' ? '教师登录' : '学生登录'
        }
    },
    onLoad() {
        if (getToken()) {
            if (getUserRole() === 'STUDENT') {
                uni.reLaunch({ url: '/pages/student-portal/assignments' })
            } else {
                uni.switchTab({ url: '/pages/home/home' })
            }
        }
    },
    methods: {
        setPageMode(mode) {
            this.pageMode = mode
            if (mode === 'REGISTER') {
                this.loginRole = 'TEACHER'
            }
        },
        handleSubmit() {
            if (this.pageMode === 'REGISTER') {
                this.handleRegister()
                return
            }
            this.handleLogin()
        },
        async handleLogin() {
            if (this.loginRole === 'TEACHER' && (!this.form.username || !this.form.password)) {
                uni.showToast({ title: '请填写用户名和密码', icon: 'none' })
                return
            }
            if (this.loginRole === 'STUDENT' && (!this.studentForm.username || !this.studentForm.password)) {
                uni.showToast({ title: '请填写学生账号和密码', icon: 'none' })
                return
            }
            this.loading = true
            try {
                const res = this.loginRole === 'TEACHER'
                    ? await login(this.form)
                    : await studentLogin(this.studentForm)
                if (res.code === 200) {
                    this.persistLogin(res.data)
                    uni.showToast({ title: '登录成功', icon: 'success' })
                    this.goHome()
                } else {
                    uni.showToast({ title: res.message || '登录失败', icon: 'none' })
                }
            } catch (e) {
                uni.showToast({ title: '网络请求失败', icon: 'none' })
            }
            this.loading = false
        },
        async handleRegister() {
            const form = this.registerForm
            if (!form.name || !form.username || !form.password || !form.confirmPassword) {
                uni.showToast({ title: '请完整填写注册信息', icon: 'none' })
                return
            }
            if (form.password !== form.confirmPassword) {
                uni.showToast({ title: '两次输入的密码不一致', icon: 'none' })
                return
            }
            this.loading = true
            try {
                const res = await registerTeacher(form)
                if (res.code === 200) {
                    this.loginRole = 'TEACHER'
                    this.persistLogin(res.data)
                    uni.showToast({ title: '注册成功', icon: 'success' })
                    this.goHome()
                } else {
                    uni.showToast({ title: res.message || '注册失败', icon: 'none' })
                }
            } catch (e) {
                uni.showToast({ title: '网络请求失败', icon: 'none' })
            }
            this.loading = false
        },
        persistLogin(data) {
            setToken(data.token)
            if (this.loginRole === 'TEACHER') {
                setTeacherInfo(data)
            } else {
                setStudentInfo(data)
            }
        },
        goHome() {
            setTimeout(() => {
                if (this.loginRole === 'TEACHER') {
                    uni.switchTab({ url: '/pages/home/home' })
                } else {
                    uni.reLaunch({ url: '/pages/student-portal/assignments' })
                }
            }, 500)
        },
        async handleWechatLogin() {
            this.loading = true
            try {
                const [loginErr, loginRes] = await new Promise((resolve) => {
                    uni.login({
                        provider: 'weixin',
                        success(res) { resolve([null, res]) },
                        fail(err) { resolve([err, null]) }
                    })
                })

                if (loginErr || !loginRes || !loginRes.code) {
                    uni.showToast({ title: '获取微信授权失败', icon: 'none' })
                    this.loading = false
                    return
                }

                const res = await wechatLogin(loginRes.code)
                if (res.code === 200) {
                    this.loginRole = 'TEACHER'
                    this.persistLogin(res.data)
                    uni.showToast({ title: '登录成功', icon: 'success' })
                    this.goHome()
                } else {
                    uni.showToast({ title: res.message || '微信登录失败', icon: 'none' })
                }
            } catch (e) {
                uni.showToast({ title: '网络请求失败', icon: 'none' })
            }
            this.loading = false
        }
    }
}
</script>

<style scoped>
.login-container {
    min-height: 100vh;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    background:
        radial-gradient(circle at 18% 12%, rgba(255, 255, 255, 0.26), transparent 30%),
        linear-gradient(180deg, #1E3A5F 0%, #2563EB 38%, #4f7df5 72%, #93B4FF 100%);
    padding: var(--space-2xl);
}

.login-header {
    display: flex;
    flex-direction: column;
    align-items: center;
    margin-bottom: 44rpx;
    animation: fadeInUp 0.7s var(--ease-out);
}

.logo-icon { margin-bottom: 24rpx; }

.logo-inner {
    width: 112rpx;
    height: 112rpx;
    border-radius: 30rpx;
    background: rgba(255, 255, 255, 0.18);
    backdrop-filter: blur(12px);
    display: flex;
    align-items: center;
    justify-content: center;
    border: 2rpx solid rgba(255, 255, 255, 0.28);
    box-shadow: 0 8rpx 32rpx rgba(0, 0, 0, 0.12);
    animation: bounce-in 0.8s var(--ease-spring);
}

.logo-image { width: 88rpx; height: 88rpx; border-radius: 20rpx; }
.app-name { font-size: 44rpx; font-weight: 800; color: #FFFFFF; margin-bottom: 10rpx; letter-spacing: 2rpx; }
.app-desc { font-size: 26rpx; color: rgba(255, 255, 255, 0.78); letter-spacing: 1rpx; }

.login-form {
    width: 100%;
    padding: 40rpx 32rpx;
    animation: fadeInUp 0.5s 0.15s var(--ease-out) backwards;
}

.mode-switch,
.role-switch {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 8rpx;
    padding: 8rpx;
    background: var(--color-surface-muted);
    border-radius: var(--radius-full);
}

.mode-switch { margin-bottom: 20rpx; }
.role-switch { margin-bottom: 28rpx; }

.mode-option,
.role-option {
    height: 64rpx;
    line-height: 64rpx;
    text-align: center;
    border-radius: var(--radius-full);
    font-size: 26rpx;
    color: var(--color-text-secondary);
    font-weight: 700;
}

.mode-option.active,
.role-option.active {
    background: var(--color-primary);
    color: #FFF;
    box-shadow: 0 6rpx 16rpx rgba(37, 99, 235, 0.18);
}

.register-note {
    padding: 18rpx 20rpx;
    margin-bottom: 24rpx;
    border-radius: var(--radius-md);
    background: rgba(37, 99, 235, 0.08);
    color: var(--color-primary);
    font-size: 24rpx;
    line-height: 1.6;
}

.input-group { margin-bottom: 22rpx; }

.input-label {
    display: block;
    margin-bottom: 10rpx;
    color: var(--color-text-secondary);
    font-size: 24rpx;
    font-weight: 700;
}

.input-field {
    box-sizing: border-box;
    width: 100%;
    background: var(--color-bg);
    border: 2rpx solid transparent;
    padding: 0 var(--space-lg);
    height: 92rpx;
    border-radius: var(--radius-md);
    font-size: 30rpx;
    transition: all 0.22s var(--ease-out);
}

.input-field:focus {
    border-color: var(--color-primary);
    background: var(--color-surface);
    box-shadow: 0 0 0 6rpx var(--color-primary-light);
}

.login-action,
.wechat-btn {
    height: 96rpx !important;
    line-height: 96rpx;
    font-size: 32rpx;
    font-weight: 700;
    border: none;
    border-radius: 48rpx;
    width: 100%;
    text-align: center;
    transition: all 0.22s var(--ease-out);
    letter-spacing: 2rpx;
    position: relative;
    overflow: hidden;
}

.login-action {
    margin-top: 12rpx;
    background: var(--color-primary-gradient);
    color: #FFF;
    box-shadow: 0 12rpx 28rpx rgba(37, 99, 235, 0.30);
}

.login-action:active {
    transform: scale(0.96);
    box-shadow: 0 6rpx 16rpx rgba(37, 99, 235, 0.25);
}

.login-action[disabled],
.wechat-btn[disabled] {
    opacity: 0.45;
    transform: none;
}

.form-link {
    margin-top: 24rpx;
    text-align: center;
    color: var(--color-primary);
    font-size: 26rpx;
    font-weight: 700;
}

.divider { display: flex; align-items: center; margin: 36rpx 0; }
.divider-line { flex: 1; height: 2rpx; background: var(--color-border); }
.divider-text {
    padding: 0 24rpx;
    font-size: 24rpx;
    color: var(--color-text-tertiary);
    font-weight: 500;
}

.wechat-btn {
    background: linear-gradient(135deg, #07C160, #06AD56);
    color: #FFF;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 10rpx;
    box-shadow: 0 8rpx 20rpx rgba(7, 193, 96, 0.25);
}

.wechat-btn:active {
    transform: scale(0.96);
    box-shadow: 0 4rpx 12rpx rgba(7, 193, 96, 0.2);
}

.wechat-dot { font-size: 34rpx; }

.footer-text {
    margin-top: 44rpx;
    font-size: 24rpx;
    color: rgba(255, 255, 255, 0.62);
    text-align: center;
    letter-spacing: 1rpx;
    animation: fadeIn 0.8s 0.3s var(--ease-out) backwards;
}
</style>
