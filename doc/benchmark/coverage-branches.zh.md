# 测试覆盖与边界

## 覆盖门槛

`coverage` profile 在 `verify` 阶段要求全部生产代码的行、分支、指令、方法和类覆盖率分别达到 100%。
HTML 报告位于 `target/site/jacoco/index.html`，XML 报告位于同目录的 `jacoco.xml`。

`&&`、`||` 中的每个条件都可能产生字节码分支。覆盖率用于确认执行路径，输入组合、并发调度和异常恢复由结果断言验证。

## 测试命名与断言

- 单类用例采用对应生产类名加 `Test`，放在相同包中。跨类集成、运行时环境和安全用例按场景命名。
- `TraceTest` 提供日志基类，`TestingHelper`、`*Probe` 提供测试辅助或独立子 JVM 入口。JMH 类位于 `jmh` 包，对应的正确性用例以 `Test` 结尾。
- 输出断言覆盖完整字符或字节结果，编码用例以整串 JDK 编码结果为对照。
- 时间相关用例使用固定日期或调用前后的时间区间。时区、区域设置在测试结束时恢复。
- 并发用例使用门闩或屏障协调开始时机，并对等待与结果获取设置超时。

## 边界与正确性范围

| 范围 | 结果约束 | 主要测试 |
|:--|:--|:--|
| 输入、输出与所有权 | 输入解码和输出编码分别由调用方指定，调用方 Map 与输出流的生命周期可独立管理 | `ApiCoverageTest`、`MeepoTest`、`CharsetTemplateTest`、`PublicApiCompatibilityTest` |
| 字符片段与编码状态 | 空片段、孤立代理字符、跨片段代理对、编码切换、BOM 与有状态编码均匹配整串编码结果 | `UtxtTest`、`UbufTest` |
| 静态缓存 | 首次字节输出、软引用清除和并发读取输出一致，缓存保存完整字符集与字节快照 | `GeneTest`、`BranchSemanticsTest` |
| 循环 | Collection、数组和有限游标的正反向、步长、空输入、null 元素与计数元数据一致，支持整数极值步长 | `RnaEachTest`、`LifeTest`、`EachTest` |
| 控制流 | 未选中分支遵守短路，空边界 RUN 执行一次副作用，DONE 清理相应控制组 | `ControlShortCircuitTest`、`BranchSemanticsTest`、`ParserTest` |
| 函数与属性 | 原始函数键与前缀别名保留优先级，未命中和异常按 mute 处理，不缓存请求值 | `MapEngineTest`、`MapHelperTest` |
| 哈希与相等性 | 同 hash 的不同表达式或引擎类型保持独立，缓存键符合 equals/hashCode 契约 | `RngCheckerTest` |
| 解析器边界 | EOF、空白、非法头部、行内正文、嵌套组、BKB、单字符 token 与扩展节点符合语法约定 | `ParserTest`、`HolderTest` |
| 正则替换 | 整段匹配、捕获组和未参与匹配的可选组输出正确 | `DnaSetTest`、`RegressionTest` |
| 编译器环境 | 缺少 javac 时给出明确错误，运行时编译禁用隐式注解处理器 | `CompilerRuntimeTest`、`CompilerEnvironmentTest`、`CompilerSecurityTest` |
| 模型与资源 | 模型文本按数据输出，私有模型字段、资源路径、包含环和命令参数边界按宿主约定处理 | `ModelDataSecurityTest`、`ResourceSecurityTest`、`ArgumentBoundaryTest` |
| 输出失败 | Writer、字节写入及编码结束阶段的失败传播为渲染错误 | `WriterFailureTest`、`GeneTest`、`UbufTest` |

缺少编译器的测试使用真实运行时。Java 8 从 JDK 分离 JRE，Java 17+ 使用包含 `java.compiler`、不含 `jdk.compiler` 的 jlink 运行时。
固定种子随机用例覆盖编码分片和 Life 区间，区间结果与独立集合模型对照。

## 并发约束

完成解析的 Gene 及其节点按只读约定发布。每次 merge 创建独立的 Acid，分别管理变量、引擎映射和分支状态。可变引擎通过 fork 提供独立实例，无状态引擎可以共享。
自定义函数、嵌套模型对象、输入游标、Writer 与输出流遵循各自的并发使用约定。

- `GeneTest` 以 8 个线程同步共享包含 EACH、WHEN 与替换节点的模板，核验不同请求标识、正反分支和多字符集输出。
- `UtxtTest`、`UbufTest`、`MapEngineTest` 验证软缓存切换、编码输出及不同模型类型的并发读取。
- `AcidTest` 验证可变引擎的 fork 隔离、同一执行内的引擎复用，以及不同执行上下文的独立清理。
- `RnaManagerTest` 验证进行中的 merge 使用已捕获的函数快照，后续 merge 使用新注册的函数。

## 运行环境

| 环境 | 测试支持 |
|:--|:--|
| Java 8 | 使用内置 Nashorn，`jre-jdk8` 添加独立 JRE 用例 |
| JDK 15+ | `nashorn-jdk15` 添加 test scope 的独立 Nashorn，支持完整 JS 用例 |
| JDK 17+ | `jmh-jdk17` 添加完整引擎比较与现代运行时环境用例，运行时环境用例需要 jlink |

复现命令、跨 JDK 构建和报告管理见 [校验 Prompt](../prompt/jmh.prompt.md)。
阶段测试数量、缺陷修复和分支调整依据见 [测试迭代记录](../changelog/2026-11-13-tests.zh.md)。
