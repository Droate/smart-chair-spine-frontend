#  基于大模型与云边端协同的智能座椅健康管家 (Android 端)

>  **提示**：本项目为《智能座椅健康管家》的 **Android 移动端/端侧感知部分**。
>  **配套的云端大模型 Agent 与 FastAPI 后端源码请移步至**：https://github.com/Droate/smart-chair-spine-backend

##  项目简介
本项目是针对数字化办公久坐健康痛点研发的 AIoT 全栈系统的移动端部分。主要负责通过手机摄像头进行无接触式的视觉姿态监测，管理多轮对话上下文，并通过蓝牙协议驱动底层硬件设备。

##  核心技术栈
* **UI 框架**: Kotlin, Jetpack Compose, Coroutines
* **视觉算法**: MediaPipe (面部网格与三维欧拉角提取)
* **通信协议**: 经典蓝牙 (RFCOMM), BLE, mDNS
* **其他**: CameraX, Room 数据库

##  核心亮点
* **高精度姿态识别**：弃用传统 2D 方案，基于 MediaPipe 提取头部三维欧拉角，并自主设计 EMA 滤波与防抖状态机，复杂环境识别准确率达 96.9%。
* **极致性能调优**：针对 CameraX 高频视频流设计背压丢帧策略，实现严格的内存闭环回收，杜绝 OOM。
* **端云协同隔离**：配合后端 LangChain 无状态设计，在端侧安全托管多轮对话与设备状态上下文。

```mermaid
graph TD
    %% 样式定义
    classDef client fill:#E1F5FE,stroke:#0288D1,stroke-width:2px;
    classDef server fill:#FFF3E0,stroke:#F57C00,stroke-width:2px;
    classDef ai fill:#E8F5E9,stroke:#388E3C,stroke-width:2px;
    classDef hardware fill:#F3E5F5,stroke:#7B1FA2,stroke-width:2px;

    %% --- 客户端 (Android 端) ---
    subgraph Client ["📱 边缘计算与状态托管端 (Android)"]
        direction TB
        UI["Jetpack Compose 3D 可视化 UI"]
        
        subgraph Perception ["端侧视觉感知"]
            Camera["CameraX 视频流采集\n背压防 OOM"]
            MediaPipe["MediaPipe 面部网格\n提取头部三维欧拉角"]
            Filter["EMA 滤波 & 防抖状态机\n准确率 96.9%"]
            Camera --> MediaPipe --> Filter
        end

        subgraph Context ["端侧上下文托管"]
            StateFlow["StateFlow 状态管理"]
            RoomDB[("Room 本地数据库\n存储多轮对话记忆")]
        end

        subgraph Comms ["硬件通信模块"]
            BLE["动态权限管理\n经典蓝牙/BLE 协议"]
            Delay["防拥塞时序控制"]
            BLE --> Delay
        end

        UI <--> Context
        Filter --> Context
    end

    %% --- 云端服务 (FastAPI) ---
    subgraph Server ["☁️ 云端微服务后端 (FastAPI)"]
        direction TB
        API["FastAPI 网关\nStateless 无状态设计"]
        mDNS["mDNS 局域网服务发现"]

        subgraph AgentEngine ["Agentic Workflow 执行引擎"]
            LCEL["LangChain LCEL 管道"]
            Pydantic["Pydantic 结构化输出\n降维解析 JSON 动作"]
        end

        subgraph KnowledgeBase ["垂直医疗知识库 RAG"]
            DashScope["DashScope Embedding 模型"]
            ChromaDB[("Chroma 本地向量数据库\n检索久坐指南")]
        end

        subgraph Models ["核心大模型"]
            DeepSeek(("DeepSeek LLM"))
            Sklearn["Scikit-learn 多输出回归\n个性化参数推荐"]
        end

        API --> AgentEngine
        AgentEngine <--> DeepSeek
        AgentEngine <--> KnowledgeBase
        API <--> Sklearn
    end

    %% --- 物理执行层 ---
    subgraph HardwareLayer ["🪑 物理执行层"]
        Simulator["数字孪生硬件模拟器"]
    end

    %% --- 跨边界交互链路 ---
    
    %% 1. HTTP 请求
    Context -- "1. HTTP POST\n自然语言意图+物理状态+多轮对话记忆" --> API
    
    %% 2. HTTP 响应
    API -- "2. HTTP Response\n动作序列 JSON + RAG 诊断报告" --> Context

    %% 3. 蓝牙驱动
    Context -- "3. 解析动作序列" --> Comms
    Comms -- "4. RFCOMM 蓝牙字节流\n串行驱动电机" --> Simulator

    %% 应用样式
    class Client client;
    class Server server;
    class AgentEngine,KnowledgeBase,Models ai;
    class HardwareLayer hardware;
```
