# Meepo 语法与跨语言实现规范

规范标识为 `meepo-language-1`，参考实现为本仓库的 Java 源码。

本文面向 Go、Python、JavaScript 等实现者，也可作为模板使用手册。
“必须”表示对应兼容级别需要保留的行为，“建议”表示便于移植的写法或架构选择。
EBNF 描述规范写法；动态注释边界、正则匹配和运行期语义由后续算法补足。
Java 扫描器的宽松写法与边界行为见 §13，移植实现应声明相应兼容范围。

## 1. 兼容范围与处理模型

Meepo 不解析目标语言的完整语法。它借用目标语言的注释包裹指令，保留模板本来的可读性。
同一文件既可以是可预览的 HTML，也可以包含 Meepo 的处理指令。

| 阶段 | 输入 | 处理 | 输出 |
|:--|:--|:--|:--|
| parse | 模板文本、可选来源 URI、引擎注册表 | 识别注释；执行 DNA；选定 RNA 替换位置；检查或预编译表达式；组装控制树 | 可复用模板计划，Java 中称 Gene |
| merge | 已解析计划、本次数据 Map | 求值 RNA；赋值、循环、分支；写出文本 | String、字符流或指定字符集的字节流 |

**匹配位置在 parse 时决定，值在 merge 时求出。** 对某个 WHEN 分支是否执行的判断，不能改变已解析好的替换位置。
DNA:SON 也是 parse 时包含，不能用运行期变量动态选择子模板。
输出文本不再次作为 Meepo 解析；不存在隐式递归展开或 HTML 转义。

建议移植实现声明以下能力，而不要用“完全兼容”掩盖宿主差异：

| 能力 | 内容 |
|:--|:--|
| 核心语言 | 注释声明、全部 DNA/RNA 指令、分组及生命周期、默认 map/raw、占位符、输出规则 |
| 正则能力 | 声明支持的模式、Unicode 与分组行为。Java 正则完整兼容是额外能力；Go RE2 等子集需对不支持的模式报错 |
| 宿主扩展 | uri、系统属性/环境变量、反射对象访问、自定义函数、java/fun/js/命令执行 |
| Java 边界兼容 | §13 的宽松扫描、异常输入、UTF-16 等行为 |

移植版可以把资源读取放入可注入 resolver，以支持 DNA:SON 而不依赖 Java classpath。
未提供的引擎必须明确拒绝相应模板，不能把 `java` 的表达式自动改当 Python/Go/JS 代码执行。

## 2. 字符、词法与 EBNF 约定

```ebnf
HWS          = " " | "\t" | "\r" ;
BLANK        = HWS | "\n" ;
LF           = "\n" ;
ASCII_LETTER = "A" … "Z" | "a" … "z" ;
DIGIT        = "0" … "9" ;
ALNUM        = ASCII_LETTER | DIGIT ;
WORD_CHAR    = ? 除 HWS 和 LF 之外的字符 ? ;
word         = WORD_CHAR, { WORD_CHAR } ;
engine       = ALNUM, { ALNUM } ;
integer      = [ "+" | "-" ], DIGIT, { DIGIT } ;
```

`{x}` 是零次或多次，`[x]` 是可选，`|` 是选择，`,` 是连接，`? … ?` 是由文字定义的词法规则。
代码块中的 `\t/\r/\n` 表示相应控制字符，不是模板文件里的两个可见字符。
`EOF` 表示输入结束。输入不做换行归一化，不自动 trim 整个模板。
字符入口不删除 BOM；字节输入仅按调用方指定的字符集解码，BOM 行为由该解码器决定，没有额外的 BOM 探测或删除。
CR 是水平空白；LF 才是行边界。普通文本中的 CRLF 原样保留。

Java 扫描与源码位置使用 UTF-16 code unit。对于普通 Unicode 文本，移植版可以内部采用字节或码点偏移，
但替换边界、捕获组和输出必须相同；诊断偏移若声称 Java 兼容，则需要换算为 UTF-16。

| 内容 | 大小写规则 |
|:--|:--|
| `HI-MEEPO`、`DNA:`、`RNA:`、指令关键字、注释头尾匹配 | 忽略大小写；常用标记均为 ASCII |
| 引擎别名、Map key、函数名、命名 Life | 区分大小写 |
| WHEN 的 `y/yes/n/no/not` | **只接受小写** |
| 参数中的 `TRUE/FALSE` | **只接受大写** |
| 数值类型后缀 D/F/N/L | 忽略大小写 |
| 控制组名 | 始终使用完全一致的拼写；Java 内部比较的混合规则见 §13 |

目标语言本身没有额外的字符串/注释词法保护：若文本中出现符合约定的注释头和指令，会被扫描器识别，
即使目标语言认为它位于一个字符串字面量内。

## 3. HI-MEEPO：声明注释边界

```ebnf
declaration = { HWS }, head_word, { HWS }, "HI-MEEPO", [ "!" ],
              { HWS }, [ tail_word ], { HWS }, ( LF | EOF ) ;
```

`head_word` 是标记之前紧邻的非空词，`tail_word` 是之后紧邻的非空词。
标记与词之间可以没有空白，例如 `/*HI-MEEPO*/`；建议加空格。
整段声明必须独占一行，行首/行尾只允许 HWS。没有尾词时，尾边界是 LF。
声明本身不出现在 merge 结果中；此前的普通文本可以保留。

| 声明 | 后续 HEAD | 后续 TAIL |
|:--|:--|:--|
| `// HI-MEEPO` | `//` | LF |
| `# HI-MEEPO` | `#` | LF |
| `-- HI-MEEPO` | `--` | LF |
| `/* HI-MEEPO */` | `/*` | `*/` |
| `<!-- HI-MEEPO -->` | `<!--` | `-->` |

没有可识别的声明时，注释模式返回原文；不会自动启用 `{{…}}`。
解析过程中可出现新的声明并切换 HEAD/TAIL；已有替换 Life 和控制组不会因此重置。
BKB 内的声明按普通文本保留。

HI-MEEPO 不承载字符集配置。输入、输出字符集由模板之外的调用方独立指定，默认 UTF-8。
标记后的单词保持原有尾界定符语义，不会因为恰好等于 utf8、gbk 等名字而改变含义。

HEAD 全由同一个字符重复组成时，允许延长重复部分：`#` 可匹配 `#####`，`//` 可匹配 `////`。
混合字符边界不扩展：`/*` 不把 `/***** DNA:RAW …` 当成同样的指令。

### 3.1 指令边界与空白

普通指令形态是 `HEAD + HWS* + 指令正文 + HWS* + TAIL`。
行注释在 LF 或 EOF 结束；块注释在之后第一个 TAIL 结束，可跨行。
查找 TAIL 不解释引号、括号或转义；表达式内需要该序列时应换一组注释边界。

默认声明启用 trim：从 HEAD 向左，若到行首之间全为 HWS，一并删除这些字符；
从 TAIL 向右，若直到 LF/EOF 全为 HWS，一并删除这些字符，并包含该 LF。
左右两侧分别判断，因此行内指令不会吞掉相邻正文。

`HI-MEEPO!` 关闭这两侧的扩展删除，只删除实际的注释头尾及其中的指令。
**行注释的 LF 本身就是 TAIL，使用 `!` 也会删除它**；块注释后的 LF 在注释外，使用 `!` 时保留。
DNA:RAW、DNA:SON 不调用普通指令的扩展 trim，见 §5。

正文右侧去空白不是对整个多行块调用 `strip()`：从 TAIL 前向左跳 HWS，遇 LF 停止；
若在该最后一行找到非空白，则正文结束于该字符之后，否则保留原正文结束位置。
完整边界算法的依据是 `Parser.findXnaGrp/bornHiMeepo/trimEdge`，复现时应保持这种“仅最后一行”的行为。

## 4. 指令 EBNF

HEAD、TAIL 是当前声明绑定的字符串，不是下面代码中的固定关键字。
`D` 是本条指令选定的**一个 UTF-16 字符分隔符**；同一指令三个分隔符必须完全相同。
`field(D)` 是不含 D 的任意字符序列，可以为空或包含 LF，前提是未先遇到 TAIL。

```ebnf
directive  = dna | rna ;
dna        = dna_set | dna_end | dna_bkb | dna_raw | dna_son ;
rna        = rna_put | rna_use | rna_run | rna_when | rna_each | rna_else | rna_done ;

dna_set    = "DNA:SET", HWS, { HWS }, D, field(D), D, field(D), D, [ scope ] ;
dna_end    = "DNA:END", HWS, { HWS }, name_list ;
dna_bkb    = "DNA:BKB", HWS, { HWS }, name ;
dna_raw    = "DNA:RAW", HWS, { HWS }, raw_text ;
dna_son    = "DNA:SON", HWS, { HWS }, resource_path ;

rna_put    = "RNA:PUT", HWS, { HWS }, engine_spec, D, field(D), D, field(D), D ;
rna_use    = "RNA:USE", HWS, { HWS }, D, field(D), D, field(D), D, [ scope ] ;
rna_run    = "RNA:RUN", HWS, { HWS }, engine_spec, D, field(D), D, field(D), D, [ scope ] ;
rna_when   = "RNA:WHEN", HWS, { HWS }, engine_spec, D, condition, D, field(D), D, group ;
rna_each   = "RNA:EACH", HWS, { HWS }, engine_spec, D, integer, D, field(D), D, group ;
rna_else   = "RNA:ELSE", HWS, { HWS }, group ;
rna_done   = "RNA:DONE", HWS, { HWS }, name_list ;

engine_spec = [ engine ], [ "!" ] ;
condition   = "y" | "yes" | "n" | "no" | "not" ;
scope       = "*" | occurrence_list | scope_name ;
occurrence_list = occurrence, { ",", occurrence } ;
occurrence  = positive | positive, "-", positive ;
positive    = ( "1" … "9" ), { DIGIT } ;
scope_name  = ? 非数字开头的非空 word；建议使用 ASCII 标识符 ? ;
name        = word ;
group       = word ;
name_list   = name, { HWS, { HWS }, name } ;
raw_text    = ? 指令关键字之后、当前正文边界之前的原文 ? ;
resource_path = ? 同 raw_text，作为资源路径使用 ? ;
```

Java 允许省略部分空格，具体扫描规则见 §13；为了跨语言模板清晰，建议遵循上述写法。
组名在实现中不局限于英数，但建议 `[A-Za-z_][A-Za-z0-9_-]*`。
正数范围、步长均需检查溢出：Java 步长是非零 signed int32；`-2147483648` 的绝对值须用更宽的整数计算。

### 4.1 分隔符的选择

1. 从指令关键字之后寻找第一个不属于 `HWS / ! / ASCII ALNUM` 的字符，作为 D。
2. 从它之后取接下来两个相同字符的位置，得到两个字段。
3. **没有分隔符转义机制。** `\/` 中的 `/` 仍会截断字段；表达式或正则包含 `/` 时可选 `#`、`~` 等其他分隔符。
4. 字段不经过参数引号解析；`"`、`'`、反斜杠和空格是否有意义，交给相应引擎或正则层。
5. scope/group 必须紧贴第三个 D。`/X/value/*` 是无限次；`/X/value/ *` 在 Java 中退回默认一次。
   WHEN/EACH 的组名前有空格则缺少有效组名，不能静默改成另一个作用域。

例如：

```text
# HI-MEEPO
# DNA:SET #https://old.example#https://new.example#*
https://old.example
```

## 5. DNA：解析时处理

### 5.1 DNA:SET、匹配与 Life

```text
# HI-MEEPO
# DNA:SET /name/{{user.name}}/1,3
name name name
```

解析后的文本是 `{{user.name}} name {{user.name}}\n`。
它是普通输出文本，不会自动再作为 Holder 占位符执行。

查找字段使用正则，默认相当于 Java `UNIX_LINES | MULTILINE`：仅 LF 是正则行边界，`^/$` 按行，`.` 默认不跨 LF。
内嵌标志如 `(?i)` 属于正则方言；不属于 Meepo 自己的表达式语法。

对每个有效匹配：有捕获组时仅替换 **group(1)** 的范围，否则替换 group(0)。
若可选 group(1) 未参与此次匹配，跳过它且不消耗 Life。
替换字段是**字面文本**；`\1`、`$1` 不展开为捕获组。
`a(b)c` 替换成 `Q` 得到 `aQc`，不是 `Q`。

Life 计数从 1 开始，记录匹配出现次序，跨后续文本片段累积，不是运行期循环次数：

| scope | 含义 |
|:--|:--|
| 省略 | 仅第 1 次匹配生效 |
| `*` | 匿名无限次 |
| `1,3-5` | 第 1、3、4、5 次；逆序端点归一化，重复和重叠范围并集化 |
| `part` | 命名无限次，直到 DNA:END part |

所有活动规则先在同一段**原始文本**上匹配，再按位置拼装；替换结果不参与后续规则匹配。
不同规则的选中范围重叠是解析错误，不能取“先匹配优先”。相接而不相交的半开区间不冲突。
Java 的具体判定是 `a.until > b.start && b.until > a.start`；零长度范围落在正长度范围内部时也可能被判为冲突。
普通文本以已识别指令/声明为边界分段；正则在每段单独运行，Life 继续累计。
因此正则的段首/段尾可能不等同于原文件行首/行尾，不应改成先对整份文件进行全局替换。

### 5.2 DNA:END 与 DNA:BKB

DNA:END 可列多个命名 Life；关闭动作只影响后面的源文本。
同名 Life 的特殊覆盖行为见 §13。匿名 `*` 没有可关闭名称。

```text
# HI-MEEPO
# DNA:BKB shield
# RNA:USE /X/value/*
X
# DNA:END shield
```

上例输出保留免疫区域中的 `# RNA:USE …` 和 `X`；BKB 与匹配的 END 自身被移除。
免疫区内不创建新指令、不切换注释声明，也不执行已有替换规则。
解析器只识别 DNA:END，以检查能否结束当前免疫区；不匹配的 END 保持在原文中。
当前扫描路径不会在免疫区内开启嵌套 BKB。未结束 BKB 的 EOF 行为见 §13，不能拿通常的语言块栈规则推断。

### 5.3 DNA:RAW

从指令中取出原文写入结果，例如：

```text
<!-- HI-MEEPO -->
<!-- DNA:RAW <div>{{backend.value}}</div> -->
```

输出 `<div>{{backend.value}}</div>\n`。RAW 内容不再次执行 DNA/RNA/Holder。
RAW 保留注释外字符；正文的左右水平空白按 §3.1 的扫描规则处理，不是整段 Unicode trim。
对于 `<!-- HI-MEEPO! -->`，RAW 仍提取正文；`!` 不把关键字和注释边界保留到结果。

### 5.4 DNA:SON

在 parse 时按调用方指定的输入字符集读取资源并独立解析，默认 UTF-8；子模板需有自己的 HI-MEEPO。
父子拥有独立的注释配置、Life、控制栈和解析规则；父规则不处理子源码，子规则不泄露到父模板后续文本。
合并时子片段在包含位置执行，使用**同一份本次 merge 上下文**，所以子模板的 PUT 可影响父模板后续读取。
输入字符集仅保存在本次 Parser 上下文，并用于后续嵌套 SON；Gene、Exon 及子节点不保存输入字符集。
同次解析中的资源应使用相同输入字符集，混合源编码需要应用在解析边界适配。
合并统一使用调用方本次指定的输出字符集，不在子片段处切换流编码或插入额外 BOM。

资源路径不做 Map 插值，也不去除引号。Java 的读取约定为：

| 形式 | 含义 |
|:--|:--|
| `classpath:/path` | Java 类资源；移植版可用相同逻辑资源空间代替 |
| `file:///path` | 标准本地文件 URI；百分号编码按 URI 解码，支持空格、`%`、`#` 和 Unicode 路径 |
| `file://./path`、`file://../path` | Java 保留的旧式相对文件路径，按进程工作目录定位 |
| `http://`、`https://` 等 URL | 读取内容；Java 连接与读取超时均为 3 秒 |
| 无协议 | 先类资源，再文件，再 URL 处理 |
| 以 `.` 或 `/` 开始 | 按父模板来源 URI 解析；路径中的空格需要 URI 编码处理 |

标准 file URI 交由本地文件系统 provider 校验，不能把非空 authority 当作相对目录名。
其他相对文件路径使用无协议路径或转换为标准 file URI；scheme 大小写不影响 file 读取。

需要检测当前包含链中的递归环，报 parse 错误。Java 对已有文件用真实路径作为链中身份，识别软链接别名；
尚不存在的来源路径按绝对规范路径标识，其他 URI 规范化路径。身份只用于检测环，相对包含仍按原来源 URI 定位。
同一资源在不形成环的不同位置被包含是允许的。
不要把“已经包含过一次”误实现为全局禁止再次包含。

## 6. RNA：渲染时执行

省略引擎时为 `map`。引擎别名后紧贴的 `!` 是 mute 请求，例如 `raw!/X/text/*`。
mute 只涉及求值错误处理，不保证忽略解析/预编译/正则错误；具体引擎需要实现该行为。

| 指令 | parse 的工作 | 每次 merge 访问到节点时 |
|:--|:--|:--|
| PUT | 保存字段、检查表达式 | 求值，把结果写到指定的**完整 key**，不输出；变量或表达式为空则不操作 |
| USE | 匹配源码并生成每个替换位置 | 通过 map 读取并输出当前值；Java 的值读取固定为 mute=true |
| RUN，非空 find | 匹配源码并生成每个替换位置 | 每个位置单独执行表达式并立即输出，不缓存计算结果 |
| RUN，空 find | 创建一个直接执行节点 | 只执行副作用，不输出；Life 不控制这种空 find 执行 |
| WHEN | 组装命名条件分支 | 若同名组已有胜出分支，不求值；否则按条件决定是否执行子节点 |
| EACH | 组装循环子节点 | 求值得到数组、集合或有限游标，按步长访问并输出，空输入留给 ELSE |
| ELSE | 组装同名后备分支 | 同名组没有胜出分支时执行 |
| DONE | 关闭当前控制栈的组 | 清理组的胜出标记，使包围它的循环可再次执行此组 |

没有匹配位置的 USE、非空 find RUN 不会执行表达式。
不要把 RUN 每个位置的计算提升到本次 merge 开头，也不要把不执行的 WHEN 分支预求值。

### 6.1 分组与树

```text
# HI-MEEPO
# RNA:WHEN /yes/primary/choice
first
# RNA:WHEN /yes/secondary/choice
second
# RNA:ELSE choice
fallback
# RNA:DONE choice
```

同组多个 WHEN 相当于 if / else-if；最多选一个。不同组名形成嵌套。
DONE 可列多个名称，但只连续关闭**当前栈顶**在给定名称集合中的组，不是遍历任意祖先栈。
文件结束时控制栈必须只剩根节点，否则严格与 lax 都失败。

Java 对控制节点的实际构树算法是：

```text
push ROOT
遇 WHEN / EACH / ELSE 节点 N:
    如果栈顶名字与 N 的名字忽略大小写相同，弹出原栈顶
    把 N 加入当前父节点，再压入 N 的子节点列表
遇 DONE names:
    只要当前栈顶名字按精确拼写属于 names，就弹出该组
    在其父列表追加一个负责清理该组运行标记的 DONE 节点
EOF: 栈必须只有 ROOT
```

普通模板应使用常规 WHEN 链或 EACH/ELSE。EACH 不像 WHEN 那样检查此前的胜出标记：
它总是求值，非空时写入该组标记；不要把“同组 EACH”擅自实现为标准 else-if。
Java 也允许可被 DONE 关闭的独立 ELSE；它在无胜出标记时执行，不要求先出现 WHEN。

### 6.2 假值规则

以下为 false，其余为 true，不能直接使用 Python/JS/Go 的默认真值规则：

| 值 | false 的条件 |
|:--|:--|
| null | 总是假 |
| Boolean | false |
| 字符序列 | 长度为 0；`"false"`、`"0"` 都是真 |
| Number | 转为 double 后为 NaN，或 **-1e-9 < value < 1e-9**；边界 ±1e-9 为真 |
| Map、Collection、数组 | 为空 |
| 其他对象 | 为真 |

`y/yes` 取真，`n/no/not` 对上述结果取反；不允许字段周围添加空格。

### 6.3 EACH 步长、状态和变量

支持数组、Collection，以及 Iterator、非 Collection 的 Iterable、BaseStream 有限游标。
BaseStream 包括 Stream、IntStream、LongStream、DoubleStream。
null、Map、普通对象和标量都作为空输入处理。
移植时 Python 的 dict、JS 的普通 object 不应自动变成键值对迭代。

`step > 0` 访问索引 `0, step, 2*step, …`；`step < 0` 从 `size-1` 向前走。
不得为 0。集合迭代顺序使用其本身顺序；不可依赖无序集合生成稳定结果。
游标先完整读取到 ArrayList，然后进入与 Collection 相同的循环逻辑，支持正负步长。
正向读入时，将步长跳过的位置填为 null，保留原始索引。
反向读入时先保留全部值，取得总数后，按 `size-1` 为起点清空跳过位置的引用。
真实 null 条目和占位 null 的处理由访问索引区分，选中的真实 null 仍会执行循环正文。

以组名 `it` 为例，以下是上下文中的**完整字符串 key**：

| key | 内容 |
|:--|:--|
| `it` | 当前条目，包括 null |
| `it._total` | 输入的原始大小，游标使用从当前位置起读到末尾的元素数 |
| `it._count` | 已访问条目的 1-based 序号 |
| `it._first` | 当前访问序号为 1 |
| `it._last` | 当前为本步长下最后一次访问 |

非空输入的访问次数为 `floor((size-1)/abs(step))+1`。循环前写入精确 total、count=0、first=false、last=false。
结束后最后一个条目及这些状态仍可读取，不恢复原绑定；空循环不覆盖已有 `it` 条目。
所有这些修改只影响本次 merge 的上下文副本，不改变调用方 Map。
EACH/ELSE 的含义是“没有条目则 ELSE”，不同于 Python 的 for/else。

Java 的 `_total`、`_count` 对所有输入类型都使用 int，`_first`、`_last` 由同一套集合循环逻辑计算。
游标预处理时间为 O(n)，ArrayList 保留 n 个引用位置，额外空间为 O(n)。
条目按对象引用保存，跳过位置的引用清为 null。反向预处理在取得总数前会暂时保留全部条目。
EACH 在读完有限游标后开始输出正文。预处理异常会向调用方传播，此前其他节点已经写入 Writer 的内容会保留。

Iterator 从当前位置开始消费。再次合并同一个已耗尽 Iterator 会进入 ELSE，已使用 Stream 再次合并会报错。
复用模板时可以让 Supplier 每次提供新的 Iterator 或 Stream。
Stream 由调用方关闭，包括正常完成、表达式或遍历失败的情况。
上下文使用浅复制，消费游标会改变输入对象本身的状态。

## 7. map 引擎与管道

### 7.1 两层扫描，而不是宿主语言表达式

map 不支持通用算术、比较运算、下标或函数括号语法。基本写法：

```ebnf
map_expression = head, { "|", call } ;
head           = lookup_text | call_with_arguments ;
call           = argument, { BLANK, { BLANK }, argument } ;
call_with_arguments = argument, BLANK, { BLANK }, argument,
                      { BLANK, { BLANK }, argument } ;
argument       = quoted | unquoted ;
quoted         = single_quoted | double_quoted ;
single_quoted  = "'", { ? 非结束单引号的字符；转义规则见下文 ? }, "'" ;
double_quoted  = '"', { ? 非结束双引号的字符；转义规则见下文 ? }, '"' ;
unquoted       = ? 参数扫描得到的未加引号文本 ? ;
lookup_text    = ? 管道第一段的原文，支持点号导航 ? ;
```

这里 EBNF 是便于阅读的形态，实际必须按下面两层算法处理：

1. 先按未转义 `|` 分段，trim 每段两端的 BLANK，忽略空段。
2. 对每段调用参数扫描器。**管道分割不识别引号**，所以 `"a|b"` 不保护其中的管道符。
3. 第一段参数数 >1 时为“函数 + 参数”；否则用第一段**原文**作 key/导航，不用参数转换后的单个值。
4. 之后各段始终是函数调用，允许零参数。

`{{TRUE}}` 是查 key `TRUE`，不是输出布尔常量；`{{42}}` 同理。
`{{nil|mod TRUE}}` 中 TRUE 才是布尔参数。给整个第一段加引号不会自动获得字符串字面量输出能力。

### 7.2 管道与导航分隔

对管道符前连续 N 个反斜杠：N 为奇数时，把最后一个反斜杠替换为 `|`，不切段；
N 为偶数时切段，已有反斜杠保留。没有“顺便把所有 `\\` 解码”的步骤。
trim 和空段移除发生于切段时。参数扫描是第二个独立步骤，不能把两个步骤合并成 shell 分词。

导航分隔符为点 `.`，没有冒号导航。`fun:` 的冒号只是 key 的一部分。
点分割会 trim 并忽略空段，不做反斜杠转义。

### 7.3 lookup 与函数调用

读取 key 时依次执行：

1. 在本次上下文查找完整 key，非 null 即返回。显式 null 不阻止后续路径回退。
2. 有点号路径时逐段读取 Map key，或读取宿主对象属性；中途 null 返回 null。
3. 普通首段查找仍为空时，Java 尝试 System property，然后环境变量。函数调用首段不做这两步回退。
4. 全局函数默认值已合并到上下文，调用方同 key 绑定优先；不要在最后重新放回一个被调用方覆盖的全局函数。

显式带参数的函数首段、后续管道均按完整函数名读取，不把其中的点号当成对象导航。

Java Bean 适配层优先匹配 public 无参方法：方法名等于 key，或 `getX/isX/hasX`，再找 public 字段。
多种 getter 同时存在时反射遍历顺序不保证；移植版应声明自己的对象适配策略，普通跨语言模型建议使用 Map。
完整 Map key 的优先级必须一致，例如 `user.name` 的直接值优先于嵌套 `user -> name`。
Java 在 Map 键查不到时也可能反射这个 Map 对象本身，例如读取无参 `size()`；
此类 Java 容器成员可见性属于宿主适配能力，不能依靠 Python/JS/Go 的默认字典行为自然获得。

求值时函数名先尝试原 key；不是可调用值时再尝试加 `fun:` 前缀。已有前缀不重复添加。
函数名及前缀均区分大小写。
前缀省略仅发生在已判定为函数调用的位置；单个普通首段不会自动加 `fun:`。
例如 `{{abs}}` 查 key `abs`，通常为空；`{{fun:abs}}` 读取注册函数并执行，previous 为 null，结果为 0。
无参 `now` 同理，应写 `{{fun:now}}`；`{{now "yyyy-MM-dd"}}` 已有参数，因此可省略前缀。

| callable 类型 | 第一段中的行为 | 后续管道中的行为 |
|:--|:--|:--|
| Supplier | 调用一次取得值 | 不作为管道函数接受 |
| JavaEval 等价函数 | `eval(ctx, null, args...)` | `eval(ctx, previous, args...)` |
| Java Function 等价单参函数 | 有显式参数时收到参数数组，否则收到 null | `apply(previous)`，不传显式参数 |

首段普通值不是函数时仍可直接返回。后续管道找不到可调用函数则为求值错误。
Java 的 USE/Holder 对求值错误静默返回 null；RUN/PUT/WHEN/EACH 由其 mute 标记控制。
空表达式的 map 结果为 null。**缺失 key 不回显 key，默认输出为空。**

### 7.4 参数扫描与类型

参数扫描按 BLANK 分隔；单双引号可包含空白，另一种引号在当前引用内是普通字符。
关闭引号时立即生成一个 **String** 参数，包括空串；未引用参数按以下顺序分类：

```ebnf
boolean_argument = "TRUE" | "FALSE" ;
number_candidate = [ "+" | "-" ], number_body, [ number_suffix ] ;
number_body      = ( DIGIT | "_" | "," | "." ), { DIGIT | "_" | "," | "." } ;
number_suffix    = "D" | "F" | "N" | "L" | "d" | "f" | "n" | "l" ;
```

候选数删除 `_` 和 `,` 后，还须通过相应数值类型的合法性检查；不能把 `1.2.3` 当成数字。
无后缀时整数为 int32，含点为 float32；D 为 double，F 为 float32，N 为十进制 BigDecimal，L 为 int64。
转换失败（含整数溢出）退回 **RefStr**，不报数字语法错误。科学记数法不被这个扫描器接受。
引号内的 `"001"`、`"TRUE"` 保持字符串。

其余未引用文本为 RefStr，即“可能引用上下文的词”，不是普通 String。
MapEngine 不统一解析所有函数参数中的 RefStr；具体函数自行决定是否进行引用查找。
例如 mod 对 RefStr 找不到值时回退原词，see 找不到则视为 null；引用字符串从不查上下文。

参数转义比常见 shell/JSON 更有限：反斜杠可转义引号和反斜杠；`\n/\t/\r` **不展开**为换行/制表符。
反斜杠加普通字符保留原反斜杠。完整有限状态算法与宽松引号边界见 §13.4。

## 8. 内置函数与宿主扩展

| 名称（可省略 `fun:`） | 合同与移植要点 |
|:--|:--|
| `mod` | 将 previous 转 int32；null=0、false=0、true=1。对参数数目取 floor-mod，负数也产生非负索引。零参数报错；选中 RefStr 查上下文，未找到回退该词 |
| `abs` | null→0，Boolean→0/1；整数绝对值，最小 int64 需扩展为十进制以免溢出；非整数按十进制绝对值处理 |
| `see` | previous、各参数中第一个非 null 且非空字符序列的值；0、false、空集合都“可见”。RefStr 查不到为 null，不同于 WHEN 假值 |
| `upperCase/lowerCase` | 字符串大小写，参数可给 locale tag；无参数使用宿主默认 locale。null 输出空串；跨语言确定性输出建议显式给 locale |
| `camelCase`、`pascalCase/PascalCase` | 命名风格转换，算法见下 |
| `snakeCase/snake_case`、`kebabCase/kebab-case`、`dotCase` | 以 `_/-/.` 连接，选项 lower/upper/keep，默认 lower |
| `bigCase/bigSnake/BIG_SNAKE`、`bigKebab/BIG-KEBAB` | 全大写的下划线/减号连接 |
| `fmt` | Java `String.format(pattern, previous)`；Java 格式串与 Go/Python/JS 的格式串不能直接等同 |
| `now` | Date/TemporalAccessor 则格式化该值，否则当前本地时间；默认 `yyyy-MM-dd HH:mm:ss`，参数是 Java 时间格式串 |
| `now.date/now.time` | Supplier 变量，分别输出当前本地日期 `yyyy-MM-dd`、时间 `HH:mm:ss` |

名称变换不能直接套用宿主的“聪明 acronym 转换器”。参考算法按 UTF-16 字符扫描：

- 可保留字符按 Java `Character.isJavaIdentifierPart` 判断；下划线作分词符；其他标点作分词边界。
- 分隔连接模式：开头的普通标点忽略；下划线无论位置都设置分词状态，因此前导下划线可在首个有效字符前产生连接符。
  遇分隔符后，在下一个字符前补一个连接符；小写字符紧接大写字符也插入连接符。
  连续大写不拆为多个词；末尾分隔状态不输出连接符。字符大小写按选项逐字符转换。
- camel/Pascal 模式：首字符分别转小写/大写；分隔符后的首字符大写；其他位置若**前一个原字符**大写则本字符小写，否则保留。
  不使用词典识别 acronym；分隔符本身不输出。
- Java 标识符 Unicode 分类、逐字符大小写与 locale 全字符串转换不是同一套规则。精确 Unicode 兼容需要相应字符数据表；
  移植版至少应明确 ASCII 子集和 locale 支持范围。

两类算法的状态初始为 start；下划线总设为 separator，其他非标识符字符仅在非 start 时设为 separator。
写出有效字符后状态为 word，`last` 每步都记录原字符，包括被跳过的标点。
camel/Pascal 在 separator 后写大写，在 start 才使用其首字母大小写策略，因而 `_abc` 的 camel 结果是 `Abc`。
连接模式的大小写选项 upper/keep 忽略大小写，其余选项均回退 lower。

引擎扩展合同建议保留 `warm(alias, expression) -> plan`、`eval(context, plan, mute) -> value` 和独立实例/无状态共享约定。
parse 时复用同 alias/expression 的预热结果，但不要把请求数据或表达式求值结果存入共享计划。

| Java 引擎别名 | 行为 |
|:--|:--|
| `raw` | 直接返回表达式原文；不解析引号、转义；即使带 `!` 仍返回原文 |
| `uri` | 读取原文资源，不解析为模板；以表达式为 key 缓存在本次 merge 上下文，已有非 null 值可覆盖读取 |
| `exe` | 解析命令参数、直接执行，收集 stdout |
| `sh` / `cmd` | 分别经 bash / Windows cmd 执行；是宿主扩展而非模板核心表达式 |
| `java` | parse 时编译 Java 方法体，merge 时执行；可有 import，返回值来自显式 return |
| `fun` | parse 时编译同样的 Java 函数体，eval 返回函数对象；常与 PUT 注册 `fun:name` 一起使用 |
| `js` | 需显式注册 Java ScriptEngine 适配器，默认注册表不启用它 |

Java 运行时编译显式禁用注解处理器，不隐式执行 classpath 上的处理器；仍需完整 JDK 提供 javac。
OS 环境变量构建失败也遵守 mute；等待被中断时恢复线程中断标志，销毁直接子进程。
`exe` 的参数按引用/转义规则拆分后直接执行；`sh` / `cmd` 执行 shell 源码。
宿主扩展具有文件、网络或代码执行能力，完整模板需可信。相关回归见 [分支与边界审计](benchmark/coverage-branches.zh.md)。

未知引擎在 Java 工厂中可能先回退默认引擎，但预热检查会报告 alias 不匹配；不能依赖它自动变成 map。
Go/Python/JS 实现可提供各自引擎，但应使用清晰的新别名，模板的外层分隔语法保持不变。

## 9. 占位符模式 Holder

占位符模式是独立入口，不依赖 HI-MEEPO，不执行 DNA/RNA 指令。
默认前缀 `{{`、后缀 `}}`、转义串为空；边界可配置为多字符字符串。

```ebnf
placeholder_document = { literal_text | placeholder } ;
placeholder          = PREFIX, map_expression, SUFFIX ;
```

表达式内部使用 §7 map 规则。placeholder 的值读取固定静默；不做 HTML/JSON/SQL 等自动转义。
任一边界为空时返回纯文本。相同边界匹配位置优先认为是 PREFIX，而不是交替开/关。
不支持嵌套占位符；边界识别不理解表达式引号，所以引号内出现 SUFFIX 也会结束占位符。

配置转义串 E 后，仅在 PREFIX/SUFFIX 前计数连续、完整的 E：

1. N 为奇数：边界变成字面量，不记为结构边界。
2. N 为偶数：边界仍有效。
3. 两种情况都从输出缓冲移除 `ceil(N/2)` 个 E，保留其余 `floor(N/2)` 个 E。
4. 其他位置 E 不生效；不是对模板执行通用字符串反转义。

E=`\` 时，`\{{literal\}}` 输出字面 `{{literal}}`；`\\{{name}}` 输出一个反斜杠再输出变量。
默认 E 为空，因此默认模式不会把反斜杠当成占位符转义。

Java 的边界状态机还允许不完整起始符原样保留，部分孤立结束符有位置相关行为，见 §13.5。
移植实现若直接用一个全局 `replace(/{{.*?}}/)`，通常会在这些边界和转义情况下产生不同结果。

## 10. 输出、隔离与缓存

每次 merge 创建新的可变上下文：全局函数快照作为默认值，浅复制调用方 Map 并让其覆盖默认值。
显式 null 也覆盖默认函数；上下文内的对象值是引用，不是深拷贝。
PUT、EACH 和 uri 缓存的写入仅作用于本次副本；下一次 merge 不能继承上一次的变量或分支胜出状态。
用户函数若自行修改传入的嵌套对象，其副作用由调用方的对象共享关系决定。

输出规则：null→空串；Boolean→小写 `true/false`；整数→十进制；BigDecimal→不使用指数的 plain string；
String/字符原样写；其他标量按约定的字符串适配器处理，不自动 JSON 序列化。
Java float/double 的精度、NaN/Infinity 及指数形式需由数值兼容层处理，不应默认为宿主格式恰好相同。

USE/RUN 输出数组或集合时：跳过 null 元素；第一项直接输出，此后每个非 null 项之前补上捕获位置的左边距个**空格**。
左边距仅在该位置之前到行首全为 HWS 时有效，否则为 0；tab 计为一个字符，不展开为制表宽度。
条目本身不自动加换行，也不对条目内部每行重新缩进；换行应由条目文本携带。
Holder 使用左边距 0。Java 的 char[] 经过集合式缩进入口时逐字符处理，不能把它与普通 String 的行为混为一谈。

字节输出应与完整字符结果调用 `String.getBytes(outputCharset)` 一致。
合法 Unicode 字符必须跨静态片段、动态值甚至 flush 正确配对；无法表示的字符按目标字符集的替换策略输出。
例如 GBK 无法表示的 emoji 会被替换；UTF-8 对不完整 UTF-16 代理字符以 `?` 替换，UTF-16 使用其自己的替换字节。
流式 merge 不 flush/close 调用方的流。若需要数组，由调用方自行提供 ByteArrayOutputStream 收集。

Meepo 以字符为核心，输入和输出字符集是互相独立的边界参数，不属于模板语法，也不进入 Gene 的状态：

| Java API | 字符集的用途 |
|:--|:--|
| `Meepo.parse(file, charset[, ttl])`、`parse(uri, charset[, ttl])` | 解码模板字节，缓存键区分输入字符集 |
| `Meepo.parse(key, inputStream, charset[, ttl])` | 解码输入流，解析后关闭输入流 |
| `Meepo.merge(ctx, file/uri, charset[, ttl])`、`merge(ctx, key, inputStream, charset[, ttl])` | charset 仅用于输入解码，返回值仍是字符 String |
| `Parser.parse(inputStream, charset)` | 直接将字节解码成字符后解析 |
| `Parser.parse(text, origin, lax, charset)` | text 已经解码；charset 仅用于 SON 的字节读取 |
| `Gene.merge(ctx, outputStream, charset)` | 用本次指定的目标字符集输出字节 |
| `Gene.merge(ctx, outputStream)` | 默认 UTF-8，不关闭或刷新调用方的流 |
| `Meepo.steam(ctx, gene, charset)` | 返回指定编码字节构成的 InputStream，转交本次渲染缓冲区，游标独立 |

未指定或传 null 时，字节输入、输出默认 UTF-8。
`merge(ctx)` 返回字符，`merge(ctx, Writer)` 的最终编码由调用方 Writer 决定。
`Gene.merge(ctx, Writer)` 和 `Exon.merge(Acid, Writer)` 使用字符语义，输出编码由调用方配置的 Writer 决定。
Ubuf 持有构造时指定的输出字符集，静态字节缓存和动态编码都使用该字符集。
固定文本统一交给 Dent.write(Writer, Utxt)，普通 Writer 接收原字符数组，Ubuf 通过 write(Utxt) 输出。
Ubuf 对适合拼接的编码调用 Utxt.getBytes(charset, writer.canCache)，Utxt 负责查找缓存、编码与发布最新快照。
Utxt.getBytes(charset) 默认使用 false，每次生成调用方独占的数组。
编码器直接读取固定 char[] 的 CharBuffer 视图。字节缓冲区容量恰好等于有效长度时直接转交数组，否则按有效长度裁剪。
Ubuf 处理片段边界，状态编码的字符片段进入连续编码器。
同一 Gene 可先输出 UTF-8，再输出 GBK，而不重新解析或保留输入编码信息。
调用方还需保持 HTTP Content-Type、HTML meta、XML encoding 等外部声明与实际输出编码一致。

`Utxt` / `Ubuf` 中的 U 表示 user charset。每个静态片段只缓存最近一次“字符集＋字节”的 Encoded 快照。
缓存项及其中的字符集由 volatile SoftReference 持有，内存压力下可一起回收。
命中且字符集相同则直接复用；缓存被回收、字符集改变时重建。一次调用只读取一次引用并保留局部强引用，整体发布完整快照，保证并发时不混用编码。
Gene 沿用 `merge` 的方法命名，不直接返回 byte[] 或 InputStream。已有的 Meepo.steam 入口返回独立游标，
读取本次渲染独占的缓冲区；已经创建的流不受缓存回收和后续渲染影响。
UTF-8、GBK、GB18030、Big5、Shift_JIS、windows-31j、US-ASCII、ISO-8859-1、UTF-16BE/LE 可直接拼接缓存片段。
其他字符集使用单次渲染内连续的编码器，保证 UTF-16 的 BOM 只出现一次、有状态编码的状态不被片段切断；不能把任意编码的独立 getBytes 结果直接拼接。
Ubuf 通过 CharsetEncoder 直接编码字符数组和字符串切片的 CharBuffer 视图，复用一个字节缓冲区。
完整 String 在已知无状态编码下可使用 JDK 编码快路径。

静态结果缓存、预编码静态字节、预热表达式和访问器缓存均是可选优化，不改变语义。
只有确定不依赖本次模型的模板才能缓存整个结果。共享计划中不能保存循环状态或 Map 的上次求值值。
API 层 URI 模板缓存的 TTL、不同时钟来源和线程调度不属于模板文件语法。

## 11. 建议的实现算法

```text
parse(source, origin, lax):
    创建注释配置、活动处理器列表、命名 Life、免疫状态、控制组栈
    从第一个 HI-MEEPO 候选识别配置；无法识别则返回 Literal(source)
    顺序扫描当前 HEAD 或新的 HI-MEEPO 声明
    对已识别的正常指令:
        flush 前面的普通文本
        更新 DNA Life / BKB / 包含，或把 RNA 节点加入当前控制组
    对免疫区只检查能否结束免疫，其余保留原文
    对未知指令保留原文，不把它当成强制 flush 边界
    flush 剩余文本；校验重叠、诊断及未闭合控制组

flush(text):
    对所有仍活动的规则，在这段原文上运行正则
    为匹配或捕获组计算半开区间；推进各自 Life
    拒绝相交的选中区间；按起点排序
    拼出未匹配 Literal 与每个匹配对应的替换节点
    预热实际会保留在输出计划中的表达式

merge(plan, input):
    ctx = 全局默认函数快照 + input 的浅副本，input 优先
    winners = 空 Map
    顺序执行节点，遇控制节点按 §6 执行子计划
    输出 String 或目标字符集的字节；结束当前编码序列；清理本次状态
```

推荐节点：Literal、EvalOutput、Assign、Conditional、Each、Else、Done、Include。
Meepo 的 DNA 节点可能保留原始声明以支持 `build()` 重建；跨语言版可独立保存 source map，
不必照抄 Java 的类名/继承结构，`build()` 也不应被误当成渲染入口。

## 12. 错误与 lax

| 情况 | 当前 Java 行为 |
|:--|:--|
| 未知 DNA/RNA 指令 | strict/lax 都作为原文 |
| 缺少字段分隔符、非法 WHEN 控制值、步长为 0、缺组名等已识别语法错误 | strict 报 parse 错误；lax 尽可能保留该段原文 |
| 正则非法、某些 Life 数字转换失败、子资源错误、包含环 | 可直接报 parse 错误，lax 不是万能恢复 |
| 替换范围相交、EOF 控制组未闭合 | strict/lax 均失败 |
| warm 诊断 | strict 失败；lax 可记警告，但不能据此承诺所有损坏计划都可执行 |
| 求值抛错 | USE/Holder 静默为空；其他 RNA 根据引擎及 `!` 决定 |
| 输出流写入失败 | 传播渲染错误；不能按表达式 mute 吞掉输出失败 |

跨语言错误建议包含 phase（parse/merge）、来源 URI、行号、源码范围及原始原因。
Java 异常类名、完整堆栈和日志文案不要求逐字相同。
测试“会失败”必须区分 parse 与 merge：在解析时就执行函数会造成错误阶段和副作用时机都不兼容。

## 13. Java 扫描与边界行为附录

### 13.1 指令宽松识别

`seekFollow` 只跳 HWS，并进行忽略大小写的前缀匹配，不验证单词尾边界。
因此 Java 接受 `DNA: SET`、省略部分空白、以及关键字之后某些冗余字符；不要通过宽泛的通用词法器擅自改变结果。
find 三分隔符扫描会忽略关键字与第一个 D 之间的字符；引擎取该区间中第一个连续 ASCII ALNUM 词，没取到则 map。
`!` 必须紧贴取到的引擎词末尾；无引擎时仅紧贴首个 D 前面的 `!` 生效。
非字母数字分隔符甚至可为 LF，或把 Unicode 字母当分隔符；正常跨语言模板建议使用 ASCII 标点。

命名 Life/group 仅取第三个 D 后立即开始的第一个词；其后的额外词通常不参与语义。
END/DONE 则逐词收集名称，遇包含当前 HEAD 的词即停止。BKB/ELSE 只用第一个名称。
首个 HI-MEEPO 候选不合法时不保证继续寻找更晚的合法声明；尤其输入位置 0 的无前缀标记会导致原文返回。

还有一个新声明探测的前瞻边界：`markHiMeepo` 先找当前 HEAD 的位置 head0，
若 head0 严格大于当前位置，则只在当前位置到 head0 之间寻找新声明；
若 head0 等于当前位置，或没有找到当前 HEAD，则在全部剩余文本中寻找新声明。
后一种情况下，后面出现的新声明可能使其前面的指令保持为字面文本而未执行。
用例 `header-lookahead-boundary` 固定了这个现有行为，`switch-delimiters-after-text` 展示正常切换。
核心模式建议不要依赖这种前瞻跳过；声明逐行处理的移植版应把这里列作与 Java 的兼容差异。

### 13.2 Life 与免疫边界

Java 的 Life 先按 `String.trim()` 去除首尾空白，空串按省略 scope 处理（第 1 次）；再以首字符分类：
`*` 开头均作无限次；非数字开头均作命名；数字开头才作数字列表解析。
所以 `-1` 是名称而非负次数，某些零/负区间如 `0--2` 也被现有数字转换接受。
建议规范模板只用正数；若实现 Java 边界模式，应保留当前分类，不自行当成布尔或负索引。
区间排序比较不得使用可能溢出的 int32 减法；例如 `0--2147483648,2` 中无正次数的区间不能使第 2 次之后多出 Skip。

同名 Life 再次注册会覆盖“名称到 Life”的引用；END 只关闭该名称最近注册的对象，旧处理器可能仍活动。
重用名字不能被移植版自动解释成关闭所有同名历史规则。

SET 的零长度选中范围不会插入替换文本；USE 的零长度范围可生成动态输出节点；空 find RUN 则采用 §6 的副作用语义。
不要把所有空 find 一概处理为正则全局插入或一概忽略。

EOF 不要求 BKB 闭合；其最后剩余原文走当前解析器的最终文本 flush 路径。
存在 BKB 前活动规则时，这与“始终原样保留到 EOF”的直觉可能不同。需精确复制此边界时应对照 Parser 的最后一次 `dealTxtPlain`。

### 13.3 组名比较

相邻控制帧是否换分支使用忽略大小写比较；DONE 的名称集合查找和运行期 winners Map 使用精确 key。
混用 `choice/Choice` 不等价于整个语言都忽略大小写。移植时建议保留这一兼容行为，模板作者使用一致名称。
DONE 的未知名称可以成为无操作；它不会越过不在名称集合中的栈顶去关闭外层组。

### 13.4 参数扫描器的完整状态

令 `q=无引号`、`esc=false`、`buffer=空`，逐字符执行：

```text
遇反斜杠:
    esc=true 时追加一个反斜杠并令 esc=false；否则令 esc=true
遇单/双引号:
    esc=true 时追加引号并清 esc
    q=无引号时令 q=当前引号
    q=当前引号时立即把 buffer 作为 String 参数加入（允许空串），清 buffer 和 q
    其他引号直接加入 buffer
遇 BLANK:
    q 非空则直接加入 buffer
    q 为空且 buffer 非空则按未引用规则提交并清 buffer
    此分支不清 esc
遇其他字符:
    esc=true 则先补回反斜杠并清 esc
    再追加当前字符
EOF:
    buffer 非空时，若 esc=true 则补一个反斜杠，再按未引用规则提交
```

未闭引号不一定报错；EOF 仍按未引用规则提交剩余 buffer。单独尾反斜杠且 buffer 空时会丢弃。
`a"b"` 关闭引号时提交整体 String `ab`；`"a"b` 则可能得到两个参数。
这不是 POSIX shell 的相邻引号拼接规则；引号关闭后的参数也不再走数字识别。

### 13.5 Holder 的完整边界状态

先处理转义得到有效边界事件流，再处理如下状态；literal 指的是转义处理后的文本：

| 状态 / 新事件 | 动作 |
|:--|:--|
| 尚无状态 + PREFIX | 输出前面的 literal，记住 PREFIX |
| 尚无状态 + SUFFIX，位置 0 | 忽略结构事件，最后仍保留 literal |
| 尚无状态 + SUFFIX，位置非 0 | strict 报错；lax 忽略结构事件 |
| 记住 PREFIX + SUFFIX | 创建求值节点，记住 SUFFIX |
| 记住 PREFIX + PREFIX | strict 报嵌套错误；lax 忽略新 PREFIX 事件，原字符仍在表达式里 |
| 记住 SUFFIX + PREFIX | 输出中间 literal，记住 PREFIX |
| 记住 SUFFIX + SUFFIX | strict 报重复结束错误；lax 忽略该结构事件，原字符可保留在后续 literal |
| EOF | 从最后一次消费位置输出剩余 literal；孤立 PREFIX 不单独报错 |

例如 `a{{missing` 原样保留；开头的 `}}x` 保留；`x}}` 在严格模式报错。
lax 的“忽略事件”不等于从字符结果删除该边界。

## 14. 一致性用例

[cases.json](conformance/cases.json) 是与语言无关的输入/预期结果集合：
`mode` 为 comment 或 placeholder，`source/context` 是输入，`holder` 是可选边界设置，`files` 是子资源内容。
`expected` 要逐字符相等，不折叠空白；`error.phase` 指明预期 parse 或 merge 失败。
profile 分为 core、java-edges、java-utf16，表示该用例的目标，**并不表示已经穷尽相应兼容级别**。

Java 参考校验流程对成功用例重复 merge，并验证 String、不同调用方输出字符集的字节结果和调用方 Map 的所有权约束。
用例是本文的可执行实例，不是独立语言实现已完成的证明；正则方言、getter、宿主函数、全部异常恢复仍需补测。
构建与运行步骤见 [一致性校验 Prompt](prompt/conformance.prompt.zh.md)。

## 15. Java 参考实现

语法、解析、运行期和输出行为对应以下实现。

| 主题 | 实现位置 |
|:--|:--|
| 注释扫描、字段、规则匹配、控制树、错误 | [Parser.java](../src/main/java/pro/fessional/meepo/sack/Parser.java)、[Seek.java](../src/main/java/pro/fessional/meepo/util/Seek.java) |
| 生命周期与匹配分组 | [Life.java](../src/main/java/pro/fessional/meepo/bind/wow/Life.java)、[Tick.java](../src/main/java/pro/fessional/meepo/bind/wow/Tick.java) |
| 运行时指令 | [RNA 节点目录](../src/main/java/pro/fessional/meepo/bind/rna)、[文本节点目录](../src/main/java/pro/fessional/meepo/bind/txt) |
| map/参数 | [MapEngine.java](../src/main/java/pro/fessional/meepo/poof/impl/map/MapEngine.java)、[MapHelper.java](../src/main/java/pro/fessional/meepo/poof/impl/map/MapHelper.java)、[Eval.java](../src/main/java/pro/fessional/meepo/util/Eval.java) |
| 占位符 | [Holder.java](../src/main/java/pro/fessional/meepo/sack/Holder.java) |
| 输出与隔离 | [Gene.java](../src/main/java/pro/fessional/meepo/sack/Gene.java)、[Acid.java](../src/main/java/pro/fessional/meepo/sack/Acid.java)、[Cmap.java](../src/main/java/pro/fessional/meepo/sack/Cmap.java)、[Dent.java](../src/main/java/pro/fessional/meepo/util/Dent.java)、[pack](../src/main/java/pro/fessional/meepo/pack) |
| 注册表、资源、内置函数 | [RnaManager.java](../src/main/java/pro/fessional/meepo/poof/RnaManager.java)、[Read.java](../src/main/java/pro/fessional/meepo/util/Read.java)、[eval](../src/main/java/pro/fessional/meepo/eval) |
