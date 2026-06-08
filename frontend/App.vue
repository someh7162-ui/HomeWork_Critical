<script>
export default {
    onLaunch() {
        console.log('智阅教育 App Launch')
    }
}
</script>

<style>
/* ========== 智阅教育 全局设计系统 v2 ========== */

/* 设计令牌 */
page {
    --color-primary: #2563EB;
    --color-primary-dark: #1D4ED8;
    --color-primary-soft: #DBEAFE;
    --color-primary-light: #EFF6FF;
    --color-primary-gradient: linear-gradient(135deg, #1D4ED8, #38BDF8);

    --color-accent: #D97706;
    --color-accent-light: #FFF7ED;
    --color-ink: #0F172A;
    --color-mint: #0F766E;
    --color-mint-light: #CCFBF1;

    --color-subject-cn: #9333EA;
    --color-subject-cn-light: #F3E8FF;
    --color-subject-en: #2563EB;
    --color-subject-en-light: #DBEAFE;
    --color-subject-math: #D97706;
    --color-subject-math-light: #FEF3C7;
    --color-subject-all: #059669;
    --color-subject-all-light: #D1FAE5;

    --color-success: #059669;
    --color-success-light: #D1FAE5;
    --color-success-gradient: linear-gradient(135deg, #059669, #34D399);
    --color-danger: #DC2626;
    --color-danger-light: #FEE2E2;
    --color-warning: #D97706;
    --color-warning-light: #FEF3C7;

    --color-bg: #F5F7FB;
    --color-surface: #FFFFFF;
    --color-surface-muted: #F8FAFC;
    --color-text-primary: #0F172A;
    --color-text-secondary: #475569;
    --color-text-tertiary: #94A3B8;
    --color-text-placeholder: #CBD5E1;
    --color-border: #DDE5F0;
    --color-divider: #F1F5F9;

    --space-xs: 8rpx;
    --space-sm: 12rpx;
    --space-md: 16rpx;
    --space-lg: 24rpx;
    --space-xl: 32rpx;
    --space-2xl: 40rpx;
    --space-3xl: 48rpx;

    --radius-sm: 8rpx;
    --radius-md: 12rpx;
    --radius-lg: 16rpx;
    --radius-xl: 20rpx;
    --radius-2xl: 28rpx;
    --radius-full: 9999rpx;

    --shadow-xs: 0 1rpx 3rpx rgba(15, 23, 42, 0.04);
    --shadow-sm: 0 2rpx 8rpx rgba(15, 23, 42, 0.05);
    --shadow-md: 0 10rpx 28rpx rgba(15, 23, 42, 0.07);
    --shadow-lg: 0 18rpx 48rpx rgba(15, 23, 42, 0.10);
    --shadow-xl: 0 24rpx 64rpx rgba(15, 23, 42, 0.14);

    --ease-out: cubic-bezier(0.16, 1, 0.3, 1);
    --ease-in-out: cubic-bezier(0.4, 0, 0.2, 1);
    --ease-spring: cubic-bezier(0.34, 1.56, 0.64, 1);

    background-color: var(--color-bg);
    font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Helvetica Neue', 'Segoe UI', sans-serif;
    font-size: 28rpx;
    color: var(--color-text-primary);
    line-height: 1.6;
}

/* ========== 动画关键帧 ========== */
@keyframes fadeInUp {
    from { opacity: 0; transform: translateY(20rpx); }
    to { opacity: 1; transform: translateY(0); }
}
@keyframes fadeIn {
    from { opacity: 0; }
    to { opacity: 1; }
}
@keyframes scaleIn {
    from { opacity: 0; transform: scale(0.92); }
    to { opacity: 1; transform: scale(1); }
}
@keyframes shimmer {
    0% { left: -100%; }
    100% { left: 200%; }
}
@keyframes spin {
    to { transform: rotate(360deg); }
}
@keyframes pulse-glow {
    0%, 100% { box-shadow: 0 0 0 0 rgba(37, 99, 235, 0.3); }
    50% { box-shadow: 0 0 0 12rpx rgba(37, 99, 235, 0); }
}
@keyframes bounce-in {
    0% { transform: scale(0.3); opacity: 0; }
    50% { transform: scale(1.05); }
    70% { transform: scale(0.95); }
    100% { transform: scale(1); opacity: 1; }
}
@keyframes slideInRight {
    from { opacity: 0; transform: translateX(30rpx); }
    to { opacity: 1; transform: translateX(0); }
}

/* ========== 通用卡片 ========== */
.card {
    background: var(--color-surface);
    border-radius: var(--radius-lg);
    padding: var(--space-lg);
    box-shadow: var(--shadow-xs);
    margin-bottom: var(--space-md);
    border: 1rpx solid rgba(221, 229, 240, 0.9);
    transition: all 0.25s var(--ease-out);
    animation: fadeInUp 0.4s var(--ease-out) backwards;
}
.card:active {
    transform: scale(0.985);
    box-shadow: var(--shadow-md);
}

/* ========== 按钮系统 ========== */
.btn-primary {
    width: 100%;
    height: 88rpx;
    background: var(--color-primary-gradient);
    color: #FFF;
    border-radius: var(--radius-full);
    font-size: 32rpx;
    font-weight: 600;
    border: none;
    display: flex;
    align-items: center;
    justify-content: center;
    transition: all 0.22s var(--ease-out);
    box-shadow: 0 10rpx 24rpx rgba(37, 99, 235, 0.22);
    position: relative;
    overflow: hidden;
}
.btn-primary:active {
    transform: scale(0.96);
    box-shadow: 0 4rpx 12rpx rgba(37, 99, 235, 0.18);
}
.btn-primary::after {
    content: '';
    position: absolute;
    inset: 0;
    background: linear-gradient(180deg, rgba(255,255,255,0.1), transparent);
    pointer-events: none;
}
.btn-primary[disabled] {
    opacity: 0.45;
    transform: none;
    box-shadow: none;
}

.btn-secondary {
    width: 100%;
    height: 88rpx;
    background: var(--color-surface);
    color: var(--color-primary);
    border-radius: var(--radius-full);
    font-size: 32rpx;
    font-weight: 600;
    border: 2rpx solid var(--color-primary-soft);
    display: flex;
    align-items: center;
    justify-content: center;
    transition: all 0.22s var(--ease-out);
}
.btn-secondary:active {
    transform: scale(0.96);
    border-color: var(--color-primary);
    box-shadow: 0 0 0 6rpx var(--color-primary-light);
}

/* ========== 科目标签 ========== */
.badge-subject {
    font-size: 20rpx;
    padding: 4rpx 14rpx;
    border-radius: var(--radius-full);
    font-weight: 600;
    letter-spacing: 1rpx;
    transition: all 0.2s;
}
.badge-subject-en { background: var(--color-subject-en-light); color: var(--color-subject-en); }
.badge-subject-cn { background: var(--color-subject-cn-light); color: var(--color-subject-cn); }
.badge-subject-math { background: var(--color-subject-math-light); color: var(--color-subject-math); }

/* ========== 增强卡片 ========== */
.card-accent {
    border-left: 6rpx solid var(--color-primary);
    transition: border-color 0.3s;
}
.card-accent-en { border-left-color: var(--color-subject-en); }
.card-accent-cn { border-left-color: var(--color-subject-cn); }
.card-accent-math { border-left-color: var(--color-subject-math); }

/* ========== 状态标签 ========== */
.tag-row {
    display: flex;
    flex-wrap: wrap;
    gap: 8rpx;
    align-items: center;
}
.badge {
    font-size: 22rpx;
    padding: 6rpx 16rpx;
    border-radius: var(--radius-full);
    font-weight: 500;
    transition: all 0.2s;
}
.badge-success { background: var(--color-success-light); color: var(--color-success); }
.badge-warning { background: var(--color-warning-light); color: var(--color-warning); }
.badge-danger { background: var(--color-danger-light); color: var(--color-danger); }
.badge-primary { background: var(--color-primary-light); color: var(--color-primary); }

/* ========== 空状态 ========== */
.empty-state {
    text-align: center;
    padding: 120rpx 0;
    color: var(--color-text-tertiary);
    animation: fadeIn 0.5s var(--ease-out);
}
.empty-state-icon {
    font-size: 80rpx;
    display: block;
    margin-bottom: var(--space-md);
    opacity: 0.4;
}
.empty-state-card {
    text-align: center;
    padding: 80rpx var(--space-xl);
    background: var(--color-surface);
    border-radius: var(--radius-lg);
    border: 1rpx dashed var(--color-border);
    animation: fadeIn 0.5s var(--ease-out);
}

/* ========== 输入框 ========== */
.input-field {
    width: 100%;
    height: 88rpx;
    background: var(--color-surface-muted);
    border-radius: var(--radius-md);
    padding: 0 var(--space-lg);
    font-size: 30rpx;
    color: var(--color-text-primary);
    box-sizing: border-box;
    border: 2rpx solid transparent;
    transition: all 0.22s var(--ease-out);
}
.input-field:focus {
    border-color: var(--color-primary);
    background: var(--color-surface);
    box-shadow: 0 0 0 6rpx var(--color-primary-light);
}

/* ========== 标签与布局 ========== */
.input-label {
    display: block;
    font-size: 28rpx;
    font-weight: 600;
    color: var(--color-text-primary);
    margin-bottom: var(--space-sm);
}
.page-container {
    padding: var(--space-lg) var(--space-xl);
    padding-bottom: 40rpx;
    min-height: 100vh;
    box-sizing: border-box;
}
.surface-panel {
    background: var(--color-surface);
    border: 1rpx solid var(--color-border);
    border-radius: var(--radius-lg);
    box-shadow: var(--shadow-xs);
}
.section-title {
    font-size: 34rpx;
    font-weight: 700;
    color: var(--color-text-primary);
    margin-bottom: var(--space-lg);
    padding: 0 var(--space-xs);
    letter-spacing: 0;
}
.header-bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: var(--space-lg);
}
.header-bar .section-title { margin-bottom: 0; }
.section-sub { font-size: 24rpx; color: var(--color-text-tertiary); }

.metric-label {
    font-size: 22rpx;
    color: var(--color-text-tertiary);
    font-weight: 600;
}
.soft-divider {
    height: 1rpx;
    background: var(--color-divider);
}
.required { color: var(--color-danger); }

/* ========== 骨架屏 ========== */
.skeleton {
    background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%);
    background-size: 200% 100%;
    animation: skeleton-loading 1.5s infinite;
    border-radius: var(--radius-sm);
}
@keyframes skeleton-loading {
    0% { background-position: 200% 0; }
    100% { background-position: -200% 0; }
}
</style>
