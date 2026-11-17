# 构建与 Maven 发布

## 项目、版本与仓库

GitHub 仓库为 `fessionalpro/professional-meepo`，Maven 坐标为 `pro.fessional:meepo`。
POM 的 `revision` 是开发版本来源，默认保留 `-SNAPSHOT` 后缀，当前为 `2.0.0-SNAPSHOT`。
本轮正式版目标为 `2.0.0`。生产代码及正式制品兼容 Java 8。
具体行为变化见 [changelog](../changelog.md)。

| 发布种类 | 当前版本 | 版本规则 |
|:--|:--|:--|
| Central 正式版 | `2.0.0` | tag 与 POM 去掉末尾 `-SNAPSHOT` 后的版本一致 |
| Central 日常版 | `2.0.0-SNAPSHOT` | 从 POM 版本派生，末尾保留一个 `-SNAPSHOT` |
| 私服正式版 | `2.0.0` | 使用 POM 去掉末尾 `-SNAPSHOT` 后的版本 |
| 私服日常版 | `2.0.0-SNAPSHOT` | 使用 POM 对应的 SNAPSHOT 版本 |

Central 正式版使用不可覆盖的版本坐标，后续修复发布新版本号。SNAPSHOT 使用 Maven 的时间戳文件名和仓库元数据。
[Central 版本不可变性](https://central.sonatype.org/publish/requirements/immutability/)、[SNAPSHOT 说明](https://central.sonatype.org/publish/publish-portal-snapshots/)

## Maven 版本管理

本项目使用 **Maven Wrapper 3.3.4 + Maven 3.9.16**，版本与下载校验值保存在
[Maven Wrapper 配置](../.mvn/wrapper/maven-wrapper.properties)。Maven 3.9.16 支持 Java 8，属于 Apache 维护的版本线。
[Apache Maven 版本记录](https://maven.apache.org/docs/history.html)

| 工具 | 本项目职责 | 入口 |
|:--|:--|:--|
| Maven Wrapper | 将 Maven 版本随代码固定，供本地、CI 和发布使用 | POSIX `./mvnw`，Windows `mvnw.cmd` |
| mise | 选择和安装本机 JDK，版本见 `.tool-versions` | `mise install`、`mise exec -- ./mvnw verify` |

这个组合让 Maven 版本由仓库统一维护，开发者可以沿用各自的 JDK 管理方式。Wrapper 使用官方 `only-script` 分发，
首次下载 Maven 时校验 `distributionSha256Sum`。更新 Maven 版本时同步更新校验值。
[Apache Maven Wrapper](https://maven.apache.org/tools/wrapper/)

```sh
./mvnw -v
./mvnw -B -ntp verify
./mvnw -B -ntp -Pcoverage verify
python3 -m unittest discover -s .github/script -p 'test_*.py' -v
```

CI 使用 Java 8 编译生产类，各 JDK 使用 `-Dmaven.main.skip=true` 编译和运行对应测试。
Java 8、11、17、21 共用同一生产字节码，合并新鲜 JaCoCo 数据后检查指令、分支、行、复杂度、方法和类均为 100%。
JaCoCo 保留追加写入，以收集独立运行时探针的子 JVM 数据。发布 job 再以 Java 8 构建并执行测试。

## GitHub Actions 入口

| 工作流 | 触发与用途 |
|:--|:--|
| [ci.yml](../.github/workflows/ci.yml) | main、develop 的 push/PR，以及手动和复用调用。完成构建、工具脚本测试、JDK 矩阵和覆盖率检查。 |
| CI 的 SNAPSHOT job | develop push 通过 CI 后自动发布 Central SNAPSHOT。CI 手动入口可选 `publish_snapshot` 和 `snapshot_dry_run`。 |
| [release.yml](../.github/workflows/release.yml) | published GitHub Release 或指定已有 tag 的手动调用。检查版本和 tag，复用 CI 后发布 Central 正式版。 |

[版本解析脚本](../.github/script/publish_version.py) 统一处理 POM、版本后缀和 tag 指向。
工作流固定官方 Actions 的 commit SHA，任务 token 使用 `contents: read`，checkout 关闭凭证持久化。
发布凭证仅提供给对应发布 job 的部署步骤。

先将工作流提交并合入默认分支 `main`，再使用 GitHub 的手动入口。日常 develop push 使用该提交中的 CI 配置。
根目录 [英文入口](../AGENTS.md) 引用 [中文开发约定](AGENTS.zh.md)，项目英文默认文件和中文 `.zh.md` 文件按该约定维护。

## Central 准备

Central 使用 Portal User Token。确认账号拥有 `pro.fessional` namespace 的发布权限，生成 Token 后保存返回的 username 和 password/token。
组织级 Secrets 可向指定仓库提供凭证，仓库级与 Environment Secrets 也可按 GitHub 的作用域配置。
[Portal User Token](https://central.sonatype.org/publish/generate-portal-token/)

SNAPSHOT 还需在 Portal 的 namespace 菜单中执行 **Enable SNAPSHOTs**。
[启用 SNAPSHOT](https://central.sonatype.org/publish/publish-portal-snapshots/#enabling-snapshot-releases-for-your-namespace)

| Secret | 用途 |
|:--|:--|
| `CENTRAL_USERNAME` | Portal User Token 的 username，正式版和 SNAPSHOT 共用 |
| `CENTRAL_TOKEN` | Portal User Token 的 password/token |
| `MAVEN_GPG_KEY` | ASCII-armored 签名私钥，用于 Central 正式版 |
| `MAVEN_GPG_PASSPHRASE` | 私钥有口令时提供 |

Central job 使用 `maven-central` Environment。
共享 Secret 需授权给 `fessionalpro/professional-meepo`；已有同名 repository Secrets 可由相应 job 使用。
Environment 配置分支或 tag 限制时，允许本项目需要的 main、develop 和正式 tag 入口。

签名使用 Maven GPG Plugin 3.2.8 的纯 Java `bc` signer，私钥和口令由环境变量提供。
Central 正式制品包含主 JAR、sources/Javadoc JAR、解析完版本号的 POM、四份 GPG 签名和校验文件，主 JAR 附带 `META-INF/LICENSE`。
[Central 制品要求](https://central.sonatype.org/publish/requirements/)、[Maven GPG 参数](https://maven.apache.org/plugins/maven-gpg-plugin/sign-mojo.html)

本地 Maven settings 可配置两个 server，按使用的目标提供相应环境变量。

```xml
<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0">
    <servers>
        <server>
            <id>central</id>
            <username>${env.CENTRAL_USERNAME}</username>
            <password>${env.CENTRAL_TOKEN}</password>
        </server>
        <server>
            <id>altrh</id>
            <username>${env.MVN_ALT_USER}</username>
            <password>${env.MVN_ALT_PASS}</password>
        </server>
    </servers>
</settings>
```

## Central 正式版

使用 `sign,central` profiles。Central Publisher 0.12.0 自动上传、校验并发布，`waitUntil=published` 让 job 等待正式发布结果。
[Publisher 配置](https://central.sonatype.org/publish/publish-portal-maven/)

正式发布以 GitHub Release 的 tag 为版本，tag 须与其提交中 POM 的 `revision` 去掉末尾 `-SNAPSHOT` 后一致。
当前对应关系为 POM `2.0.0-SNAPSHOT`、tag `2.0.0`、Central 坐标 `pro.fessional:meepo:2.0.0`。
tag 所在提交的 POM 若仍为 `1.5.2-SNAPSHOT`，则 `2.0.0` tag 会在版本检查阶段失败。

在 [New release](https://github.com/fessionalpro/professional-meepo/releases/new) 页面选择包含发布配置和待发布源码的提交，
创建或选择 tag `2.0.0`，填写说明后点击 **Publish release**。`release.published` 事件触发版本检查、CI 和签名发布。
GitHub 的该事件也覆盖已发布的 pre-release，Maven 版本仍由 tag 决定。
[GitHub Release 事件](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#release)

发布构建通过 `-Drevision=2.0.0` 设置本次 Maven 的有效版本，版本覆盖仅在该次构建中生效。
源码 POM 保存开发版本 `2.0.0-SNAPSHOT`，Flatten 插件生成的 `.pom.xml` 和发布制品使用 `2.0.0`。
日常 Maven 构建使用 POM 中的 SNAPSHOT 版本。
[Maven CI Friendly Versions](https://maven.apache.org/guides/mini/guide-maven-ci-friendly.html)

本地签名包演练如下，产物位于 `target/central-publishing/*.zip`。

```sh
./mvnw -B -ntp -Psign,central -Drevision=2.0.0 -Dcentral.skipUpload=true deploy
```

对已有的 `2.0.0` tag，可以通过手动入口执行签名包演练，默认 `dry_run=true`。

```sh
gh workflow run release.yml --ref main -f tag=2.0.0 -f dry_run=true
```

正式发布也可通过手动入口选择 `dry_run=false`。

```sh
gh workflow run release.yml --ref main -f tag=2.0.0 -f dry_run=false
```

## Central 日常 SNAPSHOT

develop 的每次 push 在 CI 通过后发布当前 POM 对应的 SNAPSHOT，当前为 `2.0.0-SNAPSHOT`。
POM 已带 `-SNAPSHOT` 时保留一个后缀。由版本解析脚本派生版本，通过 `-Drevision` 传入 Maven。
CI 对 develop 的发布流程采用排队，SNAPSHOT 部署按仓库串行执行，以保持时间戳元数据的一致性。

SNAPSHOT 使用 `central` profile 和相同 Portal Token，部署地址为 `https://central.sonatype.com/repository/maven-snapshots/`。
Central Publisher 按 Maven 版本种类选择部署路径。
[SNAPSHOT 发布协议](https://central.sonatype.org/publish/publish-portal-snapshots/)

```sh
./mvnw -B -ntp -Pcentral -Drevision=2.0.0-SNAPSHOT -Dcentral.skipUpload=true deploy
```

该命令演练 SNAPSHOT 构建和暂存。实际本地部署时设 `-Dcentral.skipUpload=false`。
手动通过 CI 验证并演练发布可使用以下入口。

```sh
gh workflow run ci.yml --ref develop -f publish_snapshot=true -f snapshot_dry_run=true
```

## 私有 Maven 仓库

私服由用户在本地通过 Maven 或 [Python 辅助脚本](../.github/script/publish_private.py) 发布。
仓库地址由用户传入，账号、密码或 Token 由用户自己的 Maven settings 及其引用的本地环境变量提供。
上文 settings 示例中的 `altrh` server 使用 `MVN_ALT_USER` 和 `MVN_ALT_PASS` 本地环境变量。

### 直接使用 Maven

`alt_repo` 是用户设置的 shell 变量，通过 Maven Deploy Plugin 3.1.1 的原生参数 `altDeploymentRepository` 传入。
其格式为 `server-id::URL`，server id 与用户 settings 中的 `<server><id>` 一致。
版本以 `-SNAPSHOT` 结束时，用户应传入目标 snapshot 仓库地址。
[Maven Deploy 参数](https://maven.apache.org/plugins/maven-deploy-plugin/deploy-mojo.html)

```sh
alt_repo='altrh::https://maven.example.org/repository/releases/'
mvn -B -ntp -Drevision=2.0.0 "-DaltDeploymentRepository=$alt_repo" deploy

alt_repo='altrh::https://maven.example.org/repository/snapshots/'
mvn -B -ntp -Drevision=2.0.0-SNAPSHOT "-DaltDeploymentRepository=$alt_repo" deploy
```

上述命令也可使用项目的 `./mvnw` 或 `mvnw.cmd`。通过 `--settings /path/to/settings.xml` 选择用户的 settings。
需要签名时增加 `-Psign`，由本地环境变量 `MAVEN_GPG_KEY` 和 `MAVEN_GPG_PASSPHRASE` 提供签名配置。
构建演练将目标 `deploy` 换为 `verify`。

已有的 `altrh` profile 支持按版本自动选择本地环境变量中的仓库地址。

| 本地环境变量 | 内容 |
|:--|:--|
| `MVN_ALT_REPO` | 私服 release 仓库部署 URL |
| `MVN_ALT_SNAP` | 私服 snapshot 仓库部署 URL |
| `MVN_ALT_USER` | 私服用户名，由 settings 引用 |
| `MVN_ALT_PASS` | 私服密码或 Token，由 settings 引用 |

```sh
./mvnw -B -ntp -Paltrh -Drevision=2.0.0 deploy
./mvnw -B -ntp -Paltrh -Drevision=2.0.0-SNAPSHOT deploy
```

该 profile 按版本使用 `altReleaseDeploymentRepository` 或 `altSnapshotDeploymentRepository`。
这两个参数优先于通用的 `altDeploymentRepository`，使用上面的 `alt_repo` 命令时直接传入通用参数即可。

### 使用 Python

脚本从 POM 派生 release 或 snapshot 版本，默认调用项目 Wrapper，在仓库根目录执行 Maven。
账号和签名配置沿用本地 Maven 设置。`--maven mvn` 可以选择本机 Maven，`--settings` 可以指定 settings 文件。

```sh
python3 .github/script/publish_private.py release \
  --alt-repo 'altrh::https://maven.example.org/repository/releases/' --dry-run

python3 .github/script/publish_private.py snapshot \
  --alt-repo 'altrh::https://maven.example.org/repository/snapshots/' \
  --maven mvn --settings /path/to/settings.xml
```

`--dry-run` 执行完整的 `verify`，实际发布执行 `deploy`。需要签名时增加 `--sign`。
发布辅助脚本及其测试统一保存在 `.github/script/`，从项目根目录运行测试。

```sh
python3 -m unittest discover -s .github/script -p 'test_*.py' -v
```

## 验证范围

`central.skipUpload=true` 用于构建和暂存演练，私服脚本 `--dry-run` 执行到 verify 阶段。
线上账号授权、Token 有效期、namespace 的 SNAPSHOT 开关、私服地址可达性和服务器权限由实际目标的认证部署结果确认。
正式发布后检查 Maven Central 或私服上的 POM、主 JAR、sources/Javadoc 和相应签名。
