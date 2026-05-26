# Nokia N1 Braun T3 风格信息终端

这个模型把 Nokia N1 当作“数字时代的显示机芯”，而不是一台裸露平板。整体定位是：Braun T3 / Rams 语言下的桌面信息终端，可用于音乐播放、时钟、Codex monitor、Agent 状态中心和 ambient dashboard。

## 设计逻辑

- 左侧是实体呼吸区：规则网孔、留白、低视觉负担。
- 中间是嵌入式信息窗口：用厚 bezel 遮住平板黑边，让屏幕像设备的原生组件。
- 右侧是实体控制区：一个大旋钮和三个小按钮，为数字界面提供物理锚点。
- 外壳保持厚度和倾角，避免做成廉价平板支架。
- 材质建议使用哑光白作为主色，旋钮和按钮也保持白色或微磨砂浅灰，不建议高亮、透明或 RGB。

## 现实尺寸约束

参考概念图里的 263 × 110 mm 无法直接容纳 Nokia N1，因为 N1 横屏实体高度是 138.6 mm。当前模型吸收了 `braun_nokia_n1.scad` 参考稿的整机比例，把外壳收敛到更接近 Braun 桌面设备的尺度：

- 外壳约：306 × 152 × 40 mm
- 平板：200.7 × 138.6 × 6.9 mm
- 中央可视窗口：164 × 110 mm，上下仍会遮挡一部分屏幕，但整体更接近设计稿比例
- 充电口：默认从视觉右侧走线，对应 Nokia N1 横屏时的 Type-C 方向
- 左侧网孔：13 × 26 阵列，并在下方加入 BRAUN 标识
- 右上角：加入 NOKIA N1 标识

## 文件

- OpenSCAD 源文件：`openscad/nokia_n1_braun_t3_status_dock.scad`
- 主体 STL：`build/nokia_n1_braun_t3_status_dock_shell.stl`
- 背面压片 STL：`build/nokia_n1_braun_t3_status_dock_clips.stl`
- 正面预览：`build/nokia_n1_braun_t3_status_dock_front.png`
- 3D 预览：`build/nokia_n1_braun_t3_status_dock_preview.png`

## 装配方式

1. 从背面放入 Nokia N1。
2. 用 M3 螺丝和打印压片固定平板边缘。
3. Type-C 线从视觉右侧开口接入，并沿背面走线槽向下走线。
4. 正面网孔、旋钮、小按钮目前是造型元素；后续可以改成真实旋钮编码器、按钮或外接音频孔。

## 打印建议

- 主体建议 PETG 或哑光 PLA。
- 层高 0.2 mm，墙线 3 道以上。
- 填充 15% 到 25%。
- 网孔较多，建议降低打印速度，避免小孔糊住。
- 如果要更像成品，可拆成白色外壳、黑色屏幕 bezel、银色旋钮三个零件分别打印或后处理。

## 下一步校准

- 用卡尺确认 Type-C 线头尺寸，必要时放大 `charge_slot_width` / `charge_slot_height`。
- 如果屏幕 UI 需要完整显示，把 `screen_window_width` / `screen_window_height` 稍微放大。
- 如果希望更 Rams、更克制，可以继续缩小 `screen_window_height`，让屏幕更像一条信息窗。
- 大旋钮后续可以预留编码器孔位，例如 EC11 或更大的铝合金旋钮模组。
