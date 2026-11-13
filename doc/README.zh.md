# Meepo 文档

## 规范与实现说明

- [语法与跨语言实现规范](syntax.zh.md) 包含 EBNF、注释扫描、DNA/RNA、map、占位符、输出和 Java 边界行为。
- [命名与执行流程](naming.zh.md) 说明英雄技能包名、生物学隐喻、类型职责、所有权与线程约束。
- [性能设计与比较结论](benchmark/optimization.zh.md) 包含输出口径、测量条件、相对排名、分配量与多线程结果。
- [测试覆盖与边界](benchmark/coverage-branches.zh.md) 说明覆盖门槛、测试命名、边界结果与并发约束。

字符集属于字节 I/O 边界。`Meepo.parse(file, inputCharset)` 指定输入编码，`Gene.merge(model, out, outputCharset)` 指定输出编码，默认 UTF-8。
解析结果持有字符文本，HI-MEEPO 使用目标语言的注释边界，具体规则见语法规范 §3 与 §10。

## 一致性校验与性能复现

- [一致性用例](conformance/cases.json) 保存模板、数据、预期输出与错误阶段。
- [一致性校验 Prompt](prompt/conformance.prompt.md) 用于校验 Java 或跨语言实现。
- [JMH 性能比较 Prompt](prompt/jmh.prompt.md) 说明构建、输出一致性门槛、计时、分配量与结果解释。

生产实现兼容 Java 8。JMH 依赖与比较引擎统一由根 POM 的 test scope 管理。基础测试和基准位于 `src/test/java`，
Java 8 运行时用例位于 `src/test/jdk8`，Java 17+ 比较与运行时用例位于 `src/test/jdk17`。

## 迭代记录

- [阶段审查与接口迁移](changelog/2026-11-13-review.zh.md)
- [测试复审与分支调整](changelog/2026-11-13-tests.zh.md)
- [性能测量与口径调整](changelog/2026-11-13-benchmark.zh.md)
- [项目变更记录](../changelog.md)

顶层文档描述当前规则和结论，按日期组织的审查、迁移与差异记录位于 `changelog/`，执行 Prompt 位于 `prompt/`。
性能文档保留测量条件与汇总数据，构建报告使用忽略目录 `target/`，原始测量文件在临时目录管理。
