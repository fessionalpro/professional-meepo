# 跨语言语法一致性校验 Prompt

输入与预期值保存在 [cases.json](../conformance/cases.json)，它们是测试规格，不是执行结果缓存。
把下面的提示词交给编码助手，按当前实现重新校验，不保存验证回执或持久执行脚本。

```text
请校验 doc/conformance/cases.json 中的全部语法一致性用例。
先读 doc/syntax.zh.md；不要通过修改预期值掩盖实现差异。

Java 参考实现的准备：
mvn -DskipTests compile org.apache.maven.plugins:maven-dependency-plugin:3.7.0:build-classpath \
  -DincludeScope=compile -Dmdep.outputFile=target/conformance-classpath.txt

使用当前生产 class 与真实编译期依赖，分别在完整 JDK 8 和 JDK 21 运行。
可在系统临时目录生成 Java wrapper、通过 javac/java 编译执行；不提交生成代码、执行脚本或结果 JSON。
只需要 Python 标准库与 JDK，不给生产依赖增加 JSON 库。

每条用例：
1. mode=comment 时调用 Parser.parse，mode=placeholder 时调用 Holder.parse。
2. context 是 JSON Map/List/字符串/Boolean/null/数值。保留类型，不把对象预先字符串化。
3. holder 可配置 prefix/suffix/escape；默认 {{、}}、空转义串；lax 默认 false。
4. source 是已经解码的字符文本，HI-MEEPO 不承载编码声明。files 是相对子资源内容，
   按调用方输入字符集写入临时目录，省略时为 UTF-8；解析时传 main.tpl 为来源路径，
   保持 DNA:SON 的相对路径及包含环语义。字节文件和流的输入解码另由 CharsetTemplateTest 验证。
5. expected 要逐字符相等，不 trim，不折叠空白。
6. error.phase=parse/merge 必须在对应阶段失败；宿主异常类名不要求逐字相同。
7. 对成功用例使用同一解析计划至少重复 merge 两次；通过 ByteArrayOutputStream 收集
   merge(ctx, outputStream, charset) 的输出，字节等于 expected 按该调用方字符集编码的结果；默认 UTF-8。
8. 检查模板赋值没有修改调用方 Map；输入嵌套对象仍遵循浅复制合同。
9. java-utf16 用例中的单独代理字符要保留 UTF-16 意义，不在 JSON 读取阶段提前替换。
10. 每条打印 PASS/FAIL，失败输出 id、阶段与期望/实际差异；本次末尾汇总数量和 JDK 版本。

生成 Java 调用代码时必须正确编码数据字面量，不用宿主 eval 读取 JSON。
校验结束后删除临时 wrapper/class/资源目录；结果只保留在本次答复。
不要生成 validation-jdk*.json、源码哈希回执或把旧输出当成重新验证。

若校验 Go/Python/JS 实现，读取相同用例；先声明支持的 profile。
core、java-edges、java-utf16 是用例范围标签，不表示已经穷尽全部语言行为。
遇宿主差异时报告差异并核对源码，不静默转换成宿主默认真值、正则或 Map 导航规则。
```
