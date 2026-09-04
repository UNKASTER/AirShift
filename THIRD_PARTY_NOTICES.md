# 第三方声明

AirShift 包含或依赖以下第三方项目。各项目仍由其权利人持有版权，并按各自许可证授权。

## PaddleOCR Android SDK 与 PP-OCRv6 tiny ONNX 模型

- 来源：[PaddlePaddle/PaddleOCR](https://github.com/PaddlePaddle/PaddleOCR)
- Android SDK 源码位置：`app/src/main/java/com/paddle/ocr`
- 模型位置：`app/src/main/assets/models`
- Copyright (c) PaddlePaddle Authors
- 许可证：[Apache License 2.0](https://github.com/PaddlePaddle/PaddleOCR/blob/main/LICENSE)

SDK 源文件保留了上游版权与许可证头。模型使用 PaddleOCR 官方发布的 `PP-OCRv6_tiny_det_onnx_infer` 与 `PP-OCRv6_tiny_rec_onnx_infer`。

## ONNX Runtime Android

- 依赖：`com.microsoft.onnxruntime:onnxruntime-android:1.21.1`
- 来源：[microsoft/onnxruntime](https://github.com/microsoft/onnxruntime)
- 许可证：[MIT License](https://github.com/microsoft/onnxruntime/blob/main/LICENSE)

## OpenCV Android

- 依赖：`org.opencv:opencv:4.12.0`
- OpenCV 来源：[opencv/opencv](https://github.com/opencv/opencv)
- 许可证：[Apache License 2.0](https://github.com/opencv/opencv/blob/4.12.0/LICENSE)

## Apache POI

- 依赖：`org.apache.poi:poi:5.5.1`
- 来源：[apache/poi](https://github.com/apache/poi)
- 许可证：[Apache License 2.0](https://github.com/apache/poi/blob/trunk/LICENSE)

应用使用 Apache POI 的 HSSF 事件模型读取旧版 BIFF `.xls` 工作簿，不解析或持久化其中的图片对象。

## Barlow 字体

- 来源：[jpt/barlow](https://github.com/jpt/barlow)
- 文件位置：`app/src/main/res/font/barlow_*.ttf`（Barlow Regular/Medium/SemiBold/Bold，Barlow Semi Condensed SemiBold/Bold）
- Copyright 2017 The Barlow Project Authors
- 许可证：[SIL Open Font License 1.1](https://github.com/jpt/barlow/blob/main/OFL.txt)

应用内所有 Latin 字母与数字使用 Barlow；板面大数字、时钟、航班号与机位号使用 Barlow Semi Condensed，并启用 tabular figures。汉字仍由系统字体渲染。
