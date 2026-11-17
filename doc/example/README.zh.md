# 实用模板示例

从配置文件和邮件入手，按“模板文件、输入数据、渲染结果”对照使用。
模板保留 `.properties`、`.yaml`、`.conf`、`.html`、`.txt` 扩展名，便于直接编辑和预览。

| 场景 | 模板文件 | 主要用法 |
|:--|:--|:--|
| [Properties 配置](#properties-配置) | [application.properties](application.properties) | 捕获组替换值，保留配置键和下游占位符 |
| [YAML 可选配置](#yaml-可选配置) | [application.yaml](application.yaml) | WHEN 控制整段配置 |
| [Nginx 后端列表](#nginx-后端列表) | [upstream.conf](upstream.conf) | EACH 重复生成配置行 |
| [HTML 欢迎邮件](#html-欢迎邮件) | [welcome.html](welcome.html) | 普通文本预览、条件段和 HTML 转义函数 |
| [纯文本待办摘要](#纯文本待办摘要) | [digest.txt](digest.txt) | 循环序号、空列表分支、公共落款 |

`HI-MEEPO` 声明当前文件的注释边界。指令放在注释中，示例值写在原来的正文位置。
解析时选中替换位置，渲染时从 Map 取值。以下示例均使用默认的 map 引擎。

## 调用方式

以下代码可放入使用 Meepo 的 Java 项目，在本仓库根目录运行。它会读取本目录的 Properties 模板并打印结果。

```java
import pro.fessional.meepo.Meepo;
import pro.fessional.meepo.sack.Gene;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class RenderExample {
    public static void main(String[] args) {
        Map<String, Object> model = new HashMap<>();
        model.put("appName", "invoice-worker");
        model.put("port", 9080);
        model.put("baseUrl", "https://api.example.test");

        Gene gene = Meepo.parse(new File("doc/example/application.properties"), StandardCharsets.UTF_8);
        String result = gene.merge(model);
        System.out.print(result);
    }
}
```

其余示例使用各节的 `model`，并将文件路径替换为相应模板。Gene 可解析一次，多次传入不同数据渲染。
模板发生修改后，可以用 `Meepo.parse(file, StandardCharsets.UTF_8, 0)` 重新读取和解析。

需要写文件时，可将上面的字符串输出改为字节流输出，相关导入和代码如下。

```java
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

try (OutputStream output = Files.newOutputStream(Paths.get("application.generated.properties"))) {
    gene.merge(model, output, StandardCharsets.UTF_8);
}
```

将 `main` 方法声明改为 `public static void main(String[] args) throws IOException`，即可运行该片段。
`parse` 的字符集负责读取模板，`merge` 的字符集负责输出字节。
本目录全部文件采用 UTF-8；HTML 邮件中的 `<meta charset="UTF-8">` 与示例输出编码一致。

## Properties 配置

输入数据见上面的完整 Java 示例。模板见 [application.properties](application.properties)。

关键指令如下。

```properties
# RNA:USE #^server\.port=(.*)$#port#
server.port=8080
```

`(.*)` 是第一个捕获组，选中等号后面的示例值。渲染时用 `model` 中的 `port` 替换这个值。
`^`、`$` 在默认匹配规则下表示行首、行尾，`\.` 匹配字面的点。
指令采用 `#` 作为字段分隔符，URL 中可以直接使用 `/`。

完整输出如下。

```properties
spring.application.name=invoice-worker
server.port=9080
service.base-url=https://api.example.test
spring.datasource.password=${DB_PASSWORD}
```

`${DB_PASSWORD}` 留给 Spring 配置解析器处理。Meepo 只替换指令选中的位置。

## YAML 可选配置

缓存段用以下注释包围。完整模板见 [application.yaml](application.yaml)。

```yaml
# RNA:WHEN /yes/cacheEnabled/cache
cache:
  address: "127.0.0.1:6379"
# RNA:DONE cache
```

`cacheEnabled` 是输入 key，最后的 `cache` 是控制组名。WHEN 与 DONE 使用相同组名。
传入 Boolean 值 `true` 时输出缓存段，`false` 时省略整个段。

输入数据如下。

```java
Map<String, Object> model = new HashMap<>();
model.put("appName", "invoice-worker");
model.put("databaseUrl", "jdbc:postgresql://db.example.test:5432/invoice");
model.put("cacheEnabled", true);
model.put("cacheAddress", "cache.example.test:6379");
```

输出如下。

```yaml
application:
  name: "invoice-worker"
database:
  url: "jdbc:postgresql://db.example.test:5432/invoice"
cache:
  address: "cache.example.test:6379"
```

将 `cacheEnabled` 改为 Boolean 值 `false` 后，输出如下。

```yaml
application:
  name: "invoice-worker"
database:
  url: "jdbc:postgresql://db.example.test:5432/invoice"
```

条件应传 Boolean 值。字符串 `"false"` 是非空字符串，在 Meepo 的真值规则中为真。

## Nginx 后端列表

这是用于 Nginx `http { ... }` 内 `include` 的 upstream 配置片段。模板见 [upstream.conf](upstream.conf)。

```nginx
upstream notification_backend {
    # RNA:EACH /1/backends/backend
    # RNA:USE /127\.0\.0\.1:8080/backend/
    server 127.0.0.1:8080;
    # RNA:DONE backend
}
```

EACH 的 `1` 表示正序逐项遍历，`backends` 是输入列表，`backend` 是当前条目的变量名和循环组名。
正文中只有一行 `server`，会按列表重复输出。

输入数据如下，需导入 `java.util.Arrays`。

```java
Map<String, Object> model = new HashMap<>();
model.put("backends", Arrays.asList("10.0.0.11:8080", "10.0.0.12:8080"));
```

输出如下。

```nginx
upstream notification_backend {
    server 10.0.0.11:8080;
    server 10.0.0.12:8080;
}
```

调用方应提供至少一个后端，且每项都是已校验的 `host:port` 配置值。

## HTML 欢迎邮件

示例姓名和链接写在正常的 HTML 元素中，可以直接打开模板查看邮件正文。模板见 [welcome.html](welcome.html)。
`trial` 控制试用说明，`displayName|html` 和 `startUrl|html` 使用本次输入中提供的转义函数。

输入数据如下，需导入 `java.util.function.Function`。

```java
Function<Object, String> html = value -> {
    if (value == null) return "";
    return value.toString()
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;");
};

Map<String, Object> model = new HashMap<>();
model.put("displayName", "林 <小米>");
model.put("startUrl", "https://docs.example.test/start?source=email&lang=zh-CN");
model.put("trial", true);
model.put("html", html);
```

输出如下。

```html
<!doctype html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <title>欢迎使用</title>
</head>
<body>
    <p>林 &lt;小米&gt;，您好！</p>
    <p><a href="https://docs.example.test/start?source=email&amp;lang=zh-CN">查看使用说明</a></p>
    <p>您的试用期为 14 天。</p>
</body>
</html>
```

`html` 是示例注册的 Java Function，负责 HTML 正文和双引号属性值的字符转义。Meepo 按求值结果原样输出。
链接地址由应用提供完整的 HTTPS URL。邮件主题、收件人和发送操作交给应用的邮件组件处理。
将 `trial` 改为 Boolean 值 `false`，即可生成普通用户的欢迎正文。

## 纯文本待办摘要

正文与公共落款分别保存在两个模板中。

- 待办摘要 [digest.txt](digest.txt)
- 公共落款 [signature.txt](signature.txt)
循环使用 `task.title`、`task.due` 读取当前条目的字段，`task._count` 给出从 1 开始的序号。
相同循环组名的 ELSE 提供空列表时的正文。

输入数据如下，需导入 `java.util.Arrays`。

```java
Map<String, Object> first = new HashMap<>();
first.put("title", "确认采购清单");
first.put("due", "2026-11-14");

Map<String, Object> second = new HashMap<>();
second.put("title", "归档本周发票");
second.put("due", "2026-11-15");

Map<String, Object> model = new HashMap<>();
model.put("reportDate", "2026-11-13");
model.put("recipient", "小林");
model.put("tasks", Arrays.asList(first, second));
model.put("teamName", "财务支持团队");
```

输出如下。

```text
待办摘要（2026-11-13）

小林，您好！

1. 确认采购清单（截止 2026-11-14）
2. 归档本周发票（截止 2026-11-15）
—— 财务支持团队
```

将 `tasks` 改为 `Collections.emptyList()`，并导入 `java.util.Collections`，输出如下。

```text
待办摘要（2026-11-13）

小林，您好！

当前没有待办事项。
—— 财务支持团队
```

父模板中的 `# DNA:SON ./signature.txt` 在解析时按父模板位置读取落款。通过 `Meepo.parse(File, Charset)` 解析，
即可获得相对路径所需的来源信息。落款文件声明自己的 `HI-MEEPO`，渲染时共享本次 `model`，因此可以读取 `teamName`。
修改落款后需重新解析父模板。

## 修改示例时

替换字段使用正则。示例默认替换首次匹配，若相同位置特征需要替换多次，在第三个分隔符后紧贴 `*`。
例如 `# RNA:USE /示例用户/recipient/*` 会替换后续所有匹配的“示例用户”。
指令要放在目标正文前面，普通独占行指令在输出时会连同缩进和换行移除。

替换值按原文写入，配置中的引号、换行和转义由调用方按目标格式准备。更多规则见[语法规范](../syntax.zh.md)。
