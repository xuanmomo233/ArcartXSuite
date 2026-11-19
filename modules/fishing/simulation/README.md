# Fishing 自动化试钓

在仓库根目录用 PowerShell 7 运行。将 `$ariaJar` 指向包含 Aria 引擎的 ArcartX 或 Aria JAR，并使用该 JAR 支持的 Java 版本。

```powershell
$ariaJar = 'D:/path/to/Aria.jar'
.\gradlew.bat :modules:fishing:simulateFishing "-PfishingAriaJar=$ariaJar"
```

默认用 [legendary-carp.yml](legendary-carp.yml) 测龙泉湖的传说鲤鱼，每档复用从 `seed` 起的 `trials` 个种子。三档的等级、鱼竿、鱼饵和已点亮天赋都在该文件。每个 `compare` 条目沿用 `profile` 指定的配装，只替换鱼竿、鱼饵或疲惫中的一项。天赋按可点亮的顺序填写。

- 用 `-PfishingScenario=路径` 选择其他场景。
- 用 `-PfishingDataDir=路径` 读取自己的 Fishing 配置目录，其中包含 `ArcartXFishing.yml`、`fatigue.yml`、`talents.yml` 和各类定义子目录。默认读取仓库内的资源。
- 用 `-PfishingTrace=路径` 将每档首个种子的输入与状态写入 TSV，包含 `behavior_stage` 和 `behavior` 两列，用于查看阶段和当前动作。指定一个父目录已存在的新文件。把场景里的 `seed` 改成要追踪的种子即可单独复跑。
- 在场景中填写 `current-zone: rapids` 可选中龙泉湖的急流。逐步记录中的 `current_drift`、`stamina` 和 `exhaustion_steps` 分别显示当步漂移、鱼的剩余体力和力竭剩余步数。

每局固定目标鱼。控制器只读延迟后的鱼和绿条坐标，用观测到的位移决定按下或松开。延迟和最短换键间隔用小游戏步数配置。默认每步按 20 TPS 换算为 50 毫秒。

「遇鱼概率」按场景的候选鱼权重计算，包含高疲惫时的特殊鱼替换。「遇到后收竿」统计固定目标鱼的成功局数。「每次有效咬钩获鱼估计」是两者相乘。「对照档同种子翻转」记录有多少局因这一项改动从失败变成成功或反过来。读取自定义 Aria 公式时，复现结果依赖公式本身也使用确定性输入。

工具运行服务端同一套对抗逻辑。客户端输入延迟、渲染与真人反应由实机试钓验收。
