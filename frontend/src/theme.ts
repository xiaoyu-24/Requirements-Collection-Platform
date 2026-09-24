import { theme as antdTheme } from 'ant-design-vue'
import type { ConfigProviderProps } from 'ant-design-vue'

/**
 * 全局主题配置（Ant Design Vue 4.x ConfigProvider token）。
 *
 * 设计目标：
 * - 统一主色 #1677ff（Ant Design 默认现代蓝）
 * - 内容区浅灰背景 #f5f7fa
 * - 现代圆角 8px（Card 10px）
 * - 深色侧栏 #001529 保留（经典中后台配色）
 * - 组件级 token 覆盖：Layout / Menu / Card / Table / Form
 *
 * 接入方式：在 App.vue 顶层包裹
 *   <a-config-provider :theme="themeConfig" :locale="zhCN">
 */
export const themeConfig: NonNullable<ConfigProviderProps['theme']> = {
  algorithm: antdTheme.defaultAlgorithm,
  token: {
    colorPrimary: '#1677ff',
    colorInfo: '#1677ff',
    colorSuccess: '#52c41a',
    colorWarning: '#faad14',
    colorError: '#ff4d4f',
    colorBgLayout: '#f5f5f5',
    borderRadius: 8,
    fontFamily:
      '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, "Noto Sans", sans-serif',
    fontSize: 14,
    wireframe: false,
  },
  components: {
    Layout: {
      headerBg: '#ffffff',
      headerHeight: 64,
      siderBg: '#ffffff',
      bodyBg: '#f5f5f5',
    } as any,
    Menu: {
      itemBg: '#ffffff',
      itemSelectedBg: '#e6f4ff',
      itemSelectedColor: '#1677ff',
      activeBarWidth: 3,
    } as any,
    Card: {
      borderRadiusLG: 8,
      boxShadowTertiary:
        '0 1px 2px 0 rgba(0, 0, 0, 0.03), 0 1px 6px -1px rgba(0, 0, 0, 0.02), 0 2px 4px 0 rgba(0, 0, 0, 0.02)',
    },
    Table: {
      headerBg: '#fafafa',
      headerColor: 'rgba(0, 0, 0, 0.88)',
      rowHoverBg: '#fafafa',
    } as any,
    Form: {
      itemMarginBottom: 16,
    } as any,
  },
}
