### Agent八荣八耻

-以瞎猜接口为耻，以认真查询为荣。
-以模糊执行为耻，以寻求确认为荣。
-以臆想业务为耻，以人类确认为荣。
-以创造接口为耻，以复用现有为荣。
-以跳过验证为耻，以主动测试为荣。
-以破坏架构为耻，以遵循规范为荣。
-以假装理解为耻，以诚实无知为荣。
-以盲目修改为耻，以谨慎重构为荣。

-软件每次迭代都要更新版本号
-软件每次完成功能更新和迭代后都需要重新审查README.md和spec.md，并立刻更新新的内容。


## PowerShell 环境规范

本机已安装 PowerShell 7（`pwsh`）。所有命令行操作必须遵循以下规则。

### 1\. 使用最新版 PowerShell

* 优先使用 `pwsh.exe`（PowerShell 7），不要使用旧版 `powershell.exe`（Windows PowerShell 5.1）。
* 一律使用 PowerShell 7 的现代语法和 cmdlet，不要使用已弃用的写法。

### 2\. 显式调用并强制使用 UTF-8 编码

调用任何 PowerShell 命令时，必须使用以下完整形式：

```powershell
pwsh.exe -NoProfile -ExecutionPolicy Bypass -Command "\[Console]::OutputEncoding = \[System.Text.Encoding]::UTF8; <你的命令>"
```

* `-NoProfile`：跳过配置文件，保证环境一致并加快启动速度。
* `-ExecutionPolicy Bypass`：避免脚本执行策略拦截。
* 命令开头必须设置 `\[Console]::OutputEncoding = \[System.Text.Encoding]::UTF8`，防止中文输出乱码。

### 3\. 目录判空必须二次验证

如果初次判断某个目录为空，必须再执行一次 `dir`（即 `Get-ChildItem`）命令进行实际验证。

只有再次确认目录确实为空后，才能按照空目录处理，不得仅凭推断判断目录为空。

### 4\. 先读后写

每次修改文件之前，都必须重新读取目标文件的最新内容，例如：

```powershell
Get-Content <文件路径>
```

必须确保所有修改均基于文件的最新状态进行，禁止使用缓存或过期的文件内容。
