# JMH 性能比较执行 Prompt

把下面的提示词交给编码助手，在仓库根目录执行。JMH 是正式拼写，对应 `pro.fessional.meepo.jmh` 测试包。
提示词描述每次重新测量的流程；仓库不保存 benchmark 结果 JSON、日志、历史 class、源码基线补丁或报告生成脚本。
构建临时文件使用忽略目录 `target/`，每次构建更新；测量结果直接输出并在本次答复中解释，不作为下一次运行的缓存。

```text
请在当前 Meepo 工作树执行一次可复核的 JMH 性能比较，并在本次答复中给出结果。

先阅读：
- pom.xml：所有比较引擎和 JMH 依赖必须在根 POM 的 test scope，版本以此为准。
- src/test/java/pro/fessional/meepo/jmh：Java 8 基准、旧股票模型、输出一致性测试、分配/复制探针。
- src/test/jdk17/pro/fessional/meepo/jmh：TemplateComparison、AllocationProbe、完整比较输出测试及 TopTemplateEnginesTest。
- src/test/jdk17/pro/fessional/meepo/util：不含 javac 的真实运行时测试，与基准计时无关。
- src/test/jdk8/pro/fessional/meepo/util：Java 8 独立 JRE 测试，自动复制并清理临时运行时，与基准计时无关。
- src/main/java/pro/fessional/meepo/Meepo.java：历史测量摘要，仅作背景。

范围：
1. 先编译并验证正确性，再计时。JDK 8 使用内置 Nashorn；JDK 15+ 的 nashorn-jdk15 profile 自动添加 test scope 的独立 Nashorn。现代 JDK 同样执行全量测试。JMustache 1.16 发布包是 Java 9 字节码，jte 要用 Java 17，二者的比较测试统一用完整 JDK 17+。
2. TemplateComparison 对比 Meepo、jte、JMustache、Mustache.java、FreeMarker、Pebble。
   不把 POM 中旧的固定版本称作当前市场最新版；要研究新版本，先核验官方版本、调整 test 依赖并重新验证。
3. 不添加只用反射掩盖缺失依赖的候选，不把未运行成功的引擎写入排名。
4. 不读取旧的 JMH 数据当作这次结果。不创建 benchmark/results 或提交任何执行结果。
5. 不新增持久化的 Python/Shell 执行脚本。可用临时命令编排，本次完成后删除临时测量文件。

构建及正确性：
- 切换 JDK 时先 clean，避免 Java 17 测试 class 被 Java 8 误用。
- 完整 JDK 8：./mvnw -Pcoverage clean verify -Dmaven.javadoc.skip=true -Dmaven.source.skip=true
- 完整 JDK 17+：./mvnw clean test
- TemplateComparisonTest 检查六个引擎 × 四种负载 × 四种接口，共 96 个组合。
- 输出必须逐字符、逐字节相同，含中文、emoji、原始 <&>；不得通过 trim、空白折叠或预计算模型值制造一致。
- 缺依赖、版本不兼容或网络失败时报告实际原因，不偷偷换版本或把替代源码构建说成发行二进制。
- 用正式发布 JAR 验证其 class 字节码版本；不能通过把源码重新编译为 Java 8 来推断发布 JAR 支持 Java 8。

建立实际测试 classpath：
./mvnw -DskipTests test-compile org.apache.maven.plugins:maven-dependency-plugin:3.7.0:build-classpath \
  -DincludeScope=test -Dmdep.outputFile=target/jmh-classpath.txt

POSIX 环境示意：
jmh_cp="target/test-classes:target/classes:$(cat target/jmh-classpath.txt)"

先列出 JMH 元数据，确认改包后没有残留旧 benchmark 名称：
java -cp "$jmh_cp" org.openjdk.jmh.Main -l

正式热渲染：
java -cp "$jmh_cp" org.openjdk.jmh.Main \
  '^pro.fessional.meepo.jmh.TemplateComparison.render$' \
  -p engine=meepo,jte,jmustache,mustache,freemarker,pebble \
  -p workload=static,placeholder,rows20,rows200 \
  -p api=native,string,writer,utf8 \
  -t 1 -wi 3 -w 1s -i 5 -r 1s -f 2 -prof gc -jvmArgs '-Xms256m -Xmx256m'

首次创建/编译与输出必须单独运行，不与热渲染混算：
java -cp "$jmh_cp" org.openjdk.jmh.Main \
  '^pro.fessional.meepo.jmh.TemplateComparison.compileAndFirstRender$' \
  -p engine=meepo,jte,jmustache,mustache,freemarker,pebble \
  -p workload=static,placeholder,rows20,rows200 -p api=string \
  -t 1 -wi 1 -i 3 -f 2 -jvmArgs '-Xms256m -Xmx256m'

如果环境禁止 JMH fork 所需的监听端口：
- 不改成一个进程里测完所有引擎后声称进程隔离。
- 对每个参数组合分别启动独立外部 JVM，JMH 参数用 -f 0；外部重复至少两轮。
- 第二轮逆序，交错 Meepo before/current；不同时做编译、测试或另一组基准。
- 短测只用来验证 runner 能工作，不拿 100ms、零预热的 smoke 数字作性能结论。

比较口径：
- setup 中解析/编译模板；计时区只做指定的完整输出工作。
- native 使用各自默认字符串入口；string/writer 固定初始容量 4096。TemplateComparison 的 writer 返回已经写完的 StringWriter，计时不包含 toString() 快照。旧 EngineComparison 的 writer 包含快照，二者分别解释。
- utf8 返回调用方独占的完整 byte[]；Meepo 由测试适配器收集 `merge(ctx, OutputStream, Charset)` 的输出，jte 开启 binaryStaticContent。
- Meepo UTF-8 容量为 4096 字节，直接调用当前 merge(ctx, OutputStream, Charset)；jte 默认 512 个 chunk 槽位，不能解释为相同容量。
- 没有字节接口的引擎用完整 String 再编码；在表中说明这部分成本。
- 大小写变换与条件判断保留在渲染过程中；Mustache 的真假 section 可能各调用一次谓词。
- 静态 String 返回已有不可变对象的缓存命中与实际复制输出分别说明。
- @Setup 的输出检查和本次渲染中的合法模板缓存是算法的一部分；“不缓存执行结果”指不保存/复用测量数据。

共享模板的多线程分析使用 MeepoConcurrency。

- Scope.Benchmark 共享一个 Gene，Scope.Thread 为每个工作线程建立独立模型，输出缓冲区每次新建。所有线程使用相同的分支条件、行数和输出形状，仅请求标识不同。
- workload=placeholder,rows20,rows200，api=string,writer,utf8,mixed，分别运行 -t 1、2、4、8，其他热渲染参数一致。
- mixed 每次在 UTF-8 与 GBK 之间交替；它同时改变编码成本和单项字节缓存的命中情况，结合 B/op 解释。
- 报告总 ops/s、相对于 1 线程的扩展倍数和每线程效率。不要把每线程独立模板的 TemplateComparison 解释为共享模板测试。
- 单独做 JFR 或其他采样分析，采样运行的吞吐量不混入正式排名。

如需修改前后对照：
- 先明确 baseline 的可识别 Git commit/tag，记录当前工作树是否有未提交改动。
- 不再使用已经删除的 reviewed/source.patch 或历史 class 缓存。
- 在临时目录构建 baseline 生产代码，复用同一份当前测试 harness 和依赖，只替换生产 class。
- baseline 也先通过输出检查；当前 TemplateComparison 直接调用字节输出 API，旧版本缺少 API 时需要在临时基线 harness 中明确适配方式，不能静默换成不同输出契约。
- 不自动改动用户的工作树或 Git index；结束后删除临时基线构建。

如需分配量：
- fork 可用时使用合适的 JMH profiler；-f 0 时不要引用已经退出的渲染线程的无效计数。
- 可执行 pro.fessional.meepo.jmh.AllocationProbe ENGINE WORKLOAD API ITERATIONS，计数发生在渲染线程退出前。
- 该探针先预热 50000 次，返回 B/op；计数开销与短小对象误差需要解释。
- StringCopyExperiment 只作字符串复制实验，不与端到端模板吞吐排行混用。

本次答复需报告：
- 日期、提交/工作树状态、CPU/OS、JDK/VM、实际依赖来源与版本、堆、线程、预热、测量和独立 JVM 数。
- 按同一 workload/API 比较，不把不同输出接口混成一个普遍排名。
- ops/s 越大越好；B/op、ms/op 越小越好。
- Mean ops/s 是 JVM 均值的算术平均；JVM means min–max 是 JVM 均值范围，不是请求延迟区间或置信区间。
- 范围重叠和轮次不一致的微小差异不宣称稳定提速；保留较慢场景。
- 若只验证编译/正确性或 runner，明确说明没有执行正式性能测量。
- 默认将结果留在本次答复。用户明确要求更新 benchmark 文档时，将条件、汇总表和限制写入现有文档；原始 JSON、日志和 JFR 仍使用临时目录，结束后清理。Javadoc 摘要单独按用户要求处理。
```
